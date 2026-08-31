"""Basic data drift detection using Kolmogorov-Smirnov test.

Lightweight: no MLflow, no complex statistical frameworks.
Uses scipy which is already a project dependency.
"""

import numpy as np
from scipy import stats

import structlog

logger = structlog.get_logger("ai-service.mlops")

class DriftDetector:
    """Detect data drift between reference and new data using KS test."""

    def __init__(self, reference_data: np.ndarray):
        """Initialize with reference (training) data distribution."""
        self.reference = np.asarray(reference_data, dtype=float)

    def check_drift(
        self, new_data: np.ndarray, threshold: float = 0.05
    ) -> dict:
        """Run KS test against reference data.

        Returns:
            dict with keys: drifted (bool), statistic (float),
            p_value (float), per_feature (list[dict])
        """
        new_data = np.asarray(new_data, dtype=float)

        if new_data.ndim == 1:
            new_data = new_data.reshape(-1, 1)
        if self.reference.ndim == 1:
            ref = self.reference.reshape(-1, 1)
        else:
            ref = self.reference

        n_features = max(ref.shape[1], new_data.shape[1])
        per_feature = []
        any_drift = False

        for i in range(n_features):
            ref_col = ref[:, i] if ref.shape[1] > 1 else ref.ravel()
            new_col = new_data[:, i] if new_data.shape[1] > 1 else new_data.ravel()

            if len(ref_col) == 0 or len(new_col) == 0:
                per_feature.append({
                    "feature_index": i,
                    "drifted": False,
                    "statistic": 0.0,
                    "p_value": 1.0,
                })
                continue

            stat, p_value = stats.ks_2samp(ref_col, new_col)
            drifted = p_value < threshold
            if drifted:
                any_drift = True

            per_feature.append({
                "feature_index": i,
                "drifted": drifted,
                "statistic": float(stat),
                "p_value": float(p_value),
            })

        logger.info(
            "drift_check_completed",
            n_features=n_features,
            any_drift=any_drift,
            threshold=threshold,
        )

        return {
            "drifted": any_drift,
            "statistic": max(f["statistic"] for f in per_feature) if per_feature else 0.0,
            "p_value": min(f["p_value"] for f in per_feature) if per_feature else 1.0,
            "per_feature": per_feature,
        }