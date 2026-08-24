"""Price model wrapper.

Uses RandomForestRegressor with the existing ML infrastructure.
No StandardScaler — RF doesn't need it.
Confidence estimated via tree prediction dispersion.
"""

import numpy as np
import structlog
from sklearn.ensemble import RandomForestRegressor

logger = structlog.get_logger("ai-service.ml.price")

class PriceModelWrapper:
    """Wraps RandomForestRegressor with confidence estimation.

    Confidence is derived from the standard deviation across tree predictions:
    - Low std → trees agree → high confidence
    - High std → trees disagree → low confidence
    """

    def __init__(
        self,
        n_estimators: int = 100,
        max_depth: int | None = 10,
        random_state: int = 42,
    ):
        self.model = RandomForestRegressor(
            n_estimators=n_estimators,
            max_depth=max_depth,
            random_state=random_state,
            n_jobs=-1,
        )
        self._is_fitted = False

    def fit(self, X: np.ndarray, y: np.ndarray) -> "PriceModelWrapper":
        """Train the model."""
        logger.info("price_model_fit_start", samples=len(X), features=X.shape[1])
        self.model.fit(X, y)
        self._is_fitted = True
        logger.info("price_model_fit_complete")
        return self

    def predict(self, X: np.ndarray) -> np.ndarray:
        """Predict prices."""
        if not self._is_fitted:
            raise RuntimeError("Model not fitted. Call fit() first.")
        return self.model.predict(X)

    def predict_with_confidence(self, X: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
        """Predict with confidence estimation.

        Returns:
            predictions: mean prediction across trees
            confidence: normalized confidence [0, 1] based on tree dispersion
        """
        if not self._is_fitted:
            raise RuntimeError("Model not fitted. Call fit() first.")

        # Get predictions from each tree
        tree_preds = np.array([tree.predict(X) for tree in self.model.estimators_])

        # Mean prediction
        predictions = tree_preds.mean(axis=0)

        # Confidence: inverse of coefficient of variation, clamped to [0, 1]
        std = tree_preds.std(axis=0)
        mean = np.abs(predictions)
        # Avoid division by zero
        cv = np.where(mean > 0, std / mean, 0.0)
        confidence = 1.0 / (1.0 + cv)

        return predictions, confidence

    @property
    def feature_importances(self) -> np.ndarray | None:
        """Feature importances if fitted."""
        if self._is_fitted:
            return self.model.feature_importances_
        return None