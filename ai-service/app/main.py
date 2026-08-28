"""FastAPI application factory for ai-service."""

from contextlib import asynccontextmanager
from datetime import datetime, timezone

import structlog
from fastapi import FastAPI
from fastapi.responses import JSONResponse

from app.config import get_settings
from app.middleware.error_handler import register_error_handlers
from app.middleware.request_id import RequestIdMiddleware
from app.routers import health
from app.utils.logging import setup_logging


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan: startup and shutdown events."""
    settings = get_settings()
    logger = structlog.get_logger("ai-service")
    logger.info(
        "service_starting",
        service=settings.service_name,
        environment=settings.environment,
        version=settings.version,
    )
    yield
    logger.info("service_stopping", service=settings.service_name)

def create_app() -> FastAPI:
    """Create and configure the FastAPI application."""
    settings = get_settings()

    setup_logging(log_level=settings.log_level)

    application = FastAPI(
        title=settings.service_name,
        version=settings.version,
        description="AI Service for Nestorria — ML/DL/RAG foundation",
        lifespan=lifespan,
        docs_url="/docs" if settings.environment != "production" else None,
        redoc_url="/redoc" if settings.environment != "production" else None,
    )

    # Middleware — order matters: request_id first so it's available to error handlers
    application.add_middleware(RequestIdMiddleware)

    # Error handlers
    register_error_handlers(application)

    # Routers
    application.include_router(health.router)

    # Price prediction router
    from app.routers import price
    application.include_router(price.router)

    # Cancellation prediction router
    from app.routers import cancellation
    application.include_router(cancellation.router)

    # Recommendation scoring router (AI-006)
    from app.routers import recommendation
    application.include_router(recommendation.router)

    # RAG router
    from app.routers import rag
    application.include_router(rag.router)

    # Visual search router — experimental
    if settings.visual_search_enabled:
        from app.routers import visual
        application.include_router(visual.router)

        # Rate limiting for visual search endpoints
        from app.middleware.rate_limit import RateLimitMiddleware
        application.add_middleware(
            RateLimitMiddleware,
            max_requests=settings.visual_search_rate_limit,
            window_seconds=settings.visual_search_rate_window,
        )

    return application

app = create_app()