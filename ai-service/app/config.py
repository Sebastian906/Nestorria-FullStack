"""Centralized configuration using Pydantic Settings."""

from functools import lru_cache
from typing import Literal

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

@lru_cache
def get_settings() -> Settings:
    """Singleton settings instance. Cached after first call."""
    return Settings()