"""Hybrid ranking model combining heuristic, content, and collaborative scores.

Score fusion with configurable weights. Includes A/B variant assignment.
"""

from __future__ import annotations

import hashlib
from dataclasses import dataclass

import numpy as np
import structlog

logger = structlog.get_logger("ai-service.ml.recommendation")

@dataclass
class ScoreComponents:
    """Individual score components for a property."""
    graph: float
    content: float
    collab: float

    def weighted_sum(self, weights: tuple[float, float, float]) -> float:
        """Compute weighted combination."""
        return (
            weights[0] * self.graph
            + weights[1] * self.content
            + weights[2] * self.collab
        )

@dataclass
class RankedProperty:
    """Property with final score and breakdown."""
    property_id: str
    score: float
    breakdown: ScoreComponents

class HybridRanker:
    """Combine heuristic, content, and collaborative scores.
    
    Score normalization:
    - Graph score: raw similarity weight from PropertyRecommendationService algorithm
      (0 to ~6 depending on matches). Normalized to [0, 1] via min-max or fixed scale.
    - Content score: cosine similarity from TF-IDF+SVD embeddings. Already in [0, 1].
    - Collab score: frequency count from collaborative filtering. Normalized to [0, 1].
    
    Fallback:
    - If content unavailable → use only graph + collab (redistribute weights)
    - If collab unavailable → use only graph + content
    - If both unavailable → use graph only (pure heuristic)
    """

    def __init__(
        self,
        graph_weight: float = 0.4,
        content_weight: float = 0.3,
        collab_weight: float = 0.3,
    ):
        # Validate weights sum to ~1.0
        total = graph_weight + content_weight + collab_weight
        if abs(total - 1.0) > 0.01:
            logger.warning(
                "ranker_weights_not_normalized",
                total=total,
                normalizing=True,
            )
            graph_weight /= total
            content_weight /= total
            collab_weight /= total

        self.graph_weight = graph_weight
        self.content_weight = content_weight
        self.collab_weight = collab_weight
        self._weights = (graph_weight, content_weight, collab_weight)

    def rank(
        self,
        candidate_ids: list[str],
        graph_scores: dict[str, float],
        content_scores: dict[str, float] | None = None,
        collab_scores: dict[str, float] | None = None,
    ) -> list[RankedProperty]:
        """Rank candidates by hybrid score.
        
        Args:
            candidate_ids: property IDs to rank
            graph_scores: {property_id: score} from attribute similarity
            content_scores: {property_id: score} from content embeddings (optional)
            collab_scores: {property_id: score} from collaborative filtering (optional)
            
        Returns:
            List of RankedProperty sorted by score descending
        """
        content_scores = content_scores or {}
        collab_scores = collab_scores or {}

        # Determine active weights based on available scores
        weights = self._adjust_weights(bool(content_scores), bool(collab_scores))

        ranked = []
        for pid in candidate_ids:
            graph = graph_scores.get(pid, 0.0)
            content = content_scores.get(pid, 0.0)
            collab = collab_scores.get(pid, 0.0)

            components = ScoreComponents(graph=graph, content=content, collab=collab)
            final_score = components.weighted_sum(weights)

            ranked.append(RankedProperty(
                property_id=pid,
                score=round(final_score, 4),
                breakdown=components,
            ))

        # Sort by score descending
        ranked.sort(key=lambda r: r.score, reverse=True)

        logger.info(
            "ranker_scored",
            n_candidates=len(candidate_ids),
            weights=weights,
            top_score=ranked[0].score if ranked else 0.0,
        )

        return ranked

    def _adjust_weights(
        self, has_content: bool, has_collab: bool
    ) -> tuple[float, float, float]:
        """Redistribute weights when components are missing."""
        if has_content and has_collab:
            return self._weights

        if has_content and not has_collab:
            # Redistribute collab weight to graph and content
            g = self.graph_weight + self.collab_weight * 0.5
            c = self.content_weight + self.collab_weight * 0.5
            total = g + c
            return (g / total, c / total, 0.0)

        if not has_content and has_collab:
            # Redistribute content weight to graph and collab
            g = self.graph_weight + self.content_weight * 0.5
            co = self.collab_weight + self.content_weight * 0.5
            total = g + co
            return (g / total, 0.0, co / total)

        # No content, no collab → graph only
        return (1.0, 0.0, 0.0)

    @staticmethod
    def assign_variant(user_id: str, experiment_ratio: float = 0.5) -> str:
        """Assign A/B variant using deterministic hash.
        
        Args:
            user_id: stable user identifier
            experiment_ratio: fraction of users in treatment group (0.0-1.0)
            
        Returns:
            "control" or "treatment"
        """
        hash_val = int(hashlib.sha256(user_id.encode()).hexdigest(), 16)
        bucket = (hash_val % 10000) / 10000.0
        return "treatment" if bucket < experiment_ratio else "control"