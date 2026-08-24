"""Tests for PricePredictor."""

import numpy as np
import joblib
import pytest
from sklearn.ensemble import RandomForestRegressor

from app.ml.models.registry import ModelRegistry
from app.ml.price.predictor import PricePredictor

@pytest.fixture
def trained_model(tmp_path):
    """Create and persist a trained model for testing."""
    registry = ModelRegistry(artifacts_path=str(tmp_path))
    model = RandomForestRegressor(n_estimators=10, random_state=42)
    X = np.random.rand(50, 15)
    y = np.random.rand(50) * 100000
    model.fit(X, y)
    registry.save_model(
        model, "price_rf", "test-v1",
        metrics={"mse": 1000},
        features=[f"f{i}" for i in range(15)],
    )
    return tmp_path, registry

class TestPricePredictor:
    def test_load_model(self, trained_model):
        tmp_path, _ = trained_model
        predictor = PricePredictor()
        predictor.registry = ModelRegistry(artifacts_path=str(tmp_path))
        loaded = predictor.load_model("price_rf", "test-v1")
        assert loaded is True
        assert predictor.is_available is True

    def test_load_nonexistent_model(self, tmp_path):
        predictor = PricePredictor()
        predictor.registry = ModelRegistry(artifacts_path=str(tmp_path))
        loaded = predictor.load_model("nonexistent")
        assert loaded is False
        assert predictor.is_available is False

    def test_predict_with_model(self, trained_model):
        tmp_path, _ = trained_model
        predictor = PricePredictor()
        predictor.registry = ModelRegistry(artifacts_path=str(tmp_path))
        predictor.load_model("price_rf", "test-v1")

        # Mock feature names
        predictor._feature_names = [f"f{i}" for i in range(15)]

        property_data = {f"f{i}": float(np.random.rand()) for i in range(15)}
        result = predictor.predict(property_data)
        assert result is not None
        assert "predicted_price" in result
        assert "confidence" in result
        assert "model" in result
        assert 0 <= result["confidence"] <= 1

    def test_predict_without_model(self):
        predictor = PricePredictor()
        result = predictor.predict({"area": 100})
        assert result is None