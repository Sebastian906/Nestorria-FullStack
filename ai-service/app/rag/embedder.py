"""Text embedding using sentence-transformers.

Provides a singleton model instance for efficient embedding generation.
Normalizes embeddings for cosine similarity search with pgvector.
"""

import time

import numpy as np
import structlog

logger = structlog.get_logger("ai-service.rag.embedder")

_model = None
_model_name = None

def _get_model(model_name: str):
    """Get or load the sentence-transformer model (singleton)."""
    global _model, _model_name
    if _model is None or _model_name != model_name:
        from sentence_transformers import SentenceTransformer
        t0 = time.perf_counter()
        _model = SentenceTransformer(model_name)
        _model_name = model_name
        elapsed_s = time.perf_counter() - t0
        logger.info(
            "embedding_model_loaded",
            model=model_name,
            elapsed_s=round(elapsed_s, 2),
        )
    return _model


class TextEmbedder:
    """Generate text embeddings using sentence-transformers.

    Args:
        model_name: HuggingFace model name.
        embedding_dim: Expected embedding dimension.
    """

    def __init__(self, model_name: str = "all-MiniLM-L6-v2", embedding_dim: int = 384):
        self.model_name = model_name
        self.embedding_dim = embedding_dim

    def embed(self, texts: list[str]) -> np.ndarray:
        """Embed a list of texts.

        Args:
            texts: List of strings to embed.

        Returns:
            numpy array of shape (N, embedding_dim), normalized.

        Raises:
            ValueError: If texts is empty.
        """
        if not texts:
            raise ValueError("Cannot embed empty list of texts")

        model = _get_model(self.model_name)

        t0 = time.perf_counter()
        embeddings = model.encode(
            texts,
            normalize_embeddings=True,
            show_progress_bar=False,
        )
        elapsed_ms = (time.perf_counter() - t0) * 1000

        # Validate dimension
        if embeddings.shape[1] != self.embedding_dim:
            raise ValueError(
                f"Model returned {embeddings.shape[1]}-dim vectors, "
                f"expected {self.embedding_dim}"
            )

        logger.info(
            "embeddings_generated",
            count=len(texts),
            dimension=self.embedding_dim,
            elapsed_ms=round(elapsed_ms, 1),
        )
        return embeddings