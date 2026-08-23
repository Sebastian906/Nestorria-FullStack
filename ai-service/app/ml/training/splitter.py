"""Data splitting utilities for train/validation/test sets.

Provides standard split and temporal split for time series data.
"""

import numpy as np
import pandas as pd
from sklearn.model_selection import train_test_split


def train_test_split_custom(
    X: pd.DataFrame,
    y: pd.Series | np.ndarray,
    test_size: float = 0.2,
    random_state: int = 42,
) -> tuple:
    """Split data into train and test sets.

    Args:
        X: Feature DataFrame.
        y: Target series or array.
        test_size: Fraction of data for testing (0.0 to 1.0).
        random_state: Random seed for reproducibility.

    Returns:
        X_train, X_test, y_train, y_test
    """
    if not 0.0 < test_size < 1.0:
        raise ValueError(f"test_size must be between 0 and 1, got {test_size}")

    if len(X) == 0:
        raise ValueError("Cannot split empty DataFrame")

    if len(X) != len(y):
        raise ValueError(
            f"X and y must have the same length, got {len(X)} and {len(y)}"
        )

    return train_test_split(X, y, test_size=test_size, random_state=random_state)


def temporal_split(
    X: pd.DataFrame,
    y: pd.Series | np.ndarray,
    timestamps: pd.Series | np.ndarray,
    test_ratio: float = 0.2,
) -> tuple:
    """Split data chronologically for time series.

    Ensures all training data precedes all test data.
    No shuffle is applied.

    Args:
        X: Feature DataFrame.
        y: Target series or array.
        timestamps: Timestamp series for chronological ordering.
        test_ratio: Fraction of data for testing (0.0 to 1.0).

    Returns:
        X_train, X_test, y_train, y_test
    """
    if not 0.0 < test_ratio < 1.0:
        raise ValueError(f"test_ratio must be between 0 and 1, got {test_ratio}")

    if len(X) == 0:
        raise ValueError("Cannot split empty DataFrame")

    if len(X) != len(y) or len(X) != len(timestamps):
        raise ValueError(
            f"X, y, and timestamps must have the same length, "
            f"got {len(X)}, {len(y)}, and {len(timestamps)}"
        )

    # Normalize timestamps to datetime
    timestamps = pd.to_datetime(pd.Series(timestamps), errors="coerce")
    if timestamps.isna().any():
        raise ValueError("timestamps contain invalid or missing date values")

    X = X.copy()
    y = np.asarray(y)

    # Sort by timestamp
    sort_idx = timestamps.argsort()
    X_sorted = X.iloc[sort_idx].reset_index(drop=True)
    y_sorted = y[sort_idx]

    # Split point
    split_idx = int(len(X_sorted) * (1 - test_ratio))

    # Reject empty partitions
    if split_idx <= 0:
        raise ValueError(
            f"test_ratio ({test_ratio}) too large: would produce empty training set"
        )
    if split_idx >= len(X_sorted):
        raise ValueError(
            f"test_ratio ({test_ratio}) too small: would produce empty test set"
        )

    X_train = X_sorted.iloc[:split_idx]
    X_test = X_sorted.iloc[split_idx:]
    y_train = y_sorted[:split_idx]
    y_test = y_sorted[split_idx:]

    # Verify chronological order
    train_max = timestamps.iloc[sort_idx[:split_idx]].max()
    test_min = timestamps.iloc[sort_idx[split_idx:]].min()

    if train_max >= test_min:
        raise ValueError(
            f"Temporal split failed: train max ({train_max}) >= test min ({test_min})"
        )

    return X_train, X_test, y_train, y_test