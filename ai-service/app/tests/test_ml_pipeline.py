"""Tests for MLPipeline preprocessing."""

import numpy as np
import pandas as pd
import pytest

from app.ml.preprocessing.pipeline import MLPipeline

@pytest.fixture
def numeric_df():
    """DataFrame with numeric columns and missing values."""
    return pd.DataFrame({
        "price": [100.0, 200.0, np.nan, 400.0, 500.0],
        "bedrooms": [1, 2, 3, np.nan, 5],
        "area": [50.0, 80.0, 120.0, 150.0, np.nan],
    })

@pytest.fixture
def categorical_df():
    """DataFrame with categorical columns."""
    return pd.DataFrame({
        "type": ["house", "apartment", "house", "condo", "apartment"],
        "status": ["active", "sold", "active", "pending", "sold"],
    })

@pytest.fixture
def mixed_df():
    """DataFrame with numeric and categorical columns."""
    return pd.DataFrame({
        "price": [100.0, 200.0, 300.0, 400.0, 500.0],
        "bedrooms": [1, 2, 3, 4, 5],
        "type": ["house", "apartment", "house", "condo", "apartment"],
    })

@pytest.fixture
def date_df():
    """DataFrame with date columns."""
    return pd.DataFrame({
        "price": [100.0, 200.0, 300.0],
        "list_date": ["2024-01-15", "2024-06-20", "2024-12-01"],
    })

class TestMLPipelineNumeric:
    def test_fit_transform_numeric(self, numeric_df):
        pipeline = MLPipeline(numeric_columns=["price", "bedrooms", "area"])
        result = pipeline.fit_transform(numeric_df)

        assert isinstance(result, pd.DataFrame)
        assert result.shape[0] == 5
        assert result.shape[1] == 3
        # No NaN after imputation
        assert result.isna().sum().sum() == 0

    def test_transform_after_fit(self, numeric_df):
        pipeline = MLPipeline(numeric_columns=["price", "bedrooms", "area"])
        pipeline.fit(numeric_df)

        new_data = pd.DataFrame({
            "price": [150.0, 250.0],
            "bedrooms": [2, 3],
            "area": [70.0, 90.0],
        })
        result = pipeline.transform(new_data)

        assert result.shape[0] == 2
        assert result.shape[1] == 3

    def test_transform_before_fit_raises(self, numeric_df):
        pipeline = MLPipeline(numeric_columns=["price", "bedrooms", "area"])

        with pytest.raises(RuntimeError, match="Pipeline not fitted"):
            pipeline.transform(numeric_df)

class TestMLPipelineCategorical:
    def test_fit_transform_categorical(self, categorical_df):
        pipeline = MLPipeline(categorical_columns=["type", "status"])
        result = pipeline.fit_transform(categorical_df)

        assert isinstance(result, pd.DataFrame)
        assert result.shape[0] == 5
        # OneHotEncoder expands categories
        assert result.shape[1] > 0

    def test_handles_unknown_category(self, categorical_df):
        pipeline = MLPipeline(categorical_columns=["type", "status"])
        pipeline.fit(categorical_df)

        new_data = pd.DataFrame({
            "type": ["villa"],
            "status": ["unknown_status"],
        })
        result = pipeline.transform(new_data)

        assert result.shape[0] == 1

class TestMLPipelineMixed:
    def test_fit_transform_mixed(self, mixed_df):
        pipeline = MLPipeline(
            numeric_columns=["price", "bedrooms"],
            categorical_columns=["type"],
        )
        result = pipeline.fit_transform(mixed_df)

        assert isinstance(result, pd.DataFrame)
        assert result.shape[0] == 5
        # 2 numeric + OneHot categories for type
        assert result.shape[1] >= 3

class TestMLPipelineMinMax:
    def test_minmax_scaler(self, numeric_df):
        pipeline = MLPipeline(
            numeric_columns=["price", "bedrooms", "area"],
            scaler="minmax",
        )
        result = pipeline.fit_transform(numeric_df)

        # MinMax scales to [0, 1]
        assert result.min().min() >= -0.01
        assert result.max().max() <= 1.01

class TestMLPipelineErrors:
    def test_no_columns_raises(self):
        df = pd.DataFrame({"a": [1, 2]})
        pipeline = MLPipeline()

        with pytest.raises(ValueError, match="At least one column type"):
            pipeline.fit(df)

    def test_feature_names_cached(self, numeric_df):
        pipeline = MLPipeline(numeric_columns=["price", "bedrooms", "area"])
        pipeline.fit(numeric_df)

        names1 = pipeline._get_feature_names()
        names2 = pipeline._get_feature_names()
        assert names1 == names2