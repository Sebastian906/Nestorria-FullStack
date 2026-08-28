"""Tests for PgVectorStore (mocked psycopg2)."""

from unittest.mock import MagicMock, patch

import numpy as np
import pytest

from app.rag.chunker import Chunk
from app.rag.vector_store import PgVectorStore

@pytest.fixture
def mock_conn():
    """Mock psycopg2 connection."""
    conn = MagicMock()
    conn.closed = False
    cursor = MagicMock()
    conn.cursor.return_value.__enter__ = lambda s: cursor
    conn.cursor.return_value.__exit__ = MagicMock(return_value=False)
    return conn, cursor

@pytest.fixture
def store(mock_conn):
    """Create PgVectorStore with mocked connection pool."""
    conn, cursor = mock_conn
    mock_pool = MagicMock()
    mock_pool.getconn.return_value = conn
    s = PgVectorStore()
    s._pool = mock_pool
    return s, conn, cursor

class TestPgVectorStore:
    """Tests for vector store operations."""

    def test_insert_stores_chunks(self, store):
        s, conn, cursor = store
        chunks = [
            Chunk(content="chunk 1", metadata={}, index=0),
            Chunk(content="chunk 2", metadata={}, index=1),
        ]
        embeddings = np.random.rand(2, 384).astype(np.float32)

        result = s.insert(chunks, embeddings, source="test.md", version="1")

        assert result == 2
        # 2 INSERTs (no DELETE — ingestor handles cleanup via delete_source)
        assert cursor.execute.call_count == 2

    def test_insert_rollback_on_error(self, store):
        s, conn, cursor = store
        cursor.execute.side_effect = Exception("DB error")
        chunks = [Chunk(content="chunk 1", metadata={}, index=0)]
        embeddings = np.random.rand(1, 384).astype(np.float32)

        with pytest.raises(Exception):
            s.insert(chunks, embeddings, source="test.md", version="1")

        conn.rollback.assert_called_once()

    def test_search_returns_results(self, store):
        s, conn, cursor = store
        cursor.fetchall.return_value = [
            ("uuid-1", "content 1", "test.md", "1", {"key": "val"}, 0.85),
            ("uuid-2", "content 2", "test.md", "1", {}, 0.72),
        ]

        query_emb = np.random.rand(384).astype(np.float32)
        results = s.search(query_emb, top_k=5)

        assert len(results) == 2
        assert results[0].score >= results[1].score

    def test_search_with_source_filter(self, store):
        s, conn, cursor = store
        cursor.fetchall.return_value = []

        query_emb = np.random.rand(384).astype(np.float32)
        s.search(query_emb, top_k=5, filters={"source": "test.md"})

        # Verify WHERE clause was added
        call_args = cursor.execute.call_args
        assert "WHERE source = %s" in call_args[0][0]

    def test_get_count(self, store):
        s, conn, cursor = store
        cursor.fetchone.return_value = (42,)

        count = s.get_count()
        assert count == 42

    def test_close_connection(self, store):
        s, conn, cursor = store
        s.close()
        s._pool.closeall.assert_called_once()

    def test_insert_length_mismatch_raises(self, store):
        s, conn, cursor = store
        chunks = [Chunk(content="c1", metadata={}, index=0)]
        embeddings = np.random.rand(2, 384).astype(np.float32)

        with pytest.raises(ValueError, match="length mismatch"):
            s.insert(chunks, embeddings, source="test.md")