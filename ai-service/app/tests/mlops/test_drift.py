"""Tests for DriftDetector."""

import numpy as np
import pytest

from app.mlops.drift import DriftDetector

class TestDriftDetection:
    def test_no_drift_same_distribution(self):
        rng = np.random.default_rng(42)
        reference = rng.normal(0, 1, 1000)
        new_data = rng.normal(0, 1, 1000)

        detector = DriftDetector(reference)
        result = detector.check_drift(new_data)
        assert result["drifted"] is False

    def test_drift_detected_different_distribution(self):
        rng = np.random.default_rng(42)
        reference = rng.normal(0, 1, 1000)
        new_data = rng.normal(5, 1, 1000)  # shifted mean

        detector = DriftDetector(reference)
        result = detector.check_drift(new_data)
        assert result["drifted"] is True
        assert result["p_value"] < 0.05

    def test_per_feature_results(self):
        rng = np.random.default_rng(42)
        reference = rng.normal(0, 1, (1000, 3))
        new_data = rng.normal(0, 1, (1000, 3))

        detector = DriftDetector(reference)
        result = detector.check_drift(new_data)
        assert len(result["per_feature"]) == 3

    def test_single_feature(self):
        rng = np.random.default_rng(42)
        reference = rng.normal(0, 1, 500)
        new_data = rng.normal(5, 1, 500)

        detector = DriftDetector(reference)
        result = detector.check_drift(new_data)
        assert result["drifted"] is True

    def test_empty_new_data(self):
        reference = np.array([1.0, 2.0, 3.0])
        detector = DriftDetector(reference)
        result = detector.check_drift(np.array([]))
        # Empty data should not crash
        assert "per_feature" in result

    def test_custom_threshold(self):
        rng = np.random.default_rng(42)
        reference = rng.normal(0, 1, 1000)
        new_data = rng.normal(0.5, 1, 1000)

        detector = DriftDetector(reference)
        result = detector.check_drift(new_data)
        p = result["p_value"]
        # Strict threshold (below p) must detect drift
        strict = detector.check_drift(new_data, threshold=p + 0.01)
        assert strict["drifted"] is True
        # Loose threshold (above p) must NOT detect drift
        loose = detector.check_drift(new_data, threshold=max(p - 0.01, 0.0))
        assert loose["drifted"] is False

    def test_multidimensional_input(self):
        rng = np.random.default_rng(42)
        reference = rng.normal(0, 1, (500, 5))
        new_data = rng.normal(0, 1, (500, 5))

        detector = DriftDetector(reference)
        result = detector.check_drift(new_data)
        assert len(result["per_feature"]) == 5
        assert result["drifted"] is False

    def test_mismatched_features_raises(self):
        rng = np.random.default_rng(42)
        reference = rng.normal(0, 1, (500, 2))
        new_data = rng.normal(0, 1, (500, 3))

        detector = DriftDetector(reference)
        with pytest.raises(ValueError, match="Feature count mismatch"):
            detector.check_drift(new_data)
