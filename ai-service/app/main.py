"""FastAPI application factory for ai-service."""

# Force UTF-8 client encoding for psycopg2 on Windows.
# PostgreSQL lc_messages=es_ES causes error messages to be sent in WIN1252/Latin1,
# and psycopg2 C extension crashes trying to decode them as UTF-8.
import os

os.environ["PGCLIENTENCODING"] = "UTF8"
from app.middleware.metrics import MetricsMiddleware

from contextlib import asynccontextmanager
from datetime import datetime, timezone

import structlog
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.config import get_settings
from app.middleware.error_handler import register_error_handlers
from app.middleware.request_id import RequestIdMiddleware
from app.middleware.auth import ApiKeyAuthMiddleware
from app.middleware.audit import AuditLogMiddleware
from app.routers import health
from app.routers import metrics
from app.utils.logging import setup_logging
from app.routers.admin import router as admin_router

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
    from app.middleware.rate_limit import RateLimitMiddleware

    setup_logging(log_level=settings.log_level)

    application = FastAPI(
        title=settings.service_name,
        version=settings.version,
        description="AI Service for Nestorria — ML/DL/RAG foundation",
        lifespan=lifespan,
        docs_url="/docs" if settings.environment != "production" else None,
        redoc_url="/redoc" if settings.environment != "production" else None,
    )

    # Middleware — registration order = outermost first.
    # Starlette executes LAST-registered FIRST on request,
    # so request_id (registered last) runs first and sets
    # request.state.request_id before audit reads it.

    # 1. Rate limiting (outermost — first rejection layer)
    # 2. API key auth (second — rejects unauthenticated after rate limit)
    # 3. CORS (before auth so preflight OPTIONS passes)
    # 4. Audit logging (reads request_id set by step 5)
    # 5. Request ID (innermost — runs first on request, sets request_id)

    # Rate limiting per user for chat — 20 msgs/user/hour
    def _chat_user_key(request):
        return request.headers.get("X-User-ID", request.client.host if request.client else "unknown")

    application.add_middleware(
        RateLimitMiddleware,
        max_requests=settings.llm_chat_rate_limit,
        window_seconds=settings.llm_chat_rate_window,
        prefix="/rag/chat",
        key_func=_chat_user_key,
    )

    application.add_middleware(
        RateLimitMiddleware,
        max_requests=settings.rag_rate_limit,
        window_seconds=settings.rag_rate_window,
        exclude_prefixes=["/rag/chat", "/health", "/ready", "/metrics"],
    )

    # Rate limiting for RAG ingestion
    application.add_middleware(
        RateLimitMiddleware,
        max_requests=settings.rag_rate_limit,
        window_seconds=settings.rag_rate_window,
        prefix="/rag/",
        exclude_prefixes=["/rag/chat", "/metrics"],
    )

    # Rate limiting for ML endpoints (30/min per IP)
    application.add_middleware(
        RateLimitMiddleware,
        max_requests=30,
        window_seconds=60,
        prefix="/ml/",
    )

    # Rate limiting for admin endpoints (10/min per IP)
    application.add_middleware(
        RateLimitMiddleware,
        max_requests=10,
        window_seconds=60,
        prefix="/ai/admin/",
    )

    # API key auth — excludes health probes for K8s/Docker
    application.add_middleware(
        ApiKeyAuthMiddleware,
        exclude_paths=["/health", "/ready", "/metrics"],
    )

    # CORS — must be before auth so preflight OPTIONS requests are allowed
    origins = [o.strip() for o in settings.cors_origins.split(",") if o.strip()]
    application.add_middleware(
        CORSMiddleware,
        allow_origins=origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Audit logging — reads request_id set by RequestIdMiddleware
    application.add_middleware(AuditLogMiddleware)

    # Request ID — innermost, runs first on request, sets request.state.request_id
    application.add_middleware(RequestIdMiddleware)

    # Error handlers
    register_error_handlers(application)

    # Routers
    application.include_router(health.router)

    application.add_middleware(MetricsMiddleware)
    application.include_router(metrics.router)

    # Price prediction router
    from app.routers import price
    application.include_router(price.router)

    # Cancellation prediction router
    from app.routers import cancellation
    application.include_router(cancellation.router)

    # Recommendation scoring router
    from app.routers import recommendation
    application.include_router(recommendation.router)

    # RAG router
    from app.routers import rag
    application.include_router(rag.router)

    # Chat router
    from app.routers import chat
    application.include_router(chat.router)

    # Visual search router — experimental
    if settings.visual_search_enabled:
        from app.routers import visual
        application.include_router(visual.router)

        # Rate limiting for visual search endpoints
        application.add_middleware(
            RateLimitMiddleware,
            max_requests=settings.visual_search_rate_limit,
            window_seconds=settings.visual_search_rate_window,
            prefix="/dl/",
        )

    application.include_router(admin_router, prefix="/ai")

    return application

app = create_app()