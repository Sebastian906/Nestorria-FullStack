"""In-memory sliding window rate limiter middleware.

ponytail: per-IP sliding window, no external deps.
For distributed deployments, swap to Redis-backed limiter.
"""

import time
from collections import defaultdict

from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.responses import JSONResponse, Response

# ponytail: dict[ip, list[timestamp]]. Fine for single-process.
# Upgrade to Redis if you need multi-process or distributed limiting.
_buckets: dict[str, list[float]] = defaultdict(list)

class RateLimitMiddleware(BaseHTTPMiddleware):
    """Sliding window rate limiter. Key can be per-IP (default) or
    custom (e.g., per-user via key_func).

    Args:
        max_requests: Maximum requests per window.
        window_seconds: Window size in seconds.
        prefix: If set, only apply to routes starting with this path prefix.
        key_func: Optional callable(Request) -> str. Overrides IP-based key.
    """

    def __init__(self, app, max_requests: int = 10, window_seconds: int = 60, prefix: str = "",
                key_func=None, exclude_prefixes: list[str] | None = None):
        super().__init__(app)
        self.max_requests = max_requests
        self.window_seconds = window_seconds
        self.prefix = prefix
        self.key_func = key_func
        self.exclude_prefixes = exclude_prefixes or []

    async def dispatch(
        self, request: Request, call_next: RequestResponseEndpoint
    ) -> Response:
        # Skip if prefix is set and route doesn't match
        if self.prefix and not request.url.path.startswith(self.prefix):
            return await call_next(request)
        # Skip excluded paths (e.g., chat already handled by per-user limiter)
        if any(request.url.path.startswith(ex) for ex in self.exclude_prefixes):
            return await call_next(request)
        if self.key_func:
            key = self.key_func(request)
        else:
            key = request.client.host if request.client else "unknown"
        now = time.monotonic()
        cutoff = now - self.window_seconds

        # Clean old entries and count current window
        timestamps = _buckets[key]
        _buckets[key] = [t for t in timestamps if t > cutoff]

        if len(_buckets[key]) >= self.max_requests:
            retry_after = int(_buckets[key][0] - cutoff) + 1
            return JSONResponse(
                status_code=429,
                content={"detail": "Rate limit exceeded. Try again later."},
                headers={"Retry-After": str(retry_after)},
            )

        _buckets[key].append(now)
        return await call_next(request)
