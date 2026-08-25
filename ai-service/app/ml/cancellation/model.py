"""Cancellation model wrapper.

Uses RandomForestClassifier for binary classification (cancelled vs not).
Confidence estimated via probability dispersion across trees.
"""

import numpy as np
import structlog
from sklearn.ensemble import RandomForestClassifier

logger = structlog.get_logger("ai-service.ml.cancellation")

class CancellationModelWrapper:
    """Wraps RandomForestClassifier with probability-based confidence.

    Confidence is derived from the distance of the predicted probability
    from the decision boundary (0.5):
    - Probability near 0 or 1 → high confidence
    - Probability near 0.5 → low confidence
    """

    def __init__(
        self,
        n_estimators: int = 100,
        max_depth: int | None = 8,
        class_weight: str | None = "balanced",
        random_state: int = 42,
    ):
        self.model = RandomForestClassifier(
            n_estimators=n_estimators,
            max_depth=max_depth,
            class_weight=class_weight,
            random_state=random_state,
            n_jobs=-1,
        )
        self._is_fitted = False

    def fit(self, X: np.ndarray, y: np.ndarray) -> "CancellationModelWrapper":
        """Train the model."""
        unique, counts = np.unique(y, return_counts=True)
        logger.info(
            "cancel_model_fit_start",
            samples=len(X),
            features=X.shape[1],
            class_distribution={str(k): int(v) for k, v in zip(unique, counts)},
        )
        self.model.fit(X, y)
        self._is_fitted = True
        logger.info("cancel_model_fit_complete")
        return self

    def predict_proba(self, X: np.ndarray) -> np.ndarray:
        """Predict cancellation probabilities."""
        if not self._is_fitted:
            raise RuntimeError("Model not fitted. Call fit() first.")
        return self.model.predict_proba(X)

    def predict(self, X: np.ndarray) -> np.ndarray:
        """Predict binary labels."""
        if not self._is_fitted:
            raise RuntimeError("Model not fitted. Call fit() first.")
        return self.model.predict(X)

    @property
    def feature_importances(self) -> np.ndarray | None:
        """Feature importances if fitted."""
        if self._is_fitted:
            return self.model.feature_importances_
        return None