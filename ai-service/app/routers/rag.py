"""RAG endpoints.

POST /rag/ingest   — ingest a document
POST /rag/retrieve — similarity search
"""

import asyncio
import secrets
import time

import structlog
from fastapi import APIRouter, Depends, HTTPException, Request

from app.dependencies import get_config
from app.rag.schemas import (
    IngestRequest,
    IngestResponse,
    RetrieveRequest,
    RetrieveResponse,
    RetrievalResult,
)

logger = structlog.get_logger("ai-service.routers.rag")

router = APIRouter(prefix="/rag", tags=["rag"])

_store = None
_embedder = None
_ingestor = None
_retriever = None
_lock = asyncio.Lock()

async def _get_components():
    """Get or initialize RAG components (store, embedder, ingestor, retriever)."""
    global _store, _embedder, _ingestor, _retriever
    if _store is None:
        async with _lock:
            if _store is None:
                from app.config import get_settings
                from app.rag.chunker import DocumentChunker
                from app.rag.embedder import TextEmbedder
                from app.rag.ingestor import DocumentIngestor
                from app.rag.retriever import DocumentRetriever
                from app.rag.vector_store import PgVectorStore

                settings = get_settings()
                _store = PgVectorStore(table_name=settings.rag_table_name)
                _store.initialize(embedding_dim=settings.rag_embedding_dim)

                _embedder = TextEmbedder(
                    model_name=settings.rag_embedding_model,
                    embedding_dim=settings.rag_embedding_dim,
                )

                _ingestor = DocumentIngestor(
                    store=_store,
                    chunker=DocumentChunker(
                        chunk_size=settings.rag_chunk_size,
                        overlap=settings.rag_chunk_overlap,
                    ),
                    embedder=_embedder,
                )

                _retriever = DocumentRetriever(
                    store=_store,
                    embedder=_embedder,
                    similarity_threshold=settings.rag_similarity_threshold,
                )
    return _store, _embedder, _ingestor, _retriever

async def _require_api_key(request: Request):
    """Validate API key. Same pattern as visual.py."""
    config = await get_config()
    if not config.api_key:
        raise HTTPException(status_code=401, detail="API key not configured")

    provided = request.headers.get("X-API-Key", "")
    if not secrets.compare_digest(provided, config.api_key):
        raise HTTPException(status_code=401, detail="Invalid API key")

@router.post("/ingest", response_model=IngestResponse)
async def ingest_document(
    body: IngestRequest,
    http_request: Request = None,
):
    """Ingest a document into the RAG vector store."""
    await _require_api_key(http_request)

    _, _, ingestor, _ = await _get_components()

    loop = asyncio.get_event_loop()
    t0 = time.perf_counter()
    result = await loop.run_in_executor(
        None,
        ingestor.ingest,
        body.source,
        body.content,
        body.metadata,
        body.version,
    )
    elapsed_ms = (time.perf_counter() - t0) * 1000

    logger.info(
        "rag_ingest_completed",
        source=body.source,
        chunks_created=result["chunks_created"],
        elapsed_ms=round(elapsed_ms, 1),
    )

    return IngestResponse(**result)

@router.post("/retrieve", response_model=RetrieveResponse)
async def retrieve_documents(
    body: RetrieveRequest,
    http_request: Request = None,
):
    """Retrieve relevant chunks for a query."""
    await _require_api_key(http_request)

    _, _, _, retriever = await _get_components()

    loop = asyncio.get_event_loop()
    t0 = time.perf_counter()
    results = await loop.run_in_executor(
        None,
        retriever.retrieve,
        body.query,
        body.top_k,
        body.source_filter,
    )
    elapsed_ms = (time.perf_counter() - t0) * 1000

    logger.info(
        "rag_retrieve_completed",
        query_length=len(body.query),
        results_count=len(results),
        elapsed_ms=round(elapsed_ms, 1),
    )

    return RetrieveResponse(
        results=[RetrievalResult(**r) for r in results]
    )