"""Inference predictor for price predictions.

Loads persisted model and serves predictions.
Falls back gracefully when model is unavailable.
"""

import numpy as np
import structlog
from sklearn.ensemble import RandomForestRegressor

from app.config import get_settings
from app.ml.models.registry import ModelRegistry
from app.ml.price.features import PropertyFeatureExtractor

logger = structlog.get_logger("ai-service.ml.price")

class PricePredictor:
    """Load model and predict property prices.

    Thread-safe: model is loaded once and reused.
    """

    def __init__(self):
        self.settings = get_settings()
        self.registry = ModelRegistry(artifacts_path=self.settings.artifacts_path)
        self.extractor = PropertyFeatureExtractor()
        self._model: RandomForestRegressor | None = None
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

            self._model = self.registry.load_model(name, version)
            self._model_name = name
            self._model_version = version

            # Load feature names from metadata
            metadata = self.registry.get_latest(name)
            if metadata:
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
        if self._model is None:
            logger.warning("price_predict_no_model")
            return None

        try:
            # Extract features
            features = self.extractor.extract(property_data)
            df = pd.DataFrame([features])

            # Ensure column order matches training
            if self._feature_names:
                # Add missing columns with defaults
                for col in self._feature_names:
                    if col not in df.columns:
                        df[col] = 0
                df = df[self._feature_names]

            # Predict with confidence
            X = df.values
            tree_preds = np.array([tree.predict(X) for tree in self._model.estimators_])
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
        return self._model is not None

# Need pd for DataFrame
import pandas as pd