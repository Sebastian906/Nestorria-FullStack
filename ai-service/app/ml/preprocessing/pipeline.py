"""Preprocessing pipeline using sklearn.

Provides MLPipeline with fit/transform/fit_transform methods
using ColumnTransformer for numeric, categorical, and date columns.
"""

import structlog
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.impute import SimpleImputer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import MinMaxScaler, OneHotEncoder, StandardScaler

from app.ml.preprocessing.features import DateFeatureExtractor

logger = structlog.get_logger("ai-service.ml")

SCALERS = {
    "standard": StandardScaler,
    "minmax": MinMaxScaler,
}

class MLPipeline:
    """Modular preprocessing pipeline for tabular data.

    Supports numeric, categorical, and date columns with
    configurable scaling and missing value imputation.
    """

    def __init__(
        self,
        numeric_columns: list[str] | None = None,
        categorical_columns: list[str] | None = None,
        date_columns: list[str] | None = None,
        scaler: str = "standard",
    ):
        self.numeric_columns = numeric_columns or []
        self.categorical_columns = categorical_columns or []
        self.date_columns = date_columns or []
        self.scaler = scaler
        self._pipeline: Pipeline | None = None
        self._feature_names: list[str] | None = None

    def _build_pipeline(self) -> Pipeline:
        """Build the sklearn preprocessing pipeline."""
        transformers = []

        if self.numeric_columns:
            scaler_cls = SCALERS.get(self.scaler, StandardScaler)
            numeric_transformer = Pipeline(steps=[
                ("imputer", SimpleImputer(strategy="median")),
                ("scaler", scaler_cls()),
            ])
            transformers.append(("num", numeric_transformer, self.numeric_columns))

        if self.categorical_columns:
            categorical_transformer = Pipeline(steps=[
                ("imputer", SimpleImputer(strategy="constant", fill_value="missing")),
                ("encoder", OneHotEncoder(handle_unknown="ignore", sparse_output=False)),
            ])
            transformers.append(("cat", categorical_transformer, self.categorical_columns))

        if self.date_columns:
            date_transformer = Pipeline(steps=[
                ("extract", DateFeatureExtractor()),
            ])
            transformers.append(("date", date_transformer, self.date_columns))

        if not transformers:
            raise ValueError(
                "At least one column type must be specified: "
                "numeric_columns, categorical_columns, or date_columns"
            )

        return Pipeline(steps=[
            ("preprocessor", ColumnTransformer(
                transformers=transformers,
                remainder="drop",
            )),
        ])

    def fit(self, X: pd.DataFrame) -> "MLPipeline":
        """Fit the preprocessing pipeline."""
        logger.info("pipeline_fit_start", rows=len(X), columns=list(X.columns))
        self._pipeline = self._build_pipeline()
        self._pipeline.fit(X)
        self._feature_names = None  # Reset cached names
        logger.info("pipeline_fit_complete", output_features=self.transform(X).shape[1])
        return self

    def transform(self, X: pd.DataFrame) -> pd.DataFrame:
        """Transform data using the fitted pipeline."""
        if self._pipeline is None:
            raise RuntimeError("Pipeline not fitted. Call fit() first.")

        transformed = self._pipeline.transform(X)

        if self._feature_names is None:
            self._feature_names = self._get_feature_names()

        return pd.DataFrame(transformed, columns=self._feature_names, index=X.index)

    def fit_transform(self, X: pd.DataFrame) -> pd.DataFrame:
        """Fit and transform in one step."""
        self.fit(X)
        return self.transform(X)

    def _get_feature_names(self) -> list[str]:
        """Get feature names from the fitted ColumnTransformer."""
        preprocessor = self._pipeline.named_steps["preprocessor"]
        feature_names = []

        for name, _, columns in preprocessor.transformers_:
            if name == "remainder":
                continue

            transformer = preprocessor.named_transformers_[name]

            if hasattr(transformer, "get_feature_names_out"):
                names = transformer.get_feature_names_out(columns)
                feature_names.extend(list(names))
            elif name == "date":
                # DateFeatureExtractor produces: year, month, day, dayofweek per column
                for col in columns:
                    feature_names.extend([
                        f"{col}_year", f"{col}_month",
                        f"{col}_day", f"{col}_dayofweek",
                    ])
            else:
                feature_names.extend(columns)

        return feature_names