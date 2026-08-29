"""RAG generator: retrieve → prompt → LLM → validate."""

import json

import structlog

from app.rag.conversation import ConversationManager
from app.rag.guardrails import Guardrails
from app.rag.llm import LLMClient
from app.rag.prompt import build_rag_prompt, format_context
from app.rag.retriever import DocumentRetriever
from app.rag.sources import extract_sources, sanitize_citations
from app.rag.tools.executor import ToolExecutor, ToolExecutionError, parse_tool_calls

logger = structlog.get_logger("ai-service.rag.generator")

# Max tool call rounds to prevent infinite loops
_MAX_TOOL_ROUNDS = 3

# System message added when tool results are injected into conversation
_TOOL_RESULT_PREFIX = "[Tool Result] "


class RAGGenerator:
    """Orchestrate RAG generation: retrieval + LLM + guardrails + sources."""

    def __init__(
        self,
        retriever: DocumentRetriever,
        llm: LLMClient,
        conversation_manager: ConversationManager,
        guardrails: Guardrails,
        tool_executor: ToolExecutor | None = None,
    ):
        self.retriever = retriever
        self.llm = llm
        self.conversation_manager = conversation_manager
        self.guardrails = guardrails
        self.tool_executor = tool_executor

    def _get_tool_descriptions(self) -> str | None:
        """Return tool descriptions for system prompt, or None if no tools."""
        if self.tool_executor:
            return self.tool_executor.registry.to_prompt_descriptions()
        return None

    async def _execute_tool_calls(
        self, tool_calls: list[dict], messages: list[dict]
    ) -> bool:
        """Execute parsed tool calls and append results to messages.

        Returns True if at least one tool was executed successfully.
        """
        if not self.tool_executor:
            return False

        executed = False
        for tc in tool_calls:
            tool_name = tc["name"]
            tool_args = tc.get("args", {})

            try:
                result_str = await self.tool_executor.execute(tool_name, tool_args)
            except ToolExecutionError as e:
                result_str = json.dumps({"error": str(e)})
                logger.warning("tool_call_error", tool=tool_name, error=str(e))

            messages.append({
                "role": "user",
                "content": f"{_TOOL_RESULT_PREFIX}Tool '{tool_name}' returned:\n{result_str}",
            })
            executed = True

        return executed

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

        # 4. Build prompt (with tool descriptions if available)
        history = self.conversation_manager.get_history(conv.id)
        tool_descriptions = self._get_tool_descriptions()
        messages = build_rag_prompt(context, history, query, tool_descriptions=tool_descriptions)

        # 5. LLM generate (no native tools parameter — prompt-based)
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

        # 6. Handle tool calls (prompt-based, max N rounds)
        for _round in range(_MAX_TOOL_ROUNDS):
            tool_calls = parse_tool_calls(response_text)
            if not tool_calls or not self.tool_executor:
                break

            # Add assistant's text (containing tool call) to messages
            messages.append({"role": "assistant", "content": response_text})

            # Execute tools and add results
            await self._execute_tool_calls(tool_calls, messages)

            # Ask LLM to incorporate tool results
            try:
                response_text = await self.llm.generate(messages)
            except Exception as e:
                logger.error("llm_tool_round_error", error=str(e))
                response_text = "I encountered an error processing your request. Please try again."
                break
        else:
            if not response_text:
                response_text = "I was unable to complete the request after multiple attempts."

        # 7. Output guardrails (on full text)
        response_text = self.guardrails.check_output(response_text)

        # 8. Source validation
        sources = extract_sources(response_text, valid_sources)
        # Remove markers that reference unverified sources from persisted/returned text
        response_text = sanitize_citations(response_text, valid_sources)

        # 9. Save to conversation
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

        Yields dicts with type: start, token, content, end, error.
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

        # 5. Build prompt (with tool descriptions if available)
        history = self.conversation_manager.get_history(conv.id)
        tool_descriptions = self._get_tool_descriptions()
        messages = build_rag_prompt(context, history, query, tool_descriptions=tool_descriptions)

        # 6. Stream LLM (no native tools — prompt-based)
        full_response = ""
        try:
            async for token in self.llm.stream(messages):
                full_response += token
        except Exception as e:
            logger.error("llm_stream_error", error=str(e))
            yield {"type": "error", "error": "Service temporarily unavailable. Please try again."}
            return

        # 7. Handle tool calls (prompt-based, non-streaming follow-up)
        for _round in range(_MAX_TOOL_ROUNDS):
            tool_calls = parse_tool_calls(full_response)
            if not tool_calls or not self.tool_executor:
                break

            # Add assistant's text (containing tool call) to messages
            messages.append({"role": "assistant", "content": full_response})

            # Execute tools and add results
            await self._execute_tool_calls(tool_calls, messages)

            # Non-streaming follow-up to get final response
            try:
                full_response = await self.llm.generate(messages)
            except Exception as e:
                logger.error("tool_followup_error", error=str(e))
                full_response = "I encountered an error processing your request."
                break
        else:
            if not full_response:
                full_response = "I was unable to complete the request after multiple attempts."

        # 8. Output guardrails (on full text, before ANY token reaches client)
        full_response = self.guardrails.check_output(full_response)

        # 9. Source validation
        sources = extract_sources(full_response, valid_sources)
        full_response = sanitize_citations(full_response, valid_sources)

        # 10. Save to conversation
        self.conversation_manager.add_message(conv.id, "user", query)
        self.conversation_manager.add_message(conv.id, "assistant", full_response)

        # 11. Emit the full, validated content once
        yield {"type": "content", "content": full_response}
        yield {"type": "end", "sources": sources}
