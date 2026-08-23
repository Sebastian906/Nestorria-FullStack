"""Base feature extraction utilities.

Provides reusable transformers and helpers for feature engineering.
"""

import numpy as np
import pandas as pd
from sklearn.base import BaseEstimator, TransformerMixin

class DateFeatureExtractor(BaseEstimator, TransformerMixin):
    """Extract basic features from datetime columns.

    For each datetime column, extracts: year, month, day, dayofweek.
    """

    def fit(self, X, y=None):
        return self

    def transform(self, X):
        if isinstance(X, pd.DataFrame):
            X = X.copy()
        else:
            X = pd.DataFrame(X)

        result_parts = []
        for col in X.columns:
            dt = pd.to_datetime(X[col], errors="coerce")
            result_parts.extend([
                dt.dt.year.values.reshape(-1, 1),
                dt.dt.month.values.reshape(-1, 1),
                dt.dt.day.values.reshape(-1, 1),
                dt.dt.dayofweek.values.reshape(-1, 1),
            ])

        return np.hstack(result_parts) if result_parts else np.empty((len(X), 0))

def get_feature_count(df: pd.DataFrame) -> int:
    """Return the number of columns in a DataFrame."""
    return len(df.columns)