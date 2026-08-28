"""Text chunking with paragraph awareness and overlap.

Splits text into chunks preserving paragraph boundaries when possible.
Uses character-based chunking with configurable overlap.
"""

import re
from dataclasses import dataclass, field

import structlog

logger = structlog.get_logger("ai-service.rag.chunker")

@dataclass
class Chunk:
    """A text chunk with metadata."""
    content: str
    metadata: dict = field(default_factory=dict)
    index: int = 0

class DocumentChunker:
    """Split text into overlapping chunks, preserving paragraphs.

    Args:
        chunk_size: Maximum chunk size in characters.
        overlap: Number of overlapping characters between chunks.
    """

    def __init__(self, chunk_size: int = 500, overlap: int = 50):
        if chunk_size <= 0:
            raise ValueError(f"chunk_size must be positive, got {chunk_size}")
        if overlap < 0:
            raise ValueError(f"overlap must be non-negative, got {overlap}")
        if overlap >= chunk_size:
            raise ValueError(f"overlap ({overlap}) must be less than chunk_size ({chunk_size})")

        self.chunk_size = chunk_size
        self.overlap = overlap

    def chunk(self, text: str, metadata: dict | None = None) -> list[Chunk]:
        """Split text into chunks with overlap.

        Args:
            text: The text to chunk.
            metadata: Metadata to attach to each chunk.

        Returns:
            List of Chunk objects.
        """
        if metadata is None:
            metadata = {}

        if not text or not text.strip():
            logger.info("empty_text", source=metadata.get("source"))
            return []

        # Split into paragraphs first
        paragraphs = re.split(r"\n\s*\n", text)
        paragraphs = [p.strip() for p in paragraphs if p.strip()]

        chunks: list[Chunk] = []

        for paragraph in paragraphs:
            if len(paragraph) <= self.chunk_size:
                chunks.append(Chunk(
                    content=paragraph,
                    metadata=metadata.copy(),
                    index=len(chunks),
                ))
            else:
                # Paragraph too large — split by sentences
                sentences = re.split(r"(?<=[.!?])\s+", paragraph)
                current = ""
                for sentence in sentences:
                    if len(current) + len(sentence) + 1 <= self.chunk_size:
                        current = f"{current} {sentence}".strip() if current else sentence
                    else:
                        if current:
                            chunks.append(Chunk(
                                content=current,
                                metadata=metadata.copy(),
                                index=len(chunks),
                            ))
                        # If single sentence exceeds chunk_size, split by characters
                        if len(sentence) > self.chunk_size:
                            chunks.extend(self._split_long_text(sentence, metadata))
                            current = ""
                        else:
                            current = sentence
                if current:
                    chunks.append(Chunk(
                        content=current,
                        metadata=metadata.copy(),
                        index=len(chunks),
                    ))

        # Apply overlap
        if self.overlap > 0 and len(chunks) > 1:
            chunks = self._apply_overlap(chunks)

        logger.info(
            "chunking_completed",
            source=metadata.get("source"),
            chunks_created=len(chunks),
            input_length=len(text),
        )
        return chunks

    def _split_long_text(self, text: str, metadata: dict) -> list[Chunk]:
        """Split text that exceeds chunk_size by characters.

        Used when a single sentence or paragraph is longer than the configured
        chunk size. Applies overlap between consecutive chunks.

        Args:
            text: Text to split.
            metadata: Metadata to attach to each chunk.

        Returns:
            List of Chunk objects.
        """
        chunks = []
        start = 0
        while start < len(text):
            end = min(start + self.chunk_size, len(text))
            chunks.append(Chunk(
                content=text[start:end],
                metadata=metadata.copy(),
                index=len(chunks),
            ))
            start += self.chunk_size - self.overlap
        return chunks

    def _apply_overlap(self, chunks: list[Chunk]) -> list[Chunk]:
        """Add overlapping content from previous chunk to current.

        Prepends the last N characters (configured overlap) from the previous
        chunk to the current chunk, prefixed with "..." for context continuity.

        Args:
            chunks: List of chunks to process.

        Returns:
            List of chunks with overlap applied.
        """
        if len(chunks) <= 1:
            return chunks

        overlapped = [chunks[0]]
        for i in range(1, len(chunks)):
            prev_content = chunks[i - 1].content
            overlap_text = prev_content[-self.overlap:] if len(prev_content) > self.overlap else prev_content
            new_content = f"...{overlap_text} {chunks[i].content}"
            overlapped.append(Chunk(
                content=new_content,
                metadata=chunks[i].metadata,
                index=i,
            ))
        return overlapped