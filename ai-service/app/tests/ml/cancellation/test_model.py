"""Tests for CancellationModelWrapper."""

import numpy as np
import pytest

from app.ml.cancellation.model import CancellationModelWrapper

class TestCancellationModelWrapper:
    def test_fit_and_predict(self):
        rng = np.random.RandomState(42)
        X = rng.rand(100, 5)
        y = (X[:, 0] > 0.5).astype(int)

        model = CancellationModelWrapper(n_estimators=10, max_depth=3)
        model.fit(X, y)

        preds = model.predict(X[:5])
        assert preds.shape == (5,)
        assert set(np.unique(preds)).issubset({0, 1})

    def test_predict_proba(self):
        rng = np.random.RandomState(42)
        X = rng.rand(100, 5)
        y = (X[:, 0] > 0.5).astype(int)

        model = CancellationModelWrapper(n_estimators=10, max_depth=3)
        model.fit(X, y)

        proba = model.predict_proba(X[:5])
        assert proba.shape == (5, 2)
        assert np.allclose(proba.sum(axis=1), 1.0)

    def test_predict_before_fit_raises(self):
        model = CancellationModelWrapper()
        with pytest.raises(RuntimeError, match="not fitted"):
            model.predict(np.array([[1, 2, 3]]))

    def test_feature_importances(self):
        rng = np.random.RandomState(42)
        X = rng.rand(100, 5)
        y = (X[:, 0] > 0.5).astype(int)

        model = CancellationModelWrapper(n_estimators=10, max_depth=3)
        model.fit(X, y)

        importances = model.feature_importances
        assert importances is not None
        assert len(importances) == 5
        assert np.isclose(importances.sum(), 1.0)

    def test_feature_importances_before_fit(self):
        model = CancellationModelWrapper()
        assert model.feature_importances is None

    def test_imbalanced_classes(self):
        """Model should handle imbalanced classes."""
        rng = np.random.RandomState(42)
        X = rng.rand(100, 5)
        y = np.zeros(100, dtype=int)
        y[:5] = 1  # Only 5% positive

        model = CancellationModelWrapper(n_estimators=10, max_depth=3, class_weight="balanced")
        model.fit(X, y)

        proba = model.predict_proba(X[:5])
        assert proba.shape == (5, 2)