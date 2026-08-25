"""Recommendation service orchestrating feature engineering, embeddings, and ranking.

Entry point for the hybrid recommendation pipeline.
"""

from __future__ import annotations

import structlog
import numpy as np

from app.config import get_settings
from app.ml.recommendation.embeddings import ContentEmbedder
from app.ml.recommendation.features import RecommendationFeatureBuilder
from app.ml.recommendation.ranker import HybridRanker

logger = structlog.get_logger("ai-service.ml.recommendation")


class RecommendationService:
    """Orchestrate hybrid recommendation scoring.
    
    Pipeline:
    1. Build property features (text, numeric, categorical)
    2. Compute content embeddings (TF-IDF + SVD)
    3. Compute graph scores (attribute similarity)
    4. Compute collaborative scores (user similarity)
    5. Fuse scores with configurable weights
    6. Return ranked properties with breakdown
    """

    def __init__(self):
        settings = get_settings()
        self.feature_builder = RecommendationFeatureBuilder()
        self.embedder = ContentEmbedder(
            n_components=settings.recommendation_svd_components,
            max_features=settings.recommendation_tfidf_max_features,
            random_state=settings.random_state,
        )
        self.ranker = HybridRanker(
            graph_weight=settings.recommendation_graph_weight,
            content_weight=settings.recommendation_content_weight,
            collab_weight=settings.recommendation_collab_weight,
        )
        self._property_cache: dict[str, dict] = {}
        self._embedding_cache: dict[str, np.ndarray] = {}

    def score(
        self,
        user_id: str,
        candidate_ids: list[str],
        properties: list[dict],
        user_history: dict,
        context: dict | None = None,
    ) -> list[dict]:
        """Score and rank candidate properties for a user.
        
        Args:
            user_id: target user ID
            candidate_ids: property IDs to rank
            properties: list of property data dicts
            user_history: user's booking/favorite/review history
            context: optional context (check-in date, guests)
            
        Returns:
            List of dicts with propertyId, score, breakdown
        """
        # Build property lookup
        property_map = {p["id"]: p for p in properties}

        # 1. Compute graph scores (attribute similarity)
        graph_scores = self._compute_graph_scores(candidate_ids, property_map)

        # 2. Compute content scores (TF-IDF + SVD)
        content_scores = self._compute_content_scores(candidate_ids, property_map)

        # 3. Compute collaborative scores
        collab_scores = self._compute_collab_scores(
            user_id, candidate_ids, user_history
        )

        # 4. Rank by hybrid score
        ranked = self.ranker.rank(
            candidate_ids=candidate_ids,
            graph_scores=graph_scores,
            content_scores=content_scores,
            collab_scores=collab_scores,
        )

        return [
            {
                "propertyId": r.property_id,
                "score": r.score,
                "breakdown": {
                    "graph": round(r.breakdown.graph, 4),
                    "content": round(r.breakdown.content, 4),
                    "collab": round(r.breakdown.collab, 4),
                },
            }
            for r in ranked
        ]

    def _compute_graph_scores(
        self, candidate_ids: list[str], property_map: dict[str, dict]
    ) -> dict[str, float]:
        """Compute attribute similarity scores.
        
        Replicates PropertyRecommendationService.calculateSimilarity() logic:
        - Same city: +3
        - Same type: +2
        - Similar price (within 30%): +1
        - Shared amenities: +1 each
        
        Normalized to [0, 1] by dividing by max possible score (7).
        """
        scores = {}
        for pid in candidate_ids:
            p = property_map.get(pid)
            if p is None:
                scores[pid] = 0.0
                continue

            # Compare against user's preferred properties (from history)
            # For simplicity, use a reference property or average
            score = self._single_property_similarity(p, property_map)
            scores[pid] = score

        return scores

    def _single_property_similarity(
        self, property_a: dict, all_properties: dict[str, dict]
    ) -> float:
        """Compute average similarity of property_a against all others.
        
        Normalized to [0, 1].
        """
        if len(all_properties) <= 1:
            return 0.0

        total_similarity = 0.0
        count = 0
        for pid, p_b in all_properties.items():
            if pid == property_a.get("id"):
                continue
            total_similarity += self._pairwise_similarity(property_a, p_b)
            count += 1

        if count == 0:
            return 0.0

        # Average similarity, normalized by max possible (7)
        avg = total_similarity / count
        return min(avg / 7.0, 1.0)

    def _pairwise_similarity(self, a: dict, b: dict) -> float:
        """Attribute similarity between two properties (0-7 scale)."""
        score = 0.0

        # City match: +3
        if a.get("city") and a.get("city") == b.get("city"):
            score += 3.0

        # Type match: +2
        if a.get("propertyType") and a.get("propertyType") == b.get("propertyType"):
            score += 2.0

        # Price similarity: +1
        price_a = a.get("price", 0) or 0
        price_b = b.get("price", 0) or 0
        if price_a > 0 and price_b > 0:
            diff = abs(price_a - price_b)
            avg = (price_a + price_b) / 2.0
            if diff / avg <= 0.3:
                score += 1.0

        # Shared amenities: +1 each
        amenities_a = set(a.get("amenities", []))
        amenities_b = set(b.get("amenities", []))
        score += len(amenities_a & amenities_b)

        return score

    def _compute_content_scores(
        self, candidate_ids: list[str], property_map: dict[str, dict]
    ) -> dict[str, float] | None:
        """Compute content-based scores using TF-IDF + SVD embeddings.
        
        Returns None if embedder is not fitted or insufficient data.
        """
        if not self.embedder.is_fitted:
            return None

        # Build texts for candidates
        texts = []
        valid_ids = []
        for pid in candidate_ids:
            p = property_map.get(pid)
            if p:
                text = self.feature_builder.build_property_text(p)
                texts.append(text)
                valid_ids.append(pid)

        if not texts:
            return None

        try:
            embeddings = self.embedder.transform(texts)

            # Use first property as query (or could use user preference centroid)
            # For now, compute pairwise similarity between all candidates
            # The "query" is the centroid of user's preferred properties
            scores = {}
            if embeddings.shape[0] > 0:
                # Simple approach: similarity to centroid
                centroid = embeddings.mean(axis=0)
                centroid = centroid / (np.linalg.norm(centroid) + 1e-10)
                similarities = embeddings @ centroid
                similarities = np.clip(similarities, 0.0, 1.0)

                for i, pid in enumerate(valid_ids):
                    scores[pid] = float(similarities[i])

            return scores

        except Exception as e:
            logger.error("content_score_error", error=str(e))
            return None

    def _compute_collab_scores(
        self,
        user_id: str,
        candidate_ids: list[str],
        user_history: dict,
    ) -> dict[str, float] | None:
        """Compute collaborative filtering scores.
        
        Simplified version of UserSimilarityGraphService logic:
        - Find users who booked similar properties
        - Score candidates by how many similar users booked them
        
        Returns None if insufficient data.
        """
        bookings = user_history.get("bookings", [])
        if not bookings:
            return None

        # For now, use a simple frequency-based approach
        # In production, this would use the full bipartite graph
        user_property_ids = {b.get("propertyId") for b in bookings if b.get("propertyId")}

        if not user_property_ids:
            return None

        # Simple heuristic: properties that share attributes with booked ones score higher
        scores = {}
        for pid in candidate_ids:
            p = property_map_global.get(pid) if hasattr(self, '_property_map') else None
            if p is None:
                scores[pid] = 0.0
                continue

            # Count how many booked properties share city/type
            overlap = 0
            for booked_id in user_property_ids:
                booked_p = self._property_cache.get(booked_id)
                if booked_p and self._pairwise_similarity(p, booked_p) > 3.0:
                    overlap += 1

            scores[pid] = min(overlap / max(len(user_property_ids), 1), 1.0)

        return scores