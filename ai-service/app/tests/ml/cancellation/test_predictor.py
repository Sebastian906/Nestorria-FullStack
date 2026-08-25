"""Tests for CancellationPredictor."""

import numpy as np
import pandas as pd
import pytest
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

from app.ml.models.registry import ModelRegistry
from app.ml.cancellation.predictor import CancellationPredictor

def _make_cancel_pipeline() -> Pipeline:
    """Create a minimal fitted cancellation pipeline for testing.

    Uses the same column names that BookingFeatureExtractor produces
    so the ColumnTransformer can transform real feature data.
    """
    from app.ml.cancellation.features import BookingFeatureExtractor

    numeric_cols = BookingFeatureExtractor.get_numeric_columns() + ["is_weekend_checkin"]
    cat_cols = BookingFeatureExtractor.get_categorical_columns()

    preprocessor = ColumnTransformer(
        transformers=[
            ("num", Pipeline([
                ("imputer", SimpleImputer(strategy="median")),
                ("scaler", StandardScaler()),
            ]), numeric_cols),
            ("cat", Pipeline([
                ("imputer", SimpleImputer(strategy="constant", fill_value="missing")),
                ("encoder", OneHotEncoder(handle_unknown="ignore", sparse_output=False)),
            ]), cat_cols),
        ],
        remainder="drop",
    )

    rng = np.random.RandomState(42)
    n_rows = 50
    df_num = pd.DataFrame(rng.rand(n_rows, len(numeric_cols)), columns=numeric_cols)
    df_cat = pd.DataFrame(rng.choice(["HOUSE", "APARTMENT", "Madrid", "Barcelona"], size=(n_rows, len(cat_cols))), columns=cat_cols)
    df = pd.concat([df_num, df_cat], axis=1)

    preprocessor.fit(df)
    X_transformed = preprocessor.transform(df)
    y = rng.randint(0, 2, size=n_rows)

    model = RandomForestClassifier(n_estimators=10, random_state=42)
    model.fit(X_transformed, y)

    feature_names = numeric_cols + list(
        preprocessor.named_transformers_["cat"]
        .named_steps["encoder"]
        .get_feature_names_out(cat_cols)
    )

    return Pipeline([
        ("preprocessor", preprocessor),
        ("model", model),
    ]), feature_names


@pytest.fixture
def trained_pipeline(tmp_path):
    """Create and persist a fitted cancellation pipeline."""
    registry = ModelRegistry(artifacts_path=str(tmp_path))
    pipeline, feature_names = _make_cancel_pipeline()
    registry.save_model(
        pipeline, "cancel_rf", "test-v1",
        metrics={"f1": 0.8},
        features=feature_names,
    )
    return tmp_path

class TestCancellationPredictor:
    def test_load_model(self, trained_pipeline):
        predictor = CancellationPredictor()
        predictor.registry = ModelRegistry(artifacts_path=str(trained_pipeline))
        loaded = predictor.load_model("cancel_rf", "test-v1")
        assert loaded is True
        assert predictor.is_available is True

    def test_load_nonexistent_model(self, tmp_path):
        predictor = CancellationPredictor()
        predictor.registry = ModelRegistry(artifacts_path=str(tmp_path))
        loaded = predictor.load_model("nonexistent")
        assert loaded is False
        assert predictor.is_available is False

    def test_predict_with_model(self, trained_pipeline):
        predictor = CancellationPredictor()
        predictor.registry = ModelRegistry(artifacts_path=str(trained_pipeline))
        predictor.load_model("cancel_rf", "test-v1")

        booking = {
            "bookingId": "test-123",
            "features": {
                "checkInDate": "2026-09-15",
                "checkOutDate": "2026-09-20",
                "guests": 2,
                "totalPrice": 500,
                "propertyType": "HOUSE",
                "city": "Madrid",
            },
        }
        result = predictor.predict(booking)
        assert result is not None
        assert 0.0 <= result["cancellation_probability"] <= 1.0
        assert result["risk_level"] in ("low", "medium", "high")
        assert result["model"] == "cancel_rf_test-v1"

    def test_predict_without_model(self):
        predictor = CancellationPredictor()
        result = predictor.predict({"bookingId": "test"})
        assert result is None

    def test_risk_levels(self):
        assert CancellationPredictor._risk_level(0.1) == "low"
        assert CancellationPredictor._risk_level(0.29) == "low"
        assert CancellationPredictor._risk_level(0.3) == "medium"
        assert CancellationPredictor._risk_level(0.5) == "medium"
        assert CancellationPredictor._risk_level(0.7) == "medium"
        assert CancellationPredictor._risk_level(0.71) == "high"
        assert CancellationPredictor._risk_level(0.99) == "high"