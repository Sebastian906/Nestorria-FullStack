"""Global exception handlers for FastAPI.

Mirrors the Spring Boot GlobalExceptionHandler pattern:
- Exception → 500 (generic, no stack trace in production)
- RequestException → 502 (external service failure)
- RequestValidationError → 422 (native FastAPI)
- ValidationError → 422 (Pydantic models)
"""

import structlog
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from app.config import get_settings
from app.exceptions import RequestException

def register_error_handlers(app: FastAPI) -> None:
    """Register global exception handlers on the FastAPI app."""

    @app.exception_handler(RequestException)
    async def handle_request_exception(request: Request, exc: RequestException):
        """External HTTP request failures → 502."""
        logger = structlog.get_logger("ai-service")
        request_id = getattr(request.state, "request_id", None)

        logger.warning(
            "external_request_failed",
            status_code=exc.status_code,
            detail=exc.detail,
            request_id=request_id,
        )

        return JSONResponse(
            status_code=502,
            content={
                "detail": "External service error",
                "trace_id": request_id,
            },
        )

    @app.exception_handler(RequestValidationError)
    async def handle_request_validation_error(
        request: Request, exc: RequestValidationError
    ):
        """FastAPI request validation errors → 422."""
        logger = structlog.get_logger("ai-service")
        request_id = getattr(request.state, "request_id", None)

        logger.warning(
            "request_validation_error",
            errors=exc.errors(),
            request_id=request_id,
        )

        return JSONResponse(
            status_code=422,
            content={
                "detail": "Validation error",
                "errors": exc.errors(),
                "trace_id": request_id,
            },
        )

    @app.exception_handler(ValidationError)
    async def handle_pydantic_validation_error(
        request: Request, exc: ValidationError
    ):
        """Pydantic model validation errors → 422."""
        logger = structlog.get_logger("ai-service")
        request_id = getattr(request.state, "request_id", None)

        logger.warning(
            "pydantic_validation_error",
            errors=exc.errors(),
            request_id=request_id,
        )

        return JSONResponse(
            status_code=422,
            content={
                "detail": "Validation error",
                "errors": exc.errors(),
                "trace_id": request_id,
            },
        )

    @app.exception_handler(Exception)
    async def handle_generic_exception(request: Request, exc: Exception):
        """Catch-all → 500. Never expose stack traces or secrets."""
        logger = structlog.get_logger("ai-service")
        request_id = getattr(request.state, "request_id", None)
        settings = get_settings()

        # Log full details always (for debugging), but never return them
        logger.error(
            "unhandled_exception",
            exception_type=type(exc).__name__,
            detail=str(exc),
            request_id=request_id,
            exc_info=exc,
        )

        detail = "Internal server error"
        if settings.environment != "production":
            detail = f"{type(exc).__name__}: {exc}"

        return JSONResponse(
            status_code=500,
            content={
                "detail": detail,
                "trace_id": request_id,
            },
        )