"""RAG configuration — extends main Settings."""

from pydantic import Field

class RAGSettings:
    """RAG-specific settings. Mixed into main Settings via inheritance."""

    # Embedding model
    rag_embedding_model: str = "all-MiniLM-L6-v2"
    rag_embedding_dim: int = 384

    # Chunking
    rag_chunk_size: int = 500  # characters
    rag_chunk_overlap: int = 50  # characters

    # Retrieval
    rag_top_k: int = 5
    rag_similarity_threshold: float = 0.7

    # pgvector
    rag_table_name: str = "rag_documents"