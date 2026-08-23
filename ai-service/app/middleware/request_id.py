"""Request ID middleware.

- Reads X-Request-ID from incoming request header if present.
- Generates a new UUID4 if not present.
- Stores it in request.state for downstream use.
- Adds it to structlog context so every log includes it.
- Returns it in the response header.
"""

import uuid

import structlog
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

class RequestIdMiddleware(BaseHTTPMiddleware):
    """Middleware that manages request IDs for observability."""

    HEADER_NAME = "X-Request-ID"

    async def dispatch(self, request: Request, call_next) -> Response:
        # Read or generate request ID
        request_id = request.headers.get(self.HEADER_NAME) or str(uuid.uuid4())

        # Store on request state for handlers
        request.state.request_id = request_id

        # Bind to structlog context — all logs in this request will include it
        structlog.contextvars.bind_contextvars(request_id=request_id)

        try:
            response = await call_next(request)
        finally:
            # Clean up structlog context
            structlog.contextvars.unbind_contextvars("request_id")

        # Add to response header
        response.headers[self.HEADER_NAME] = request_id

        return response