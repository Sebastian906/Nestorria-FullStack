"""Tests for ML evaluation metrics."""

import numpy as np
import pytest

from app.ml.evaluation.metrics import classification_metrics, ranking_metrics, regression_metrics

class TestRegressionMetrics:
    def test_perfect_predictions(self):
        y_true = [1.0, 2.0, 3.0, 4.0, 5.0]
        y_pred = [1.0, 2.0, 3.0, 4.0, 5.0]
        result = regression_metrics(y_true, y_pred)

        assert result["mse"] == pytest.approx(0.0)
        assert result["rmse"] == pytest.approx(0.0)
        assert result["mae"] == pytest.approx(0.0)
        assert result["r2"] == pytest.approx(1.0)

    def test_known_values(self):
        y_true = [1.0, 2.0, 3.0, 4.0, 5.0]
        y_pred = [1.1, 2.2, 2.8, 4.1, 5.3]
        result = regression_metrics(y_true, y_pred)

        assert result["mse"] > 0
        assert result["rmse"] > 0
        assert result["mae"] > 0
        assert result["r2"] < 1.0
        assert result["r2"] > 0.5

    def test_empty_dataset(self):
        result = regression_metrics([], [])
        assert result["mse"] == 0.0
        assert result["r2"] == 0.0

class TestClassificationMetrics:
    def test_perfect_classification(self):
        y_true = [0, 1, 0, 1, 1]
        y_pred = [0, 1, 0, 1, 1]
        result = classification_metrics(y_true, y_pred)

        assert result["f1"] == pytest.approx(1.0)
        assert result["precision"] == pytest.approx(1.0)
        assert result["recall"] == pytest.approx(1.0)

    def test_with_probabilities(self):
        y_true = [0, 1, 0, 1]
        y_pred = [0, 1, 0, 1]
        y_prob = [0.1, 0.9, 0.2, 0.8]
        result = classification_metrics(y_true, y_pred, y_prob)

        assert result["roc_auc"] is not None
        assert result["roc_auc"] == pytest.approx(1.0)

    def test_without_probabilities(self):
        y_true = [0, 1, 0, 1]
        y_pred = [0, 1, 0, 1]
        result = classification_metrics(y_true, y_pred)

        assert result["roc_auc"] is None

    def test_empty_dataset(self):
        result = classification_metrics([], [])
        assert result["f1"] == 0.0
        assert result["roc_auc"] is None

    def test_single_class(self):
        y_true = [0, 0, 0]
        y_pred = [0, 0, 0]
        result = classification_metrics(y_true, y_pred)

        assert result["f1"] == pytest.approx(1.0)  # zero_division=0
        assert result["precision"] == pytest.approx(1.0)
        assert result["recall"] == pytest.approx(1.0)

class TestRankingMetrics:
    def test_perfect_ranking(self):
        y_true = [1.0, 1.0, 0.0, 0.0, 0.0]
        y_pred = [0.9, 0.8, 0.3, 0.2, 0.1]
        result = ranking_metrics(y_true, y_pred, k=2)

        assert result["precision_at_k"] == pytest.approx(1.0)
        assert result["recall_at_k"] == pytest.approx(1.0)
        assert result["ndcg_at_k"] == pytest.approx(1.0)

    def test_partial_ranking(self):
        y_true = [1.0, 0.0, 1.0, 0.0, 0.0]
        y_pred = [0.9, 0.8, 0.7, 0.3, 0.1]
        result = ranking_metrics(y_true, y_pred, k=3)

        assert 0.0 <= result["precision_at_k"] <= 1.0
        assert 0.0 <= result["recall_at_k"] <= 1.0
        assert 0.0 <= result["ndcg_at_k"] <= 1.0

    def test_k_larger_than_data(self):
        y_true = [1.0, 0.0]
        y_pred = [0.9, 0.1]
        result = ranking_metrics(y_true, y_pred, k=10)

        # k is clamped to len(y_true)
        assert result["precision_at_k"] <= 1.0

    def test_empty_dataset(self):
        result = ranking_metrics([], [], k=5)
        assert result["precision_at_k"] == 0.0
        assert result["recall_at_k"] == 0.0
        assert result["ndcg_at_k"] == 0.0

    def test_zero_k(self):
        y_true = [1.0, 0.0]
        y_pred = [0.9, 0.1]
        result = ranking_metrics(y_true, y_pred, k=0)

        assert result["precision_at_k"] == 0.0