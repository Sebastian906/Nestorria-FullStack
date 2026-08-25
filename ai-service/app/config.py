"""Centralized configuration using Pydantic Settings."""

from functools import lru_cache
from typing import Literal

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    """Application settings — validated on import.

    Environment variables are case-insensitive by default.
    Optional fields (database_url, api_key) allow the service to run
    without external dependencies configured.
    """

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
    )

    # Service identity
    service_name: str = "ai-service"
    version: str = "0.1.0"
    environment: Literal["development", "staging", "production"] = "development"

    # Logging
    log_level: str = "INFO"

    # Optional dependencies — configured later, not required for AI-001
    database_url: str | None = None
    api_key: str | None = None

    # ML Configuration (AI-002)
    artifacts_path: str = "artifacts"
    test_size: float = 0.2
    validation_size: float = 0.1
    random_state: int = 42

    # Recommendation weights (configurable)
    recommendation_graph_weight: float = 0.4
    recommendation_content_weight: float = 0.3
    recommendation_collab_weight: float = 0.3

    # Recommendation ML
    recommendation_svd_components: int = 8  # safe for small datasets
    recommendation_tfidf_max_features: int = 500
    recommendation_min_properties: int = 5  # fallback threshold

    @model_validator(mode="after")
    def _validate_ml_sizes(self) -> "Settings":
        """Validate ML split sizes are strictly between 0 and 1."""
        for field in ("test_size", "validation_size"):
            value = getattr(self, field)
            if not (0.0 < value < 1.0):
                raise ValueError(f"{field} must be strictly between 0 and 1, got {value}")
        if self.test_size + self.validation_size >= 1.0:
            raise ValueError(
                f"test_size ({self.test_size}) + validation_size ({self.validation_size}) "
                "must be less than 1.0"
            )
        return self

@lru_cache
def get_settings() -> Settings:
    """Singleton settings instance. Cached after first call."""
    return Settings()