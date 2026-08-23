"""Tests for data leakage validation."""

import numpy as np
import pandas as pd
import pytest

from app.ml.datasets.validation import validate_no_leakage

class TestValidateNoLeakage:
    def test_no_leakage(self):
        train_ts = pd.Series(pd.date_range("2024-01-01", periods=5))
        test_ts = pd.Series(pd.date_range("2024-01-06", periods=3))

        assert validate_no_leakage(train_ts, test_ts) is True

    def test_leakage_detected(self):
        train_ts = pd.Series(pd.date_range("2024-01-01", periods=10))
        test_ts = pd.Series(pd.date_range("2024-01-05", periods=5))

        assert validate_no_leakage(train_ts, test_ts) is False

    def test_empty_train(self):
        train_ts = pd.Series([])
        test_ts = pd.Series(pd.date_range("2024-01-01", periods=3))

        assert validate_no_leakage(train_ts, test_ts) is True

    def test_empty_test(self):
        train_ts = pd.Series(pd.date_range("2024-01-01", periods=3))
        test_ts = pd.Series([])

        assert validate_no_leakage(train_ts, test_ts) is True

    def test_both_empty(self):
        train_ts = pd.Series([])
        test_ts = pd.Series([])

        assert validate_no_leakage(train_ts, test_ts) is True

    def test_numpy_arrays(self):
        train_ts = np.array(["2024-01-01", "2024-01-02", "2024-01-03"])
        test_ts = np.array(["2024-01-04", "2024-01-05"])

        assert validate_no_leakage(train_ts, test_ts) is True