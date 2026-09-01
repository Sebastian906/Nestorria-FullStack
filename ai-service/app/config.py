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

    # database_url: optional, service works without it
    database_url: str | None = None
    # api_key: required in production (ApiKeyAuthMiddleware enforces this).
    # Optional in development/staging — allows unauthenticated requests.
    api_key: str | None = None

    # CORS
    cors_origins: str = "http://localhost:5173,http://localhost:5174"

    # ML Configuration
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

    # Visual Search — experimental
    visual_search_enabled: bool = False
    visual_search_embedding_dim: int = 2048
    visual_search_model: str = "resnet50"
    visual_search_rate_limit: int = 10  # max requests per minute per IP
    visual_search_rate_window: int = 60  # window in seconds

    # RAG Configuration
    rag_embedding_model: str = "all-MiniLM-L6-v2"
    rag_embedding_dim: int = 384
    rag_chunk_size: int = 500  # characters
    rag_chunk_overlap: int = 50  # characters
    rag_top_k: int = 5
    rag_similarity_threshold: float = 0.7
    rag_table_name: str = "rag_documents"
    rag_rate_limit: int = 10  # max ingestion requests per window per IP
    rag_rate_window: int = 60  # window in seconds

    # LLM Configuration
    llm_provider: Literal["groq"] = "groq"
    llm_api_key: str = ""
    llm_model: str = "openai/gpt-oss-20b"
    llm_max_tokens: int = 1024
    llm_temperature: float = 0.7
    llm_timeout: int = 30
    llm_chat_rate_limit: int = 20       # messages per user per hour
    llm_chat_rate_window: int = 3600    # 1 hour
    conversation_ttl: int = 1800        # 30 min in-memory
    conversation_max_messages: int = 10
    # Spring Boot Client (for tool execution)
    spring_boot_base_url: str = "http://localhost:4000"
    spring_boot_api_key: str = ""
    tools_rate_limit: int = 30       # max tool calls per minute per IP
    tools_rate_window: int = 60      # window in seconds

    @model_validator(mode="after")
    def _validate_ml_sizes(self) -> "Settings":
        """Validate ML split sizes and recommendation weights."""
        # ML split sizes
        for field in ("test_size", "validation_size"):
            value = getattr(self, field)
            if not (0.0 < value < 1.0):
                raise ValueError(f"{field} must be strictly between 0 and 1, got {value}")
        if self.test_size + self.validation_size >= 1.0:
            raise ValueError(
                f"test_size ({self.test_size}) + validation_size ({self.validation_size}) "
                "must be less than 1.0"
            )

        # Recommendation weights
        rec_weights = (
            self.recommendation_graph_weight,
            self.recommendation_content_weight,
            self.recommendation_collab_weight,
        )
        for field, value in zip(
            ("recommendation_graph_weight", "recommendation_content_weight", "recommendation_collab_weight"),
            rec_weights,
        ):
            if value < 0:
                raise ValueError(f"{field} must be non-negative, got {value}")
        if sum(rec_weights) <= 0:
            raise ValueError(
                f"Sum of recommendation weights must be positive, got {sum(rec_weights)}"
            )

        # Conversation settings must be positive
        if self.conversation_ttl <= 0:
            raise ValueError(f"conversation_ttl must be positive, got {self.conversation_ttl}")
        if self.conversation_max_messages <= 0:
            raise ValueError(
                f"conversation_max_messages must be positive, got {self.conversation_max_messages}"
            )

        return self

@lru_cache
def get_settings() -> Settings:
    """Singleton settings instance. Cached after first call."""
    return Settings()