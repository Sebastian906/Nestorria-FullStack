"""ML infrastructure module for ai-service.

Provides preprocessing, metrics, model registry, data splitting,
and leakage validation for the ML pipeline.
"""

from app.ml.preprocessing.features import DateFeatureExtractor
from app.ml.evaluation.metrics import classification_metrics, ranking_metrics, regression_metrics
from app.ml.preprocessing.pipeline import MLPipeline
from app.ml.models.registry import ModelRegistry
from app.ml.training.splitter import temporal_split, train_test_split_custom
from app.ml.datasets.validation import validate_no_leakage

__all__ = [
    "MLPipeline",
    "DateFeatureExtractor",
    "regression_metrics",
    "classification_metrics",
    "ranking_metrics",
    "ModelRegistry",
    "train_test_split_custom",
    "temporal_split",
    "validate_no_leakage",
]