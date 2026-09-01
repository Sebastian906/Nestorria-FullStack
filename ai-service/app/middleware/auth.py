"""Centralized API key authentication middleware.

Validates X-API-Key header on every request. Skips configured paths.
Uses secrets.compare_digest to prevent timing attacks.
"""

import secrets
from collections.abc import Sequence

import structlog
from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.responses import JSONResponse, Response

from app.config import get_settings

logger = structlog.get_logger("ai-service.middleware.auth")

class ApiKeyAuthMiddleware(BaseHTTPMiddleware):
    """Validate X-API-Key on all requests except excluded paths.

    Args:
        exclude_paths: Paths that don't require auth (e.g., /health, /docs).
    """

    def __init__(self, app, exclude_paths: Sequence[str] | None = None):
        super().__init__(app)
        self.exclude_paths = set(exclude_paths or [])

    async def dispatch(
        self, request: Request, call_next: RequestResponseEndpoint
    ) -> Response:
        # Skip excluded paths (health probes, docs)
        if request.url.path in self.exclude_paths:
            return await call_next(request)

        settings = get_settings()

        # If no API key configured: allow in dev, reject in prod
        if not settings.api_key:
            if settings.environment == "production":
                logger.error("api_key_not_configured", path=request.url.path)
                return JSONResponse(
                    status_code=500,
                    content={"detail": "API key not configured"},
                )
            return await call_next(request)

        # Validate API key with constant-time comparison
        provided = request.headers.get("X-API-Key", "")
        if not provided or not secrets.compare_digest(provided, settings.api_key):
            logger.warning(
                "invalid_api_key",
                path=request.url.path,
                client=request.client.host if request.client else "unknown",
            )
            return JSONResponse(
                status_code=401,
                content={"detail": "Invalid or missing API key"},
            )

        # Mark request as authenticated — downstream middleware (audit)
        # can trust client-supplied identity headers only when this is set.
        request.state.authenticated = True

        return await call_next(request)