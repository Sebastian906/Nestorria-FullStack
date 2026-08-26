"""In-memory sliding window rate limiter middleware.

ponytail: per-IP sliding window, no external deps.
For distributed deployments, swap to Redis-backed limiter.
"""

import time
from collections import defaultdict

from fastapi import HTTPException, Request
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.responses import Response

# ponytail: dict[ip, list[timestamp]]. Fine for single-process.
# Upgrade to Redis if you need multi-process or distributed limiting.
_buckets: dict[str, list[float]] = defaultdict(list)


class RateLimitMiddleware(BaseHTTPMiddleware):
    """Rate limiter using sliding window per client IP.

    Args:
        max_requests: Maximum requests per window.
        window_seconds: Window size in seconds.
    """

    def __init__(self, app, max_requests: int = 10, window_seconds: int = 60):
        super().__init__(app)
        self.max_requests = max_requests
        self.window_seconds = window_seconds

    async def dispatch(
        self, request: Request, call_next: RequestResponseEndpoint
    ) -> Response:
        client_ip = request.client.host if request.client else "unknown"
        now = time.monotonic()
        cutoff = now - self.window_seconds

        # Clean old entries and count current window
        timestamps = _buckets[client_ip]
        _buckets[client_ip] = [t for t in timestamps if t > cutoff]

        if len(_buckets[client_ip]) >= self.max_requests:
            retry_after = int(_buckets[client_ip][0] - cutoff) + 1
            raise HTTPException(
                status_code=429,
                detail="Rate limit exceeded. Try again later.",
                headers={"Retry-After": str(retry_after)},
            )

        _buckets[client_ip].append(now)
        return await call_next(request)
