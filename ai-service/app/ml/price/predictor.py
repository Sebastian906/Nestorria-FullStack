"""Inference predictor for price predictions.

Loads persisted model and serves predictions.
Falls back gracefully when model is unavailable.
"""

import json
from pathlib import Path

import numpy as np
import pandas as pd
import structlog
from sklearn.ensemble import RandomForestRegressor
from sklearn.pipeline import Pipeline

from app.config import get_settings
from app.ml.models.registry import ModelRegistry
from app.ml.price.features import PropertyFeatureExtractor

logger = structlog.get_logger("ai-service.ml.price")


class PricePredictor:
    """Load model and predict property prices.

    Loads the complete fitted artifact: MLPipeline (preprocessing) + RandomForestRegressor.
    Applies scaling and encoding to raw features before forest prediction.
    """

    def __init__(self):
        self.settings = get_settings()
        self.registry = ModelRegistry(artifacts_path=self.settings.artifacts_path)
        self.extractor = PropertyFeatureExtractor()
        self._pipeline: Pipeline | None = None
        self._rf_model: RandomForestRegressor | None = None
        self._feature_names: list[str] | None = None
        self._model_name: str | None = None
        self._model_version: str | None = None

    def load_model(self, name: str = "price_rf", version: str | None = None) -> bool:
        """Load model from registry.

        Args:
            name: model name
            version: specific version, or None for latest

        Returns:
            True if model loaded successfully
        """
        try:
            if version is None:
                latest = self.registry.get_latest(name)
                if latest is None:
                    logger.warning("price_model_not_found", name=name)
                    return False
                version = latest["version"]

            artifact = self.registry.load_model(name, version)
            self._model_name = name
            self._model_version = version

            if isinstance(artifact, Pipeline):
                self._pipeline = artifact
                self._rf_model = artifact.named_steps["model"]
            else:
                self._rf_model = artifact

            # Read metadata for the SPECIFIC loaded version, not latest
            model_dir = self.registry._model_dir(name, version)
            metadata_path = model_dir / "metadata.json"
            if metadata_path.exists():
                metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
                self._feature_names = metadata.get("features", [])

            logger.info(
                "price_model_loaded",
                name=name,
                version=version,
                features=len(self._feature_names or []),
            )
            return True

        except FileNotFoundError:
            logger.warning("price_model_load_failed", name=name, version=version)
            return False

    def predict(self, property_data: dict) -> dict | None:
        """Predict price for a single property.

        Args:
            property_data: property dict matching PricePredictionRequest

        Returns:
            dict with predicted_price, confidence, model info, or None if unavailable
        """
        if self._rf_model is None:
            logger.warning("price_predict_no_model")
            return None

        try:
            # Extract raw features
            features = self.extractor.extract(property_data)
            df = pd.DataFrame([features])

            # Apply preprocessing if pipeline is available
            if self._pipeline is not None:
                preprocessor = self._pipeline.named_steps["preprocessor"]
                X = preprocessor.transform(df)
            elif self._feature_names:
                for col in self._feature_names:
                    if col not in df.columns:
                        df[col] = 0
                X = df[self._feature_names].values
            else:
                X = df.values

            # Predict with confidence
            tree_preds = np.array(
                [tree.predict(X) for tree in self._rf_model.estimators_]
            )
            prediction = float(tree_preds.mean())
            std = float(tree_preds.std())
            mean_abs = abs(prediction)
            cv = std / mean_abs if mean_abs > 0 else 0.0
            confidence = 1.0 / (1.0 + cv)

            return {
                "predicted_price": round(prediction, 2),
                "confidence": round(confidence, 4),
                "model": f"{self._model_name}_{self._model_version}",
                "features_used": len(self._feature_names or []),
            }

        except Exception as e:
            logger.error("price_predict_error", error=str(e))
            return None

    @property
    def is_available(self) -> bool:
        """Check if model is loaded and ready."""
        return self._rf_model is not None