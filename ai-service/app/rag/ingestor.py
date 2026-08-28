"""RAG document ingestion pipeline.

Coordinates: validate → chunk → embed → store.
"""

import time

import structlog

from app.rag.chunker import DocumentChunker
from app.rag.embedder import TextEmbedder
from app.rag.vector_store import PgVectorStore

logger = structlog.get_logger("ai-service.rag.ingestor")

class DocumentIngestor:
    """Orchestrate document ingestion into the vector store.

    Args:
        store: PgVectorStore instance.
        chunker: DocumentChunker instance.
        embedder: TextEmbedder instance.
    """

    def __init__(
        self,
        store: PgVectorStore,
        chunker: DocumentChunker,
        embedder: TextEmbedder,
    ):
        self.store = store
        self.chunker = chunker
        self.embedder = embedder

    def ingest(
        self,
        source: str,
        content: str,
        metadata: dict | None = None,
        version: str = "1",
        user_id: str | None = None,
    ) -> dict:
        """Ingest a document into the vector store.

        Args:
            source: Document source identifier.
            content: Document text content.
            metadata: Additional metadata to attach to chunks.
            version: Document version.
            user_id: User ID owning the document.

        Returns:
            Dict with chunks_created, source, version.
        """
        t0 = time.perf_counter()

        if metadata is None:
            metadata = {}

        # Merge source and version into metadata
        chunk_metadata = {**metadata, "source": source, "version": version}

        # Delete stale chunks before chunking — ensures empty content
        # still removes obsolete data for this source+version.
        self.store.delete_source(source, version)

        # Chunk
        chunks = self.chunker.chunk(content, chunk_metadata)
        if not chunks:
            logger.info("no_chunks_created", source=source, version=version)
            return {"chunks_created": 0, "source": source, "version": version}

        chunk_ms = (time.perf_counter() - t0) * 1000

        # Embed
        t1 = time.perf_counter()
        texts = [c.content for c in chunks]
        embeddings = self.embedder.embed(texts)
        embed_ms = (time.perf_counter() - t1) * 1000

        # Store
        t2 = time.perf_counter()
        inserted = self.store.insert(chunks, embeddings, source, version, user_id)
        store_ms = (time.perf_counter() - t2) * 1000

        total_ms = (time.perf_counter() - t0) * 1000

        logger.info(
            "ingestion_completed",
            source=source,
            version=version,
            chunks_created=inserted,
            chunk_ms=round(chunk_ms, 1),
            embed_ms=round(embed_ms, 1),
            store_ms=round(store_ms, 1),
            total_ms=round(total_ms, 1),
        )

        return {"chunks_created": inserted, "source": source, "version": version}
