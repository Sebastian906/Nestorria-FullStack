"""Tests for data splitting utilities."""

import numpy as np
import pandas as pd
import pytest

from app.ml.training.splitter import temporal_split, train_test_split_custom

@pytest.fixture
def sample_data():
    """Sample DataFrame and target for splitting."""
    X = pd.DataFrame({
        "price": [100, 200, 300, 400, 500, 600, 700, 800, 900, 1000],
        "bedrooms": [1, 2, 3, 4, 5, 1, 2, 3, 4, 5],
    })
    y = pd.Series([1, 0, 1, 0, 1, 0, 1, 0, 1, 0])
    return X, y

class TestTrainTestSplit:
    def test_basic_split(self, sample_data):
        X, y = sample_data
        X_train, X_test, y_train, y_test = train_test_split_custom(X, y, test_size=0.3)

        assert len(X_train) == 7
        assert len(X_test) == 3
        assert len(y_train) == 7
        assert len(y_test) == 3

    def test_invalid_ratio_too_low(self, sample_data):
        X, y = sample_data
        with pytest.raises(ValueError, match="test_size must be between"):
            train_test_split_custom(X, y, test_size=0.0)

    def test_invalid_ratio_too_high(self, sample_data):
        X, y = sample_data
        with pytest.raises(ValueError, match="test_size must be between"):
            train_test_split_custom(X, y, test_size=1.0)

    def test_empty_dataframe(self):
        X = pd.DataFrame({"a": []})
        y = pd.Series([])
        with pytest.raises(ValueError, match="Cannot split empty"):
            train_test_split_custom(X, y)

class TestTemporalSplit:
    def test_chronological_order(self, sample_data):
        X, y = sample_data
        timestamps = pd.Series(pd.date_range("2024-01-01", periods=10, freq="D"))

        X_train, X_test, y_train, y_test = temporal_split(X, y, timestamps, test_ratio=0.3)

        # All train timestamps should be before test timestamps
        train_max = timestamps.iloc[:7].max()
        test_min = timestamps.iloc[7:].min()
        assert train_max < test_min

    def test_no_shuffle(self, sample_data):
        X, y = sample_data
        timestamps = pd.Series(pd.date_range("2024-01-01", periods=10, freq="D"))

        X_train, X_test, y_train, y_test = temporal_split(X, y, timestamps, test_ratio=0.3)

        # Training data should maintain original order
        assert X_train["price"].tolist() == [100, 200, 300, 400, 500, 600, 700]

    def test_invalid_ratio(self, sample_data):
        X, y = sample_data
        timestamps = pd.Series(pd.date_range("2024-01-01", periods=10, freq="D"))

        with pytest.raises(ValueError, match="test_ratio must be between"):
            temporal_split(X, y, timestamps, test_ratio=0.0)

    def test_empty_dataframe(self):
        X = pd.DataFrame({"a": []})
        y = pd.Series([])
        timestamps = pd.Series([])
        with pytest.raises(ValueError, match="Cannot split empty"):
            temporal_split(X, y, timestamps)