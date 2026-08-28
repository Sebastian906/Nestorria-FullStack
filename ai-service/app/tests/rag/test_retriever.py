"""Tests for DocumentRetriever (mocked store + embedder)."""

from unittest.mock import MagicMock

import numpy as np
import pytest

from app.rag.retriever import DocumentRetriever
from app.rag.vector_store import SearchResult

@pytest.fixture
def components():
    """Create retriever with mocked dependencies."""
    store = MagicMock()
    embedder = MagicMock()

    # Mock embed to return a known vector
    query_emb = np.random.rand(384).astype(np.float32)
    embedder.embed.return_value = query_emb.reshape(1, -1)

    # Mock search to return results
    store.search.return_value = [
        SearchResult(
            id="1", content="result 1", score=0.9,
            source="doc.md", version="1", metadata={"key": "val"},
        ),
        SearchResult(
            id="2", content="result 2", score=0.5,
            source="doc.md", version="1", metadata={},
        ),
    ]

    retriever = DocumentRetriever(
        store=store,
        embedder=embedder,
        similarity_threshold=0.7,
    )
    return retriever, store, embedder

class TestDocumentRetriever:
    """Tests for RAG retrieval."""

    def test_retrieve_filters_by_threshold(self, components):
        retriever, store, embedder = components

        results = retriever.retrieve("test query", top_k=5)

        # Only result with score 0.9 should pass threshold 0.7
        assert len(results) == 1
        assert results[0]["score"] == 0.9

    def test_retrieve_returns_correct_format(self, components):
        retriever, store, embedder = components

        results = retriever.retrieve("test query", top_k=5)

        assert "content" in results[0]
        assert "score" in results[0]
        assert "source" in results[0]
        assert "metadata" in results[0]

    def test_retrieve_calls_embedder(self, components):
        retriever, store, embedder = components

        retriever.retrieve("how to deploy?", top_k=3)

        embedder.embed.assert_called_once_with(["how to deploy?"])

    def test_retrieve_calls_store_with_filters(self, components):
        retriever, store, embedder = components

        retriever.retrieve("test", top_k=5, source_filter="doc.md")

        store.search.assert_called_once()
        call_kwargs = store.search.call_args
        assert call_kwargs[1]["filters"] == {"source": "doc.md"}

    def test_retrieve_empty_results(self, components):
        retriever, store, embedder = components
        store.search.return_value = []

        results = retriever.retrieve("no match", top_k=5)

        assert results == []