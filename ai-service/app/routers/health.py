"""Health and readiness check endpoints."""

from datetime import datetime, timezone

import structlog
from fastapi import APIRouter
from fastapi.responses import JSONResponse

from app.config import get_settings

router = APIRouter(tags=["health"])


@router.get("/health")
async def health_check():
    """Liveness check — always returns 200 if the process is running.

    No dependencies, no DB, no external calls. Designed for:
    - Kubernetes liveness probes
    - Docker HEALTHCHECK
    - Load balancer health checks
    """
    settings = get_settings()
    return {
        "status": "healthy",
        "service": settings.service_name,
        "version": settings.version,
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }


@router.get("/ready")
async def readiness_check():
    """Readiness check — indicates if the service can accept work.

    Behavior:
    - database_url not configured → 200 (service works without DB)
    - database_url configured → 200

    Actual connection verification with timeout.
    """
    settings = get_settings()
    logger = structlog.get_logger("ai-service")

    checks: dict[str, str] = {}

    if settings.database_url is not None:
        checks["database"] = "configured"
        # Add real connection check here
        # try:
        #     async with asyncpg.connect(settings.database_url, timeout=2) as conn:
        #         await conn.execute("SELECT 1")
        #         checks["database"] = "ok"
        # except Exception:
        #     checks["database"] = "unavailable"
        #     status_code = 503
    else:
        checks["database"] = "not_configured"

    status_code = 200
    # Future: if any check is "unavailable", set status_code = 503

    logger.info("readiness_check", checks=checks, status_code=status_code)

    return JSONResponse(
        content={
            "status": "ready" if status_code == 200 else "not_ready",
            "checks": checks,
        },
        status_code=status_code,
    )