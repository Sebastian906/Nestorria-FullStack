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
    Requires property data and user history in the request body.
    Returns HTTP 503 when required data is unavailable.
    """
    # API key validation
    config = await get_config()
    api_key = http_request.headers.get("X-API-Key")
    if config.api_key and api_key != config.api_key:
        raise HTTPException(status_code=401, detail="Invalid API key")

    # Assign A/B variant
    variant = HybridRanker.assign_variant(body.userId)

    logger.info(
        "recommendation_score_request",
        user_id=body.userId,
        n_candidates=len(body.candidatePropertyIds),
        variant=variant,
    )

    # Validate required data is present
    if not body.properties:
        raise HTTPException(
            status_code=503,
            detail="Property data unavailable for scoring",
        )

    # Use RecommendationService for actual scoring
    from app.ml.recommendation.service import RecommendationService
    service = RecommendationService()

    # Ensure embedder is fitted with available property texts
    if not service.embedder.is_fitted and len(body.properties) >= 2:
        from app.ml.recommendation.features import RecommendationFeatureBuilder
        builder = RecommendationFeatureBuilder()
        texts = [builder.build_property_text(p.model_dump()) for p in body.properties]
        service.embedder.fit_transform(texts)

    # Score properties
    results = service.score(
        user_id=body.userId,
        candidate_ids=body.candidatePropertyIds,
        properties=[p.model_dump() for p in body.properties],
        user_history=body.userHistory.model_dump(),
        context=body.context.model_dump() if body.context else None,
    )

    # Map results to response schema
    rankings = [
        PropertyRanking(
            propertyId=r["propertyId"],
            score=r["score"],
            breakdown=ScoreBreakdown(**r["breakdown"]),
        )
        for r in results
    ]

    return RecommendationResponse(
        rankings=rankings,
        model="hybrid_v1",
        variant=variant,
    )