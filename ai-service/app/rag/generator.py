"""RAG generator: retrieve → prompt → LLM → validate."""

import structlog

from app.rag.conversation import ConversationManager
from app.rag.guardrails import Guardrails
from app.rag.llm import LLMClient
from app.rag.prompt import build_rag_prompt, format_context
from app.rag.retriever import DocumentRetriever
from app.rag.sources import extract_sources, sanitize_citations

logger = structlog.get_logger("ai-service.rag.generator")

class RAGGenerator:
    """Orchestrate RAG generation: retrieval + LLM + guardrails + sources."""

    def __init__(
        self,
        retriever: DocumentRetriever,
        llm: LLMClient,
        conversation_manager: ConversationManager,
        guardrails: Guardrails,
    ):
        self.retriever = retriever
        self.llm = llm
        self.conversation_manager = conversation_manager
        self.guardrails = guardrails

    async def generate(
        self,
        query: str,
        user_id: str,
        conversation_id: str | None = None,
    ) -> dict:
        """Generate a complete (non-streaming) response.

        Returns:
            dict with content, sources, conversation_id.
        """
        # 1. Input guardrails
        is_safe, reason = self.guardrails.check_input(query)
        if not is_safe:
            return {
                "content": "I can't process that request. Please rephrase your question.",
                "sources": [],
                "conversation_id": conversation_id or "",
                "blocked": True,
            }

        # 2. Conversation
        conv = self.conversation_manager.get_or_create(conversation_id, user_id)

        # 3. Retrieve context
        results = self.retriever.retrieve(query, top_k=5, user_id=user_id)
        context = format_context(results)
        valid_sources = list({r["source"] for r in results})

        # 4. Build prompt
        history = self.conversation_manager.get_history(conv.id)
        messages = build_rag_prompt(context, history, query)

        # 5. LLM generate
        try:
            response_text = await self.llm.generate(messages)
        except Exception as e:
            logger.error("llm_generate_error", error=str(e))
            return {
                "content": "Service temporarily unavailable. Please try again later.",
                "sources": [],
                "conversation_id": conv.id,
                "blocked": True,
            }

        # 6. Output guardrails
        response_text = self.guardrails.check_output(response_text)

        # 7. Source validation
        sources = extract_sources(response_text, valid_sources)
        # Remove markers that reference unverified sources from persisted/returned text
        response_text = sanitize_citations(response_text, valid_sources)

        # 8. Save to conversation
        self.conversation_manager.add_message(conv.id, "user", query)
        self.conversation_manager.add_message(conv.id, "assistant", response_text)

        return {
            "content": response_text,
            "sources": sources,
            "conversation_id": conv.id,
            "blocked": False,
        }

    async def generate_stream(
        self,
        query: str,
        user_id: str,
        conversation_id: str | None = None,
    ):
        """Generate a streaming response (async generator for SSE).

        Yields dicts with type: start, token, end, error.
        """
        # 1. Input guardrails
        is_safe, reason = self.guardrails.check_input(query)
        if not is_safe:
            yield {"type": "error", "error": "Request blocked by safety filter"}
            return

        # 2. Conversation
        conv = self.conversation_manager.get_or_create(conversation_id, user_id)

        # 3. Start event
        yield {"type": "start", "conversation_id": conv.id}

        # 4. Retrieve context
        results = self.retriever.retrieve(query, top_k=5, user_id=user_id)
        context = format_context(results)
        valid_sources = list({r["source"] for r in results})

        # 5. Build prompt
        history = self.conversation_manager.get_history(conv.id)
        messages = build_rag_prompt(context, history, query)

        # 6. Stream LLM (buffer full text for non-streaming-safe validation)
        full_response = ""
        try:
            async for token in self.llm.stream(messages):
                full_response += token
        except Exception as e:
            logger.error("llm_stream_error", error=str(e))
            yield {"type": "error", "error": "Service temporarily unavailable. Please try again."}
            return

        # 7. Output guardrails (on full text, before ANY token reaches client)
        full_response = self.guardrails.check_output(full_response)

        # 8. Source validation
        sources = extract_sources(full_response, valid_sources)
        full_response = sanitize_citations(full_response, valid_sources)

        # 9. Save to conversation
        self.conversation_manager.add_message(conv.id, "user", query)
        self.conversation_manager.add_message(conv.id, "assistant", full_response)
        
        # 10. End event (emit the full, validated content once)
        yield {"type": "content", "content": full_response}
        yield {"type": "end", "sources": sources}