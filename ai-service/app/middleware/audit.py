"""Audit logging middleware.

Logs every request/response pair with structured data for security auditing.
"""

import time

import structlog
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.requests import Request
from starlette.responses import Response

logger = structlog.get_logger("ai-service.audit")

class AuditLogMiddleware(BaseHTTPMiddleware):
    """Log request method, path, status, client IP, and duration."""

    async def dispatch(
        self, request: Request, call_next: RequestResponseEndpoint
    ) -> Response:
        t0 = time.perf_counter()
        request_id = getattr(request.state, "request_id", None)
        client_host = request.client.host if request.client else "unknown"

        # Derive user_id only from verified authentication state.
        # X-User-ID is set by the trusted gateway (Spring Boot) after Clerk
        # JWT validation. Only trust it when the request passed API key auth.
        # Unauthenticated requests (health, docs) get "-" as identity.
        authenticated = getattr(request.state, "authenticated", False)
        user_id = request.headers.get("X-User-ID", "-") if authenticated else "-"

        response = await call_next(request)

        elapsed_ms = round((time.perf_counter() - t0) * 1000, 1)

        logger.info(
            "audit_request",
            method=request.method,
            path=request.url.path,
            status=response.status_code,
            client=client_host,
            user_id=user_id,
            duration_ms=elapsed_ms,
            request_id=request_id,
        )

        return response