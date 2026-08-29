"""Chat endpoint with SSE streaming."""

import json

import structlog
from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import StreamingResponse

from app.dependencies import get_config
from app.rag.schemas import ChatRequest, ConversationResponse, ConversationMessage
from app.routers.rag import _get_components, _require_api_key

logger = structlog.get_logger("ai-service.routers.chat")

router = APIRouter(prefix="/rag", tags=["chat"])

_generator = None
_conversation_manager = None

async def _get_generator():
    """Get or initialize RAG generator with all dependencies."""
    global _generator, _conversation_manager
    if _generator is None:
        from app.config import get_settings
        from app.rag.conversation import ConversationManager
        from app.rag.generator import RAGGenerator
        from app.rag.guardrails import Guardrails
        from app.rag.llm import LLMClient

        _, _, _, retriever = await _get_components()
        llm = LLMClient()
        conversation_manager = ConversationManager()
        guardrails = Guardrails()
        _generator = RAGGenerator(
            retriever=retriever,
            llm=llm,
            conversation_manager=conversation_manager,
            guardrails=guardrails,
        )
        _conversation_manager = conversation_manager
    return _generator, _conversation_manager

def _get_user_id(request: Request) -> str:
    """Extract user ID from request header.

    Expects X-User-ID header set by Spring Boot gateway.
    """
    user_id = request.headers.get("X-User-ID")
    if not user_id:
        raise HTTPException(status_code=401, detail="User identity required")
    return user_id

@router.post("/chat")
async def chat_stream(
    body: ChatRequest,
    request: Request,
):
    """POST /rag/chat — SSE streaming chat endpoint.

    Returns text/event-stream with events: start, token, end, error.
    """
    await _require_api_key(request)
    user_id = _get_user_id(request)
    generator, _ = await _get_generator()

    async def event_generator():
        async for event in generator.generate_stream(
            query=body.message,
            user_id=user_id,
            conversation_id=body.conversation_id,
        ):
            yield f"data: {json.dumps(event)}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )

@router.get("/conversations/{conversation_id}", response_model=ConversationResponse)
async def get_conversation(
    conversation_id: str,
    request: Request,
):
    """GET /rag/conversations/{id} — retrieve conversation history."""
    await _require_api_key(request)
    user_id = _get_user_id(request)
    _, conversation_manager = await _get_generator()

    conv = conversation_manager.get_conversation(conversation_id, user_id)
    if not conv:
        raise HTTPException(status_code=404, detail="Conversation not found")

    return ConversationResponse(
        conversation_id=conv.id,
        messages=[ConversationMessage(**m) for m in conv.messages],
    )