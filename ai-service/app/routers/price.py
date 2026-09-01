"""Price prediction endpoint.

POST /ml/price/predict — returns predicted price for a property.
"""

import structlog
from fastapi import APIRouter, Depends, HTTPException, Request

from app.ml.price.predictor import PricePredictor
from app.ml.price.schemas import PricePredictionRequest, PricePredictionResponse

logger = structlog.get_logger("ai-service.ml.price")

router = APIRouter(prefix="/ml/price", tags=["price"])

# Singleton predictor — loaded once at startup
_predictor: PricePredictor | None = None

def get_predictor() -> PricePredictor:
    """Get or initialize the price predictor singleton."""
    global _predictor
    if _predictor is None:
        _predictor = PricePredictor()
        _predictor.load_model()
    return _predictor


@router.post("/predict", response_model=PricePredictionResponse)
async def predict_price(
    body: PricePredictionRequest,
    predictor: PricePredictor = Depends(get_predictor),
):
    # Auth handled by ApiKeyAuthMiddleware
    if not predictor.is_available:
        raise HTTPException(
            status_code=503,
            detail="Price model not available",
        )

    result = predictor.predict(body.model_dump())
    if result is None:
        raise HTTPException(
            status_code=500,
            detail="Prediction failed",
        )

    return PricePredictionResponse(**result)