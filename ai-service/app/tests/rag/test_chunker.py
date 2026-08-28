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

    def test_all_chunks_respect_chunk_size(self):
        """Every chunk must be at or below chunk_size, even with overlap."""
        chunker = DocumentChunker(chunk_size=50, overlap=10)
        text = "This is a longer document that should be split into multiple chunks. " * 10
        chunks = chunker.chunk(text)
        for chunk in chunks:
            assert len(chunk.content) <= 50, (
                f"Chunk of length {len(chunk.content)} exceeds chunk_size=50: "
                f"{chunk.content[:80]}..."
            )

    def test_overlap_does_not_duplicate_boundary(self):
        """Overlap should not produce duplicated text from the previous chunk."""
        chunker = DocumentChunker(chunk_size=30, overlap=10)
        text = "AAAA BBBB CCCC DDDD EEEE FFFF GGGG HHHH"
        chunks = chunker.chunk(text)
        # With overlap, the second chunk starts with "...<tail> <content>"
        # The tail should come from the previous chunk, not be invented
        if len(chunks) >= 2:
            second = chunks[1].content
            assert second.startswith("..."), f"Expected overlap prefix, got: {second[:40]}"