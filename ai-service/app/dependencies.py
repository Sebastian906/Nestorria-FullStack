"""Dependency injection for FastAPI.

Add shared dependencies here as the service grows:
- Database connections
- ML model clients
- Vector store clients
- External API clients

Example (future):
    async def get_db():
        ...

    async def get_model_client():
        ...
"""

from typing import Any

from app.config import get_settings

async def get_config() -> Any:
    """Return application settings as a dependency."""
    return get_settings()

async def get_price_predictor():
    """Get the price predictor singleton."""
    from app.ml.price.predictor import PricePredictor
    predictor = PricePredictor()
    predictor.load_model()
    return predictor

async def get_cancellation_predictor():
    """Get the cancellation predictor singleton."""
    from app.ml.cancellation.predictor import CancellationPredictor
    predictor = CancellationPredictor()
    predictor.load_model()
    return predictor