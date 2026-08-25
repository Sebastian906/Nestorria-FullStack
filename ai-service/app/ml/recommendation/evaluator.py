"""Evaluation metrics for recommendation quality.

Computes Precision@K, Recall@K, NDCG@K per user and aggregates.
Designed for offline evaluation, not real-time scoring.
"""

from __future__ import annotations

import numpy as np
import structlog

logger = structlog.get_logger("ai-service.ml.recommendation")

def precision_at_k(recommended: list[str], relevant: set[str], k: int) -> float:
    """Fraction of top-K recommendations that are relevant.
    
    Args:
        recommended: ordered list of recommended property IDs (duplicates removed)
        relevant: set of property IDs the user actually interacted with
        k: number of top recommendations to consider
        
    Returns:
        Precision@K in [0, 1]
    """
    if k <= 0 or not recommended:
        return 0.0
    # Deduplicate while preserving order
    seen: set[str] = set()
    unique = []
    for pid in recommended:
        if pid not in seen:
            seen.add(pid)
            unique.append(pid)
    effective_k = min(k, len(unique))
    if effective_k <= 0:
        return 0.0
    top_k = unique[:effective_k]
    hits = sum(1 for pid in top_k if pid in relevant)
    return hits / effective_k

def recall_at_k(recommended: list[str], relevant: set[str], k: int) -> float:
    """Fraction of relevant items found in top-K recommendations.
    
    Returns:
        Recall@K in [0, 1]
    """
    if k <= 0 or not relevant or not recommended:
        return 0.0
    # Deduplicate while preserving order
    seen: set[str] = set()
    unique = []
    for pid in recommended:
        if pid not in seen:
            seen.add(pid)
            unique.append(pid)
    effective_k = min(k, len(unique))
    if effective_k <= 0:
        return 0.0
    top_k = unique[:effective_k]
    hits = sum(1 for pid in top_k if pid in relevant)
    return hits / len(relevant)

def ndcg_at_k(recommended: list[str], relevant: set[str], k: int) -> float:
    """Normalized Discounted Cumulative Gain at K.
    
    Measures ranking quality: relevant items ranked higher score better.
    
    Returns:
        NDCG@K in [0, 1]
    """
    if k <= 0 or not recommended or not relevant:
        return 0.0

    # Deduplicate while preserving order
    seen: set[str] = set()
    unique = []
    for pid in recommended:
        if pid not in seen:
            seen.add(pid)
            unique.append(pid)
    effective_k = min(k, len(unique))
    if effective_k <= 0:
        return 0.0

    top_k = unique[:effective_k]

    # DCG: discounted cumulative gain
    dcg = 0.0
    for i, pid in enumerate(top_k):
        if pid in relevant:
            dcg += 1.0 / np.log2(i + 2)  # i+2 because log2(1) = 0

    # Ideal DCG: all relevant items at the top
    ideal_hits = min(len(relevant), effective_k)
    idcg = sum(1.0 / np.log2(i + 2) for i in range(ideal_hits))

    return dcg / idcg if idcg > 0 else 0.0

class RecommendationEvaluator:
    """Evaluate recommendation quality using leave-one-out methodology.
    
    For each user:
    1. Hold out one interaction as ground truth
    2. Rank all other properties
    3. Measure if the held-out property appears in top-K
    
    Aggregates metrics across all users.
    """

    def __init__(self, k_values: list[int] | None = None):
        self.k_values = k_values or [5, 10]

    def evaluate_user(
        self,
        user_id: str,
        recommended: list[str],
        relevant: set[str],
    ) -> dict[str, float]:
        """Compute metrics for a single user.
        
        Returns:
            dict with keys like "precision_at_5", "ndcg_at_10", etc.
        """
        results = {}
        for k in self.k_values:
            results[f"precision_at_{k}"] = precision_at_k(recommended, relevant, k)
            results[f"recall_at_{k}"] = recall_at_k(recommended, relevant, k)
            results[f"ndcg_at_{k}"] = ndcg_at_k(recommended, relevant, k)
        return results

    def evaluate(
        self,
        user_recommendations: dict[str, list[str]],
        user_relevant: dict[str, set[str]],
    ) -> dict[str, float]:
        """Aggregate metrics across all users.
        
        Args:
            user_recommendations: {user_id: ordered list of recommended property IDs}
            user_relevant: {user_id: set of relevant property IDs}
            
        Returns:
            dict with averaged metrics and user count
        """
        if not user_relevant:
            return {
                "user_count": 0,
                **{f"precision_at_{k}": 0.0 for k in self.k_values},
                **{f"recall_at_{k}": 0.0 for k in self.k_values},
                **{f"ndcg_at_{k}": 0.0 for k in self.k_values},
            }

        all_metrics: list[dict[str, float]] = []

        for user_id, relevant in user_relevant.items():
            if not relevant:
                continue
            recommended = user_recommendations.get(user_id, [])
            metrics = self.evaluate_user(user_id, recommended, relevant)
            all_metrics.append(metrics)

        if not all_metrics:
            return {
                "user_count": 0,
                **{f"precision_at_{k}": 0.0 for k in self.k_values},
                **{f"recall_at_{k}": 0.0 for k in self.k_values},
                **{f"ndcg_at_{k}": 0.0 for k in self.k_values},
            }

        # Average across users
        averaged = {}
        for key in all_metrics[0]:
            values = [m[key] for m in all_metrics]
            averaged[key] = round(float(np.mean(values)), 4)

        averaged["user_count"] = len(all_metrics)

        logger.info(
            "evaluation_complete",
            user_count=averaged["user_count"],
            metrics=averaged,
        )

        return averaged