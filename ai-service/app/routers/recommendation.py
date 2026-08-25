"""Recommendation scoring endpoint.

POST /ml/recommendation/score — returns ranked properties with score breakdown.
"""

import structlog
from fastapi import APIRouter, Depends, HTTPException, Request

from app.dependencies import get_config
from app.ml.recommendation.ranker import HybridRanker
from app.ml.recommendation.schemas import (
    RecommendationRequest,
    RecommendationResponse,
    PropertyRanking,
    ScoreBreakdown,
)

logger = structlog.get_logger("ai-service.ml.recommendation")

router = APIRouter(prefix="/ml/recommendation", tags=["recommendation"])

# Singleton ranker — loaded once at startup
_ranker: HybridRanker | None = None

def get_ranker() -> HybridRanker:
    """Get or initialize the hybrid ranker singleton."""
    global _ranker
    if _ranker is None:
        from app.config import get_settings
        settings = get_settings()
        _ranker = HybridRanker(
            graph_weight=settings.recommendation_graph_weight,
            content_weight=settings.recommendation_content_weight,
            collab_weight=settings.recommendation_collab_weight,
        )
    return _ranker

@router.post("/score", response_model=RecommendationResponse)
async def score_recommendations(
    body: RecommendationRequest,
    http_request: Request,
    ranker: HybridRanker = Depends(get_ranker),
):
    """Score and rank candidate properties for a user.
    
    Requires authenticated request (API key from Spring Boot).
    
    The request includes candidatePropertyIds. In a full implementation,
    the service would fetch property data and user history from Spring Boot.
    For now, this endpoint expects the caller to provide sufficient context.
    """
    # API key validation
    config = await get_config()
    api_key = http_request.headers.get("X-API-Key")
    if config.api_key and api_key != config.api_key:
        raise HTTPException(status_code=401, detail="Invalid API key")

    # Assign A/B variant
    variant = HybridRanker.assign_variant(body.userId)

    # TODO: In full implementation, fetch properties and user history here
    # For now, return a placeholder that demonstrates the contract
    logger.info(
        "recommendation_score_request",
        user_id=body.userId,
        n_candidates=len(body.candidatePropertyIds),
        variant=variant,
    )

    # Placeholder: return equal scores for all candidates
    # The real implementation would call RecommendationService.score()
    rankings = []
    n = len(body.candidatePropertyIds)
    for i, pid in enumerate(body.candidatePropertyIds):
        score = round(1.0 - (i / max(n, 1)), 4)  # placeholder ordering
        rankings.append(PropertyRanking(
            propertyId=pid,
            score=score,
            breakdown=ScoreBreakdown(graph=score, content=score, collab=score),
        ))

    return RecommendationResponse(
        rankings=rankings,
        model="hybrid_v1",
        variant=variant,
    )