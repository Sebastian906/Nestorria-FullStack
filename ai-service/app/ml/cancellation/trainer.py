"""Training pipeline for cancellation prediction.

Orchestrates: data loading -> feature extraction -> preprocessing ->
train/test split -> training -> evaluation -> persistence.

Preprocessing is fitted only on training partitions to prevent holdout leakage.
"""

import time

import numpy as np
import pandas as pd
import structlog
from sklearn.metrics import (
    classification_report,
    confusion_matrix,
)
from sklearn.model_selection import StratifiedKFold
from sklearn.pipeline import Pipeline as SkPipeline

from app.config import get_settings
from app.ml.evaluation.metrics import classification_metrics
from app.ml.models.registry import ModelRegistry
from app.ml.cancellation.features import BookingFeatureExtractor
from app.ml.cancellation.model import CancellationModelWrapper
from app.ml.preprocessing.pipeline import MLPipeline
from app.ml.training.splitter import train_test_split_custom

logger = structlog.get_logger("ai-service.ml.cancellation")

# Minimum samples of minority class required for SMOTE
MIN_SMOTE_SAMPLES = 6


class CancellationTrainer:
    """End-to-end training pipeline for booking cancellation prediction.

    Uses existing infrastructure:
    - MLPipeline for preprocessing
    - train_test_split_custom for splitting
    - classification_metrics for evaluation
    - ModelRegistry for persistence
    """

    def __init__(self):
        self.settings = get_settings()
        self.extractor = BookingFeatureExtractor()
        self.registry = ModelRegistry(artifacts_path=self.settings.artifacts_path)

    @staticmethod
    def _numeric_and_binary_cols() -> list[str]:
        return BookingFeatureExtractor.get_numeric_columns() + ["is_weekend_checkin"]

    def train(
        self,
        bookings: list[dict],
        labels: list[int],
        model_name: str = "cancel_rf",
        model_version: str = "v1",
        use_smote: bool | None = None,
    ) -> dict:
        """Train cancellation model and persist artifacts.

        Preprocessing is fitted ONLY on training data to prevent leakage.

        Args:
            bookings: list of booking dicts (from Spring Boot API)
            labels: list of binary labels (1=cancelled, 0=not cancelled)
            model_name: name for ModelRegistry
            model_version: version string
            use_smote: force SMOTE on/off, or None for auto-detect

        Returns:
            dict with metrics and training info
        """
        start = time.time()

        # 1. Feature extraction — raw features, no preprocessing yet
        logger.info("cancel_training_start", n_bookings=len(bookings))
        features_list = self.extractor.extract_batch(bookings)
        df = pd.DataFrame(features_list)
        y = pd.Series(labels, name="cancelled")

        # 2. Class distribution (full dataset, for logging only)
        class_counts_raw = y.value_counts().to_dict()
        class_counts = {str(k): int(v) for k, v in class_counts_raw.items()}
        logger.info("cancel_class_distribution", distribution=class_counts)

        # 3. Train/test split on RAW features (before preprocessing)
        df_train, df_test, y_train, y_test = train_test_split_custom(
            df, y,
            test_size=self.settings.test_size,
            random_state=self.settings.random_state,
        )

        # 4. Fit preprocessing ONLY on training data
        all_numeric = self._numeric_and_binary_cols()
        pipeline = MLPipeline(
            numeric_columns=all_numeric,
            categorical_columns=self.extractor.get_categorical_columns(),
            scaler="standard",
        )
        pipeline.fit(df_train)
        X_train = pipeline.transform(df_train)
        X_test = pipeline.transform(df_test)

        # 5. Decide on SMOTE using training minority count
        n_cancelled_train = int(y_train.value_counts().get(1, 0))
        if use_smote is None:
            use_smote = n_cancelled_train >= MIN_SMOTE_SAMPLES
        if use_smote and n_cancelled_train < MIN_SMOTE_SAMPLES:
            logger.warning(
                "smote_skipped",
                reason=f"Only {n_cancelled_train} cancelled in training set, need {MIN_SMOTE_SAMPLES}",
            )
            use_smote = False

        # 6. Apply SMOTE if enabled
        if use_smote:
            try:
                from imblearn.over_sampling import SMOTE
                k = min(5, n_cancelled_train - 1)
                smote = SMOTE(random_state=self.settings.random_state, k_neighbors=k)
                X_train_arr, y_train_arr = smote.fit_resample(X_train.values, y_train.values)
                X_train = pd.DataFrame(X_train_arr, columns=X_train.columns)
                y_train = pd.Series(y_train_arr, name="cancelled")
                smote_unique, smote_counts = np.unique(y_train_arr, return_counts=True)
                logger.info(
                    "smote_applied",
                    original_samples=len(y_train),
                    class_distribution={str(k): int(v) for k, v in zip(smote_unique, smote_counts)},
                )
            except ImportError:
                logger.warning("smote_skipped", reason="imbalanced-learn not installed")
                use_smote = False
            except Exception as e:
                logger.warning("smote_failed", error=str(e))
                use_smote = False

        # 7. Train model — no class_weight if SMOTE was applied
        model = CancellationModelWrapper(
            n_estimators=100,
            max_depth=8,
            class_weight=None if use_smote else "balanced",
            random_state=self.settings.random_state,
        )
        model.fit(X_train.values, y_train.values)

        # 8. Evaluate
        y_pred = model.predict(X_test.values)
        y_prob = model.predict_proba(X_test.values)[:, 1]

        metrics = classification_metrics(y_test.values, y_pred, y_prob)
        metrics["confusion_matrix"] = confusion_matrix(y_test.values, y_pred).tolist()
        metrics["classification_report"] = classification_report(
            y_test.values, y_pred, output_dict=True, zero_division=0
        )

        # Feature importance
        importances = model.feature_importances
        feature_names = list(X_train.columns)
        feature_importance = dict(sorted(
            zip(feature_names, importances.tolist()),
            key=lambda x: x[1],
            reverse=True,
        ))

        # 9. Cross-validation (only with sufficient data, raw features)
        cv_metrics = None
        if len(y) >= 50 and n_cancelled_train >= 10:
            cv_metrics = self._cross_validate(df, y, use_smote=use_smote)

        # 10. Persist — refit MLPipeline on ALL rows for final artifact
        pipeline_full = MLPipeline(
            numeric_columns=all_numeric,
            categorical_columns=self.extractor.get_categorical_columns(),
            scaler="standard",
        )
        pipeline_full.fit(df)

        combined = SkPipeline([
            ("preprocessor", pipeline_full._pipeline.named_steps["preprocessor"]),
            ("model", model.model),
        ])

        persist_metrics = {k: v for k, v in metrics.items()
                          if k not in ("confusion_matrix", "classification_report")}
        persist_metrics["experimental"] = True
        persist_metrics["dataset_size"] = len(bookings)
        persist_metrics["class_distribution"] = class_counts
        persist_metrics["smote_applied"] = use_smote

        self.registry.save_model(
            model=combined,
            name=model_name,
            version=model_version,
            metrics=persist_metrics,
            features=feature_names,
        )

        elapsed = time.time() - start
        logger.info(
            "cancel_training_complete",
            metrics=persist_metrics,
            feature_importance_top5=dict(list(feature_importance.items())[:5]),
            cv_metrics=cv_metrics,
            elapsed_seconds=round(elapsed, 2),
        )

        return {
            "metrics": metrics,
            "feature_importance": feature_importance,
            "cv_metrics": cv_metrics,
            "model_name": model_name,
            "model_version": model_version,
            "features": feature_names,
            "training_samples": len(X_train),
            "test_samples": len(X_test),
            "class_distribution": class_counts,
            "smote_applied": use_smote,
            "experimental": True,
            "elapsed_seconds": round(elapsed, 2),
        }

    def _cross_validate(
        self,
        df: pd.DataFrame,
        y: pd.Series,
        n_splits: int = 5,
        use_smote: bool = False,
    ) -> dict:
        """Stratified K-Fold cross-validation.

        Fits a separate MLPipeline per fold to prevent holdout leakage.
        """
        skf = StratifiedKFold(n_splits=n_splits, shuffle=True, random_state=self.settings.random_state)
        fold_metrics = []
        all_numeric = self._numeric_and_binary_cols()
        cat_cols = self.extractor.get_categorical_columns()

        for fold, (train_idx, val_idx) in enumerate(skf.split(df, y)):
            df_fold_train = df.iloc[train_idx]
            df_fold_val = df.iloc[val_idx]
            y_fold_train = y.iloc[train_idx]
            y_fold_val = y.iloc[val_idx]

            # Fit preprocessing on this fold's training data only
            fold_pipeline = MLPipeline(
                numeric_columns=all_numeric,
                categorical_columns=cat_cols,
                scaler="standard",
            )
            fold_pipeline.fit(df_fold_train)
            X_fold_train = fold_pipeline.transform(df_fold_train)
            X_fold_val = fold_pipeline.transform(df_fold_val)

            # Apply SMOTE within this fold if enabled
            if use_smote:
                try:
                    from imblearn.over_sampling import SMOTE
                    train_minority = int(y_fold_train.value_counts().get(1, 0))
                    if train_minority >= MIN_SMOTE_SAMPLES:
                        k = min(5, train_minority - 1)
                        smote = SMOTE(random_state=self.settings.random_state, k_neighbors=k)
                        X_arr, y_arr = smote.fit_resample(X_fold_train.values, y_fold_train.values)
                        X_fold_train = pd.DataFrame(X_arr, columns=X_fold_train.columns)
                        y_fold_train = pd.Series(y_arr, name="cancelled")
                except Exception:
                    pass

            model = CancellationModelWrapper(
                n_estimators=50,
                max_depth=8,
                class_weight=None if use_smote else "balanced",
                random_state=self.settings.random_state,
            )
            model.fit(X_fold_train.values, y_fold_train.values)
            y_pred = model.predict(X_fold_val.values)
            y_prob = model.predict_proba(X_fold_val.values)[:, 1]

            fold_m = classification_metrics(y_fold_val.values, y_pred, y_prob)
            fold_metrics.append(fold_m)
            logger.info(f"fold_{fold}_metrics", metrics=fold_m)

        # Aggregate
        avg_metrics = {}
        for key in fold_metrics[0]:
            values = [f[key] for f in fold_metrics if f[key] is not None]
            avg_metrics[f"avg_{key}"] = np.mean(values) if values else None
            avg_metrics[f"std_{key}"] = np.std(values) if values else None

        return avg_metrics

    def train_with_synthetic(self, n_samples: int = 200) -> dict:
        """Train with synthetic data for development/testing."""
        from app.ml.cancellation.synthetic import generate_synthetic_cancellations
        bookings, labels = generate_synthetic_cancellations(n=n_samples)
        return self.train(bookings, labels, model_version="synthetic-v1")
