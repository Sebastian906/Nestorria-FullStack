"""Data leakage validation utilities.

Detects temporal leakage where future data appears in training sets.
"""

import pandas as pd
import numpy as np

def validate_no_leakage(
    train_timestamps: pd.Series | np.ndarray,
    test_timestamps: pd.Series | np.ndarray,
) -> bool:
    """Check that all training timestamps precede all test timestamps.

    Args:
        train_timestamps: Timestamps from the training set.
        test_timestamps: Timestamps from the test set.

    Returns:
        True if no leakage detected, False otherwise.
    """
    train_ts = pd.Series(train_timestamps)
    test_ts = pd.Series(test_timestamps)

    if len(train_ts) == 0 or len(test_ts) == 0:
        return True

    return bool(train_ts.max() < test_ts.min())