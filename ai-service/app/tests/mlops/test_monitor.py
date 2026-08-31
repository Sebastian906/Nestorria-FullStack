"""Tests for ModelMonitor."""

import pytest

from app.mlops.monitor import ModelMonitor

@pytest.fixture
def monitor(tmp_path):
    """Monitor using a temporary directory."""
    return ModelMonitor(artifacts_path=str(tmp_path))

class TestRecordPrediction:
    def test_record_single_prediction(self, monitor):
        monitor.record_prediction("price_model", "1.0", latency_ms=12.5, success=True)
        metrics = monitor.get_version_metrics("price_model", "1.0")
        assert metrics is not None
        assert metrics["total_predictions"] == 1
        assert metrics["failures"] == 0
        assert metrics["total_latency_ms"] == pytest.approx(12.5)

    def test_record_failure(self, monitor):
        monitor.record_prediction("price_model", "1.0", latency_ms=5.0, success=False)
        metrics = monitor.get_version_metrics("price_model", "1.0")
        assert metrics["failures"] == 1
        assert metrics["total_predictions"] == 1

    def test_record_multiple_predictions(self, monitor):
        for _ in range(5):
            monitor.record_prediction("price_model", "1.0", latency_ms=10.0, success=True)
        metrics = monitor.get_version_metrics("price_model", "1.0")
        assert metrics["total_predictions"] == 5
        assert metrics["total_latency_ms"] == pytest.approx(50.0)

    def test_record_multiple_versions(self, monitor):
        monitor.record_prediction("price_model", "1.0", latency_ms=10.0, success=True)
        monitor.record_prediction("price_model", "2.0", latency_ms=8.0, success=True)
        all_metrics = monitor.get_metrics("price_model")
        assert len(all_metrics) == 2

    def test_first_and_last_prediction_timestamps(self, monitor):
        monitor.record_prediction("price_model", "1.0", latency_ms=10.0, success=True)
        metrics = monitor.get_version_metrics("price_model", "1.0")
        assert metrics["first_prediction"] is not None
        assert metrics["last_prediction"] is not None

class TestPersistence:
    def test_metrics_persist_to_file(self, tmp_path):
        m1 = ModelMonitor(artifacts_path=str(tmp_path))
        m1.record_prediction("price_model", "1.0", latency_ms=10.0, success=True)

        # Reload from same directory
        m2 = ModelMonitor(artifacts_path=str(tmp_path))
        metrics = m2.get_version_metrics("price_model", "1.0")
        assert metrics is not None
        assert metrics["total_predictions"] == 1

class TestGetMetrics:
    def test_get_metrics_empty(self, monitor):
        assert monitor.get_metrics("nonexistent") == []

    def test_get_version_metrics_empty(self, monitor):
        assert monitor.get_version_metrics("nonexistent", "1.0") is None
