"""Inference predictor for cancellation predictions.

Loads persisted model and serves predictions.
Falls back gracefully when model is unavailable.
"""

import json
from pathlib import Path

import numpy as np
import pandas as pd
import structlog
from sklearn.ensemble import RandomForestClassifier
from sklearn.pipeline import Pipeline

from app.config import get_settings
from app.ml.models.registry import ModelRegistry
from app.ml.cancellation.features import BookingFeatureExtractor

logger = structlog.get_logger("ai-service.ml.cancellation")

# Risk level thresholds
RISK_LOW = 0.3
RISK_HIGH = 0.7


class CancellationPredictor:
    """Load model and predict booking cancellation probability.

    Loads the complete fitted artifact: MLPipeline (preprocessing) + RandomForestClassifier.
    """

    def __init__(self):
        self.settings = get_settings()
        self.registry = ModelRegistry(artifacts_path=self.settings.artifacts_path)
        self.extractor = BookingFeatureExtractor()
        self._pipeline: Pipeline | None = None
        self._rf_model: RandomForestClassifier | None = None
        self._feature_names: list[str] | None = None
        self._model_name: str | None = None
        self._model_version: str | None = None

    def load_model(self, name: str = "cancel_rf", version: str | None = None) -> bool:
        """Load model from registry.

        Returns True if model loaded successfully.
        """
        try:
            if version is None:
                latest = self.registry.get_latest(name)
                if latest is None:
                    logger.warning("cancel_model_not_found", name=name)
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

            # Read metadata for the SPECIFIC loaded version
            model_dir = self.registry._model_dir(name, version)
            metadata_path = model_dir / "metadata.json"
            if metadata_path.exists():
                metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
                self._feature_names = metadata.get("features", [])

            logger.info(
                "cancel_model_loaded",
                name=name,
                version=version,
                features=len(self._feature_names or []),
            )
            return True

        except FileNotFoundError:
            logger.warning("cancel_model_load_failed", name=name, version=version)
            return False

    def predict(self, booking_data: dict) -> dict | None:
        """Predict cancellation probability for a single booking.

        Args:
            booking_data: booking dict matching Spring Boot AiPredictionRequest

        Returns:
            dict with cancellation_probability, risk_level, model info, or None if unavailable
        """
        if self._rf_model is None:
            logger.warning("cancel_predict_no_model")
            return None

        try:
            # Extract raw features
            features = self.extractor.extract(booking_data)
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

            # Predict probability
            proba = self._rf_model.predict_proba(X)
            cancellation_prob = float(proba[0, 1])  # Probability of class 1 (cancelled)

            # Risk level
            risk_level = self._risk_level(cancellation_prob)

            return {
                "cancellation_probability": round(cancellation_prob, 4),
                "risk_level": risk_level,
                "model": f"{self._model_name}_{self._model_version}",
                "features_used": len(self._feature_names or []),
            }

        except Exception as e:
            logger.error("cancel_predict_error", error=str(e))
            return None

    @staticmethod
    def _risk_level(probability: float) -> str:
        """Map probability to risk level."""
        if probability < RISK_LOW:
            return "low"
        elif probability <= RISK_HIGH:
            return "medium"
        else:
            return "high"

    @property
    def is_available(self) -> bool:
        """Check if model is loaded and ready."""
        return self._rf_model is not None