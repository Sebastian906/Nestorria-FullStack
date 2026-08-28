"""RAG retrieval — coordinate query embedding and similarity search."""

import structlog

from app.rag.embedder import TextEmbedder
from app.rag.vector_store import PgVectorStore

logger = structlog.get_logger("ai-service.rag.retriever")

class DocumentRetriever:
    """Orchestrate RAG retrieval: query → embed → search → filter.

    Args:
        store: PgVectorStore instance.
        embedder: TextEmbedder instance.
        similarity_threshold: Minimum similarity score to include in results.
    """

    def __init__(
        self,
        store: PgVectorStore,
        embedder: TextEmbedder,
        similarity_threshold: float = 0.7,
    ):
        self.store = store
        self.embedder = embedder
        self.similarity_threshold = similarity_threshold

    def retrieve(
        self,
        query: str,
        top_k: int = 5,
        source_filter: str | None = None,
    ) -> list[dict]:
        """Retrieve relevant chunks for a query.

        Args:
            query: Search query text.
            top_k: Maximum results.
            source_filter: Optional source filter.

        Returns:
            List of dicts with content, score, source, metadata.
        """
        # Embed query
        query_embedding = self.embedder.embed([query])[0]

        # Search
        results = self.store.search(
            query_embedding=query_embedding,
            top_k=top_k,
            filters={"source": source_filter} if source_filter else None,
        )

        # Apply similarity threshold
        filtered = [r for r in results if r.score >= self.similarity_threshold]

        logger.info(
            "retrieval_completed",
            query_length=len(query),
            results_total=len(results),
            results_after_threshold=len(filtered),
            threshold=self.similarity_threshold,
        )

        return [
            {
                "content": r.content,
                "score": r.score,
                "source": r.source,
                "metadata": {**r.metadata, "version": r.version},
            }
            for r in filtered
        ]