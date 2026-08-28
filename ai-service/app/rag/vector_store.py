"""pgvector storage for RAG document chunks.

Manages the rag_documents table: schema initialization, insert, and
similarity search using cosine distance.
"""

import math
import time
import uuid
from dataclasses import dataclass

import numpy as np
import psycopg2
import psycopg2.extras
import structlog

from app.config import get_settings
from app.rag.chunker import Chunk

logger = structlog.get_logger("ai-service.rag.vector_store")

@dataclass
class SearchResult:
    """A single search result from pgvector."""
    id: str
    content: str
    score: float
    source: str
    version: str
    metadata: dict

def _normalize_similarity(value: float) -> float:
    """Clamp similarity to [0.0, 1.0], convert non-finite to 0.0."""
    if not math.isfinite(value):
        return 0.0
    return max(0.0, min(1.0, value))

class PgVectorStore:
    """Manages RAG document storage and retrieval via pgvector.

    Uses cosine similarity (1 - L2 distance for normalized vectors).
    Sequential scan without index — acceptable for <1000 documents.
    """

    def __init__(self, table_name: str = "rag_documents"):
        self.table_name = table_name
        self._conn = None
        logger.info("vector_store_initialized", table=table_name)

    def _get_connection(self):
        """Get or create database connection."""
        if self._conn is None or self._conn.closed:
            settings = get_settings()
            if not settings.database_url:
                raise RuntimeError("DATABASE_URL not configured for RAG")
            # Convert JDBC format if needed
            url = settings.database_url
            if url.startswith("jdbc:"):
                url = url.replace("jdbc:postgresql://", "postgresql://", 1)
            self._conn = psycopg2.connect(url)
            logger.info("rag_connection_established")
        return self._conn

    def initialize(self, embedding_dim: int = 384) -> None:
        """Create the RAG table and extension if not exists.

        Must be called once before first insert/search.
        """
        conn = self._get_connection()
        with conn.cursor() as cur:
            # Ensure pgvector extension
            cur.execute("CREATE EXTENSION IF NOT EXISTS vector")

            # Create RAG documents table
            cur.execute(f"""
                CREATE TABLE IF NOT EXISTS {self.table_name} (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    content TEXT NOT NULL,
                    embedding vector({embedding_dim}) NOT NULL,
                    metadata JSONB DEFAULT '{{}}'::jsonb,
                    source TEXT NOT NULL,
                    version INTEGER DEFAULT 1,
                    created_at TIMESTAMP DEFAULT NOW()
                )
            """)

            # Create index only if table was just created (no rows)
            cur.execute(f"SELECT COUNT(*) FROM {self.table_name}")
            count = cur.fetchone()[0]
            if count == 0:
                # ponytail: IVFFlat requires at least 100 rows for lists>1.
                # Skip index for small datasets — sequential scan is faster.
                logger.info("rag_table_empty_skipping_index", table=self.table_name)

        conn.commit()
        logger.info("rag_schema_initialized", table=self.table_name)

    def insert(self, chunks: list[Chunk], embeddings: np.ndarray, source: str, version: str = "1") -> int:
        """Insert chunks with embeddings into pgvector.

        Replaces existing chunks for the same source (upsert by source+version).

        Args:
            chunks: List of Chunk objects.
            embeddings: numpy array of shape (N, embedding_dim).
            source: Document source identifier.
            version: Document version.

        Returns:
            Number of chunks inserted.
        """
        if len(chunks) != len(embeddings):
            raise ValueError(f"chunks ({len(chunks)}) and embeddings ({len(embeddings)}) length mismatch")

        conn = self._get_connection()

        try:
            # Delete existing chunks for this source+version
            with conn.cursor() as cur:
                cur.execute(
                    f"DELETE FROM {self.table_name} WHERE source = %s AND version = %s",
                    (source, version),
                )

            # Insert new chunks
            with conn.cursor() as cur:
                for chunk, embedding in zip(chunks, embeddings):
                    cur.execute(
                        f"""
                        INSERT INTO {self.table_name} (id, content, embedding, metadata, source, version)
                        VALUES (%s, %s, %s::vector, %s, %s, %s)
                        """,
                        (
                            str(uuid.uuid4()),
                            chunk.content,
                            embedding.tolist(),
                            psycopg2.extras.Json(chunk.metadata),
                            source,
                            int(version) if version.isdigit() else 1,
                        ),
                    )

            conn.commit()
            logger.info(
                "chunks_inserted",
                source=source,
                version=version,
                count=len(chunks),
            )
            return len(chunks)

        except Exception as e:
            conn.rollback()
            logger.error(
                "insert_failed",
                source=source,
                error=str(e),
            )
            raise

    def search(
        self,
        query_embedding: np.ndarray,
        top_k: int = 5,
        filters: dict | None = None,
    ) -> list[SearchResult]:
        """Search for similar chunks using cosine distance.

        Args:
            query_embedding: Query vector of shape (embedding_dim,).
            top_k: Maximum results to return.
            filters: Optional dict with 'source' key for filtering.

        Returns:
            List of SearchResult objects ordered by similarity descending.
        """
        conn = self._get_connection()

        # Build query dynamically based on filters
        where_clause = ""
        params = [query_embedding.tolist(), top_k]

        if filters and filters.get("source"):
            where_clause = "WHERE source = %s"
            params.insert(1, filters["source"])

        query = f"""
            SELECT id, content, source, version, metadata,
                   1 - (embedding <=> %s::vector) AS similarity
            FROM {self.table_name}
            {where_clause}
            ORDER BY embedding <=> %s::vector
            LIMIT %s
        """

        # Need to add the embedding param again for ORDER BY
        params.insert(1, query_embedding.tolist())

        t0 = time.perf_counter()
        with conn.cursor() as cur:
            cur.execute(query, params)
            results = [
                SearchResult(
                    id=str(r[0]),
                    content=r[1],
                    score=_normalize_similarity(float(r[5])),
                    source=r[2],
                    version=str(r[3]),
                    metadata=r[4] or {},
                )
                for r in cur.fetchall()
            ]
        elapsed_ms = (time.perf_counter() - t0) * 1000

        logger.info(
            "search_completed",
            results_count=len(results),
            top_k=top_k,
            filters=filters,
            elapsed_ms=round(elapsed_ms, 1),
        )
        return results

    def get_count(self, source: str | None = None) -> int:
        """Return the total number of stored chunks, optionally filtered by source."""
        conn = self._get_connection()
        with conn.cursor() as cur:
            if source:
                cur.execute(f"SELECT COUNT(*) FROM {self.table_name} WHERE source = %s", (source,))
            else:
                cur.execute(f"SELECT COUNT(*) FROM {self.table_name}")
            return cur.fetchone()[0]

    def close(self):
        """Close database connection."""
        if self._conn and not self._conn.closed:
            self._conn.close()
            logger.info("rag_connection_closed")