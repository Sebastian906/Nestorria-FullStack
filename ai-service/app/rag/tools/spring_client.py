"""HTTP client for calling Spring Boot tool endpoints."""

from __future__ import annotations

import time
from typing import Any

import httpx
import structlog

from app.config import get_settings

logger = structlog.get_logger("ai-service.rag.tools.spring_client")

class SpringBootClient:
    """Async HTTP client for Spring Boot internal tool endpoints.

    All requests include X-API-Key for service-to-service auth.
    Timeouts: connect 3s, read 5s (matching Spring Boot AiServiceClient).
    """

    def __init__(self):
        settings = get_settings()
        self.base_url = settings.spring_boot_base_url
        self.api_key = settings.spring_boot_api_key
        self._client = httpx.AsyncClient(
            base_url=self.base_url,
            timeout=httpx.Timeout(connect=3.0, read=5.0, write=5.0, pool=5.0),
            headers=self._build_headers(),
        )

    def _build_headers(self) -> dict[str, str]:
        headers = {"Content-Type": "application/json"}
        if self.api_key:
            headers["X-API-Key"] = self.api_key
        return headers

    async def get(self, path: str, params: dict | None = None) -> dict[str, Any]:
        """GET request to Spring Boot tool endpoint.

        Returns parsed JSON response.
        Raises SpringBootError on failure.
        """
        t0 = time.perf_counter()
        try:
            response = await self._client.get(path, params=params)
            latency_ms = (time.perf_counter() - t0) * 1000

            if response.status_code >= 400:
                logger.warning(
                    "spring_boot_error",
                    path=path,
                    status=response.status_code,
                    latency_ms=round(latency_ms, 1),
                )
                raise SpringBootError(
                    f"Spring Boot returned {response.status_code}: {response.text}"
                )

            logger.info(
                "spring_boot_call",
                path=path,
                status=response.status_code,
                latency_ms=round(latency_ms, 1),
            )
            return response.json()

        except httpx.TimeoutException as e:
            latency_ms = (time.perf_counter() - t0) * 1000
            logger.error(
                "spring_boot_timeout",
                path=path,
                latency_ms=round(latency_ms, 1),
            )
            raise SpringBootError(f"Spring Boot timeout: {e}") from e
        except httpx.RequestError as e:
            latency_ms = (time.perf_counter() - t0) * 1000
            logger.error(
                "spring_boot_request_error",
                path=path,
                latency_ms=round(latency_ms, 1),
                error=str(e),
            )
            raise SpringBootError(f"Spring Boot connection error: {e}") from e

    async def close(self):
        await self._client.aclose()

class SpringBootError(Exception):
    """Error communicating with Spring Boot tool endpoints."""
    pass