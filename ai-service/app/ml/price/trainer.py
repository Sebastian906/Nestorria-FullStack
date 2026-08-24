"""Training pipeline for price prediction.

Orchestrates: data loading → feature extraction → preprocessing →
train/test split → training → evaluation → persistence.
"""

import time
from pathlib import Path

import pandas as pd
import structlog

from app.config import get_settings
from app.ml.evaluation.metrics import regression_metrics
from app.ml.models.registry import ModelRegistry
from app.ml.price.features import PropertyFeatureExtractor
from app.ml.price.model import PriceModelWrapper
from app.ml.preprocessing.pipeline import MLPipeline
from app.ml.training.splitter import train_test_split_custom

logger = structlog.get_logger("ai-service.ml.price")

class PriceTrainer:
    """End-to-end training pipeline for property price prediction.

    Uses existing infrastructure:
    - MLPipeline for preprocessing
    - train_test_split_custom for splitting
    - regression_metrics for evaluation
    - ModelRegistry for persistence
    """

    def __init__(self):
        self.settings = get_settings()
        self.extractor = PropertyFeatureExtractor()
        self.registry = ModelRegistry(artifacts_path=self.settings.artifacts_path)

    def train(
        self,
        properties: list[dict],
        prices: list[float],
        model_name: str = "price_rf",
        model_version: str = "v1",
    ) -> dict:
        """Train price model and persist artifacts.

        Args:
            properties: list of property dicts (from Spring Boot API)
            prices: list of target prices (sale or rent)
            model_name: name for ModelRegistry
            model_version: version string

        Returns:
            dict with metrics and training info
        """
        start = time.time()

        # 1. Feature extraction
        logger.info("price_training_start", n_properties=len(properties))
        features_list = self.extractor.extract_batch(properties)
        df = pd.DataFrame(features_list)

        # 2. Preprocessing
        pipeline = MLPipeline(
            numeric_columns=self.extractor.get_numeric_columns(),
            categorical_columns=self.extractor.get_categorical_columns(),
            scaler="standard",  # Will be removed from final pipeline
        )
        X = pipeline.fit_transform(df)
        y = pd.Series(prices, name="price")

        # 3. Train/test split
        X_train, X_test, y_train, y_test = train_test_split_custom(
            X, y,
            test_size=self.settings.test_size,
            random_state=self.settings.random_state,
        )

        # 4. Train model
        model = PriceModelWrapper(
            n_estimators=100,
            max_depth=10,
            random_state=self.settings.random_state,
        )
        model.fit(X_train.values, y_train.values)

        # 5. Evaluate
        y_pred = model.predict(X_test.values)
        metrics = regression_metrics(y_test.values, y_pred)

        # 6. Persist
        self.registry.save_model(
            model=model.model,  # Save the raw sklearn model
            name=model_name,
            version=model_version,
            metrics=metrics,
            features=list(X.columns),
        )

        elapsed = time.time() - start
        logger.info(
            "price_training_complete",
            metrics=metrics,
            elapsed_seconds=round(elapsed, 2),
            model_name=model_name,
            model_version=model_version,
        )

        return {
            "metrics": metrics,
            "model_name": model_name,
            "model_version": model_version,
            "features": list(X.columns),
            "training_samples": len(X_train),
            "test_samples": len(X_test),
            "elapsed_seconds": round(elapsed, 2),
        }