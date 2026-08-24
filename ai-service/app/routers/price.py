"""Price prediction endpoint.

POST /ml/price/predict — returns predicted price for a property.
"""

import structlog
from fastapi import APIRouter, Depends, HTTPException, Request

from app.dependencies import get_config
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
    request: PricePredictionRequest,
    predictor: PricePredictor = Depends(get_predictor),
):
    """Predict property price.

    Requires authenticated request (API key from Spring Boot).
    """
    # API key validation (if configured)
    config = get_config()
    api_key = request.headers.get("X-API-Key") if hasattr(request, "headers") else None
    if config.api_key and api_key != config.api_key:
        raise HTTPException(status_code=401, detail="Invalid API key")

    if not predictor.is_available:
        raise HTTPException(
            status_code=503,
            detail="Price model not available",
        )

    result = predictor.predict(request.model_dump())
    if result is None:
        raise HTTPException(
            status_code=500,
            detail="Prediction failed",
        )

    return PricePredictionResponse(**result)