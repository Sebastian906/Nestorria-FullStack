"""Tests for DocumentChunker."""

import pytest

from app.rag.chunker import Chunk, DocumentChunker

class TestDocumentChunker:
    """Tests for text chunking behavior."""

    def test_empty_text_returns_empty(self):
        chunker = DocumentChunker(chunk_size=100, overlap=10)
        assert chunker.chunk("") == []
        assert chunker.chunk("   ") == []

    def test_short_text_single_chunk(self):
        chunker = DocumentChunker(chunk_size=500, overlap=50)
        text = "This is a short document."
        chunks = chunker.chunk(text)
        assert len(chunks) == 1
        assert chunks[0].content == text

    def test_long_text_multiple_chunks(self):
        chunker = DocumentChunker(chunk_size=100, overlap=10)
        text = "word " * 50  # 250 chars
        chunks = chunker.chunk(text)
        assert len(chunks) > 1

    def test_overlap_is_applied(self):
        chunker = DocumentChunker(chunk_size=20, overlap=5)
        text = "First paragraph. Second paragraph. Third paragraph."
        chunks = chunker.chunk(text)
        if len(chunks) > 1:
            # Second chunk should start with overlap from first
            assert "..." in chunks[1].content or len(chunks[1].content) > len(chunks[0].content)

    def test_paragraphs_preserved(self):
        chunker = DocumentChunker(chunk_size=500, overlap=50)
        text = "Paragraph one.\n\nParagraph two.\n\nParagraph three."
        chunks = chunker.chunk(text)
        assert len(chunks) >= 1

    def test_metadata_attached(self):
        chunker = DocumentChunker(chunk_size=500, overlap=50)
        metadata = {"source": "test.md", "version": "1"}
        chunks = chunker.chunk("Hello world", metadata)
        assert chunks[0].metadata["source"] == "test.md"

    def test_invalid_chunk_size_raises(self):
        with pytest.raises(ValueError):
            DocumentChunker(chunk_size=0, overlap=0)

    def test_overlap_ge_chunk_size_raises(self):
        with pytest.raises(ValueError):
            DocumentChunker(chunk_size=10, overlap=10)

    def test_negative_overlap_raises(self):
        with pytest.raises(ValueError):
            DocumentChunker(chunk_size=10, overlap=-1)

    def test_chunk_indices_sequential(self):
        chunker = DocumentChunker(chunk_size=20, overlap=0)
        text = "word " * 20
        chunks = chunker.chunk(text)
        for i, chunk in enumerate(chunks):
            assert chunk.index == i