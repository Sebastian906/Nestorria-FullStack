"""Cancellation prediction endpoint.

POST /ml/cancellation/predict — returns cancellation probability for a booking.
"""

import structlog
from fastapi import APIRouter, Depends, HTTPException, Request

from app.ml.cancellation.predictor import CancellationPredictor
from app.ml.cancellation.schemas import (
    CancellationPredictionRequest,
    CancellationPredictionResponse,
)

logger = structlog.get_logger("ai-service.ml.cancellation")

router = APIRouter(prefix="/ml/cancellation", tags=["cancellation"])

# Singleton predictor — loaded once at startup
_predictor: CancellationPredictor | None = None

def get_predictor() -> CancellationPredictor:
    """Get or initialize the cancellation predictor singleton."""
    global _predictor
    if _predictor is None:
        _predictor = CancellationPredictor()
        _predictor.load_model()
    return _predictor

@router.post("/predict", response_model=CancellationPredictionResponse)
async def predict_cancellation(
    body: CancellationPredictionRequest,
    predictor: CancellationPredictor = Depends(get_predictor),
):
    # Auth handled by ApiKeyAuthMiddleware
    if not predictor.is_available:
        raise HTTPException(
            status_code=503,
            detail="Cancellation model not available",
        )

    result = predictor.predict(body.model_dump())
    if result is None:
        raise HTTPException(
            status_code=500,
            detail="Prediction failed",
        )

    return CancellationPredictionResponse(**result)