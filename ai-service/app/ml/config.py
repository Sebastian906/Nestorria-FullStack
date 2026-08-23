"""ML-specific configuration helpers.

Reuses the main application Settings to avoid duplicating
the Pydantic BaseSettings pattern.
"""

from app.config import get_settings

def get_ml_config() -> dict:
    """Return ML-related configuration from the main settings."""
    settings = get_settings()
    return {
        "artifacts_path": settings.artifacts_path,
        "test_size": settings.test_size,
        "validation_size": settings.validation_size,
        "random_state": settings.random_state,
    }