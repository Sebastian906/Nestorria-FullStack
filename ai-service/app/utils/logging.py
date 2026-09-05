"""Structured logging setup using structlog.

Output: JSON lines (one per log entry).
Context: timestamp, level, event, request_id (when available).
"""

import logging
import sys

import structlog


def _make_add_service(environment: str | None):
    """Build processor injecting service identity.

    Keeps "development" only when no environment is configured.
    """
    env = environment or "development"

    def _add_service(logger, method_name, event_dict):
        event_dict.setdefault("service", "nestorria-ai-service")
        event_dict.setdefault("environment", env)
        return event_dict

    return _add_service


def setup_logging(log_level: str = "INFO", environment: str | None = None) -> None:
    """Configure structlog with JSON rendering.

    Called once at application startup.
    Pass Settings.environment explicitly; falls back to ENVIRONMENT
    env var so setup_logging(log_level=...) keeps working.
    """
    import os

    resolved_env = environment or os.getenv("ENVIRONMENT") or "development"
    # ponytail: env via param, not import config (avoids circular import)
    # Configure stdlib logging to work with structlog
    logging.basicConfig(
        format="%(message)s",
        stream=sys.stdout,
        level=getattr(logging, log_level.upper(), logging.INFO),
    )

    # Reduce noise from third-party libraries
    logging.getLogger("uvicorn").setLevel(logging.WARNING)
    logging.getLogger("uvicorn.access").setLevel(logging.WARNING)
    logging.getLogger("uvicorn.error").setLevel(logging.INFO)

    structlog.configure(
        processors=[
            structlog.contextvars.merge_contextvars,
            structlog.processors.add_log_level,
            structlog.processors.TimeStamper(fmt="iso"),
            _make_add_service(resolved_env),
            structlog.dev.ConsoleRenderer() if sys.stderr.isatty() else structlog.processors.JSONRenderer(),
        ],
        wrapper_class=structlog.make_filtering_bound_logger(
            getattr(logging, log_level.upper(), logging.INFO)
        ),
        context_class=dict,
        logger_factory=structlog.PrintLoggerFactory(),
        cache_logger_on_first_use=True,
    )