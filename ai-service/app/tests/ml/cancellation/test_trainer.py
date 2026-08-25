"""Tests for CancellationTrainer."""

import pytest

from app.ml.cancellation.synthetic import generate_synthetic_cancellations
from app.ml.cancellation.trainer import CancellationTrainer

class TestCancellationTrainer:
    def test_train_with_synthetic(self, tmp_path):
        """Train with synthetic data and verify artifacts are created."""
        import os
        os.environ["ARTIFACTS_PATH"] = str(tmp_path)

        # Reset settings cache
        from app.config import get_settings
        get_settings.cache_clear()

        trainer = CancellationTrainer()
        trainer.registry.artifacts_path = tmp_path

        bookings, labels = generate_synthetic_cancellations(n=100, seed=42)
        result = trainer.train(bookings, labels, model_version="test-v1")

        assert result["experimental"] is True
        assert result["model_name"] == "cancel_rf"
        assert result["model_version"] == "test-v1"
        assert "f1" in result["metrics"]
        assert "precision" in result["metrics"]
        assert "recall" in result["metrics"]
        assert result["training_samples"] > 0
        assert result["test_samples"] > 0

    def test_smote_skipped_with_few_positives(self, tmp_path):
        """SMOTE should be skipped when fewer than 6 positive samples."""
        from app.config import get_settings
        get_settings.cache_clear()

        trainer = CancellationTrainer()
        trainer.registry.artifacts_path = tmp_path

        # 20 bookings, only 2 cancelled
        bookings = [
            {"checkInDate": "2026-10-01", "checkOutDate": "2026-10-05", "guests": 2, "totalPrice": 400, "propertyType": "HOUSE", "city": "Madrid"}
            for _ in range(20)
        ]
        labels = [0] * 18 + [1] * 2

        result = trainer.train(bookings, labels, use_smote=None)
        assert result["smote_applied"] is False

    def test_smote_applied_with_enough_positives(self, tmp_path):
        """SMOTE should be applied when enough positive samples exist."""
        from app.config import get_settings
        get_settings.cache_clear()

        trainer = CancellationTrainer()
        trainer.registry.artifacts_path = tmp_path

        bookings, labels = generate_synthetic_cancellations(n=100, cancel_ratio=0.2, seed=42)
        result = trainer.train(bookings, labels, use_smote=None)
        assert result["smote_applied"] is True