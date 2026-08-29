"""Pydantic schemas for RAG API requests and responses."""

from typing import Any, Literal

from pydantic import BaseModel, Field

class IngestRequest(BaseModel):
    """Request body for POST /rag/ingest."""
    source: str = Field(..., min_length=1, max_length=500, description="Document source identifier")
    content: str = Field(..., min_length=1, description="Document content to ingest")
    metadata: dict[str, Any] = Field(default_factory=dict, description="Additional metadata")
    version: str = Field(default="1", pattern=r"^\d+$", description="Document version (numeric string)")

class IngestResponse(BaseModel):
    """Response for POST /rag/ingest."""
    chunks_created: int
    source: str
    version: str

class RetrieveRequest(BaseModel):
    """Request body for POST /rag/retrieve."""
    query: str = Field(..., min_length=1, description="Search query")
    top_k: int = Field(default=5, ge=1, le=50, description="Maximum results")
    source_filter: str | None = Field(default=None, description="Filter by source")
    user_id: str = Field(..., min_length=1, description="User ID for access control")

class RetrievalResult(BaseModel):
    """A single retrieval result."""
    content: str
    score: float = Field(..., ge=0.0, le=1.0)
    source: str
    metadata: dict[str, Any] = Field(default_factory=dict)

class RetrieveResponse(BaseModel):
    """Response for POST /rag/retrieve."""
    results: list[RetrievalResult]

class ChatRequest(BaseModel):
    """Request body for POST /rag/chat."""
    message: str = Field(..., min_length=1, max_length=2000, description="User message")
    conversation_id: str | None = Field(default=None, description="Existing conversation ID")

class ChatEvent(BaseModel):
    """SSE event for chat stream."""
    type: Literal["start", "token", "end", "error"]
    content: str | None = None
    conversation_id: str | None = None
    sources: list[str] | None = None
    error: str | None = None

class ConversationMessage(BaseModel):
    """A single message in conversation history."""
    role: Literal["user", "assistant"]
    content: str

class ConversationResponse(BaseModel):
    """Response for GET /rag/conversations/{id}."""
    conversation_id: str
    messages: list[ConversationMessage]