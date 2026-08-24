"""Tests for PriceModelWrapper."""

import numpy as np
import pytest

from app.ml.price.model import PriceModelWrapper

@pytest.fixture
def training_data():
    np.random.seed(42)
    X = np.random.rand(100, 10)
    y = X @ np.random.rand(10) + np.random.randn(100) * 0.1
    return X, y

class TestPriceModel:
    def test_fit_and_predict(self, training_data):
        X, y = training_data
        model = PriceModelWrapper(n_estimators=10, max_depth=5)
        model.fit(X, y)
        predictions = model.predict(X[:5])
        assert len(predictions) == 5
        assert all(isinstance(p, (int, float)) for p in predictions)

    def test_predict_with_confidence(self, training_data):
        X, y = training_data
        model = PriceModelWrapper(n_estimators=10, max_depth=5)
        model.fit(X, y)
        preds, confidence = model.predict_with_confidence(X[:5])
        assert len(preds) == 5
        assert len(confidence) == 5
        assert all(0 <= c <= 1 for c in confidence)

    def test_predict_before_fit_raises(self):
        model = PriceModelWrapper()
        with pytest.raises(RuntimeError, match="Model not fitted"):
            model.predict(np.array([[1, 2, 3]]))

    def test_feature_importances(self, training_data):
        X, y = training_data
        model = PriceModelWrapper(n_estimators=10)
        model.fit(X, y)
        importances = model.feature_importances
        assert importances is not None
        assert len(importances) == 10
        assert abs(sum(importances) - 1.0) < 0.01

    def test_feature_importances_before_fit(self):
        model = PriceModelWrapper()
        assert model.feature_importances is None