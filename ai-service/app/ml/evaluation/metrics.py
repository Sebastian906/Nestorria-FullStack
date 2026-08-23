"""Evaluation metrics for regression, classification, and ranking.

All functions return a dict with metric names as keys.
Handles edge cases: empty datasets, division by zero, missing probabilities.
"""

import numpy as np
from sklearn.metrics import (
    f1_score,
    mean_absolute_error,
    mean_squared_error,
    precision_score,
    r2_score,
    recall_score,
    roc_auc_score,
)

def regression_metrics(y_true, y_pred) -> dict:
    """Calculate regression metrics.

    Returns: MSE, RMSE, MAE, R-squared
    """
    y_true = np.asarray(y_true, dtype=float)
    y_pred = np.asarray(y_pred, dtype=float)

    if len(y_true) == 0:
        return {"mse": 0.0, "rmse": 0.0, "mae": 0.0, "r2": 0.0}

    mse = float(mean_squared_error(y_true, y_pred))
    return {
        "mse": mse,
        "rmse": float(np.sqrt(mse)),
        "mae": float(mean_absolute_error(y_true, y_pred)),
        "r2": float(r2_score(y_true, y_pred)),
    }

def classification_metrics(y_true, y_pred, y_prob=None) -> dict:
    """Calculate classification metrics.

    Returns: F1, Precision, Recall, ROC-AUC (if y_prob provided).
    """
    y_true = np.asarray(y_true)
    y_pred = np.asarray(y_pred)

    if len(y_true) == 0:
        return {"f1": 0.0, "precision": 0.0, "recall": 0.0, "roc_auc": None}

    result = {
        "f1": float(f1_score(y_true, y_pred, average="weighted", zero_division=0)),
        "precision": float(precision_score(y_true, y_pred, average="weighted", zero_division=0)),
        "recall": float(recall_score(y_true, y_pred, average="weighted", zero_division=0)),
        "roc_auc": None,
    }

    if y_prob is not None:
        y_prob = np.asarray(y_prob, dtype=float)
        try:
            if y_prob.ndim == 1 or (y_prob.ndim == 2 and y_prob.shape[1] == 2):
                prob = y_prob if y_prob.ndim == 1 else y_prob[:, 1]
                result["roc_auc"] = float(roc_auc_score(y_true, prob))
            else:
                result["roc_auc"] = float(roc_auc_score(y_true, y_prob, multi_class="ovr"))
        except (ValueError, TypeError):
            result["roc_auc"] = None

    return result

def ranking_metrics(y_true, y_pred, k: int = 10) -> dict:
    """Calculate ranking metrics at K.

    Returns: Precision@K, Recall@K, NDCG@K
    """
    y_true = np.asarray(y_true, dtype=float)
    y_pred = np.asarray(y_pred, dtype=float)

    if len(y_true) == 0 or k <= 0:
        return {"precision_at_k": 0.0, "recall_at_k": 0.0, "ndcg_at_k": 0.0}

    k = min(k, len(y_true))

    # Sort by predicted score (descending)
    order = np.argsort(y_pred)[::-1]
    y_true_sorted = y_true[order]

    # Precision@K
    precision_at_k = float(np.sum(y_true_sorted[:k]) / k)

    # Recall@K
    relevant_total = np.sum(y_true)
    recall_at_k = (
        float(np.sum(y_true_sorted[:k]) / relevant_total)
        if relevant_total > 0
        else 0.0
    )

    # NDCG@K
    dcg = float(np.sum(y_true_sorted[:k] / np.log2(np.arange(2, k + 2))))
    ideal_order = np.argsort(y_true)[::-1]
    y_true_ideal = y_true[ideal_order]
    idcg = float(np.sum(y_true_ideal[:k] / np.log2(np.arange(2, k + 2))))
    ndcg_at_k = dcg / idcg if idcg > 0 else 0.0

    return {
        "precision_at_k": precision_at_k,
        "recall_at_k": recall_at_k,
        "ndcg_at_k": ndcg_at_k,
    }