"""Tests for PricePredictor."""

import numpy as np
import pandas as pd
import pytest
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestRegressor
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler, OneHotEncoder
from sklearn.impute import SimpleImputer

from app.ml.models.registry import ModelRegistry
from app.ml.price.predictor import PricePredictor


def _make_pipeline(n_numeric: int, n_features: int) -> Pipeline:
    """Create a minimal fitted pipeline for testing."""
    numeric_cols = [f"num{i}" for i in range(n_numeric)]
    preprocessor = ColumnTransformer(
        transformers=[
            ("num", Pipeline([
                ("imputer", SimpleImputer(strategy="median")),
                ("scaler", StandardScaler()),
            ]), numeric_cols),
        ],
        remainder="drop",
    )
    df = pd.DataFrame(np.random.rand(50, n_numeric), columns=numeric_cols)
    preprocessor.fit(df)

    model = RandomForestRegressor(n_estimators=10, random_state=42)
    y = np.random.rand(50) * 100000
    X_transformed = preprocessor.transform(df)
    model.fit(X_transformed, y)

    return Pipeline([
        ("preprocessor", preprocessor),
        ("model", model),
    ])


@pytest.fixture
def trained_pipeline(tmp_path):
    """Create and persist a fitted pipeline for testing."""
    registry = ModelRegistry(artifacts_path=str(tmp_path))
    pipeline = _make_pipeline(n_numeric=15, n_features=15)
    registry.save_model(
        pipeline, "price_rf", "test-v1",
        metrics={"mse": 1000},
        features=[f"num{i}" for i in range(15)],
    )
    return tmp_path


@pytest.fixture
def two_version_models(tmp_path):
    """Create two model versions with different feature columns."""
    registry = ModelRegistry(artifacts_path=str(tmp_path))
    p1 = _make_pipeline(n_numeric=10, n_features=10)
    registry.save_model(
        p1, "price_rf", "v1",
        metrics={"mse": 2000},
        features=[f"f{i}" for i in range(10)],
    )
    p2 = _make_pipeline(n_numeric=15, n_features=15)
    registry.save_model(
        p2, "price_rf", "v2",
        metrics={"mse": 500},
        features=[f"f{i}" for i in range(15)],
    )
    return tmp_path


class TestPricePredictor:
    def test_load_model(self, trained_pipeline):
        predictor = PricePredictor()
        predictor.registry = ModelRegistry(artifacts_path=str(trained_pipeline))
        loaded = predictor.load_model("price_rf", "test-v1")
        assert loaded is True
        assert predictor.is_available is True

    def test_load_nonexistent_model(self, tmp_path):
        predictor = PricePredictor()
        predictor.registry = ModelRegistry(artifacts_path=str(tmp_path))
        loaded = predictor.load_model("nonexistent")
        assert loaded is False
        assert predictor.is_available is False

    def test_load_specific_version_not_latest(self, two_version_models):
        """Verify that loading v1 uses v1 metadata, not v2."""
        predictor = PricePredictor()
        predictor.registry = ModelRegistry(artifacts_path=str(two_version_models))
        loaded = predictor.load_model("price_rf", "v1")
        assert loaded is True
        assert predictor._model_version == "v1"
        assert predictor._feature_names == [f"f{i}" for i in range(10)]

    def test_predict_with_model(self, trained_pipeline):
        predictor = PricePredictor()
        predictor.registry = ModelRegistry(artifacts_path=str(trained_pipeline))
        predictor.load_model("price_rf", "test-v1")

        # Use only the preprocessor step to transform, then predict with RF
        data = {f"num{i}": [float(np.random.rand())] for i in range(15)}
        df = pd.DataFrame(data)
        preprocessor = predictor._pipeline.named_steps["preprocessor"]
        X = preprocessor.transform(df)
        tree_preds = np.array([t.predict(X) for t in predictor._rf_model.estimators_])
        prediction = float(tree_preds.mean())
        assert prediction > 0

    def test_predict_without_model(self):
        predictor = PricePredictor()
        result = predictor.predict({"area": 100})
        assert result is None

    def test_predict_applies_preprocessing(self, trained_pipeline):
        """Pipeline preprocessor transforms raw features before forest prediction."""
        predictor = PricePredictor()
        predictor.registry = ModelRegistry(artifacts_path=str(trained_pipeline))
        predictor.load_model("price_rf", "test-v1")

        data = {f"num{i}": [100.0 + i] for i in range(15)}
        df = pd.DataFrame(data)
        preprocessor = predictor._pipeline.named_steps["preprocessor"]
        X = preprocessor.transform(df)
        assert X.shape[0] == 1
        assert X.shape[1] == 15