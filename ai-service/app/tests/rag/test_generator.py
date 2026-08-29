"""Tests for RAG generator (mocked retriever, LLM, conversation, guardrails)."""

from unittest.mock import AsyncMock, MagicMock

import pytest

from app.rag.generator import RAGGenerator

@pytest.fixture
def generator():
    """Create RAGGenerator with mocked dependencies."""
    retriever = MagicMock()
    retriever.retrieve.return_value = [
        {"content": "Rental terms context", "source": "contract_template_v1", "score": 0.92},
    ]

    llm = MagicMock()
    llm.generate = AsyncMock(
        return_value="The contract includes X. [Source: contract_template_v1]"
    )
    llm.stream = MagicMock()
    llm.stream.return_value = _async_iter(["The ", "contract ", "includes X. [Source: contract_template_v1]"])

    conversation_manager = MagicMock()
    conv = MagicMock()
    conv.id = "conv_test"
    conversation_manager.get_or_create.return_value = conv
    conversation_manager.get_history.return_value = []

    guardrails = MagicMock()
    guardrails.check_input.return_value = (True, None)
    guardrails.check_output.side_effect = lambda s: s

    return RAGGenerator(
        retriever=retriever,
        llm=llm,
        conversation_manager=conversation_manager,
        guardrails=guardrails,
    )

async def _async_iter(items):
    for item in items:
        yield item

class TestRAGGeneratorGenerate:
    async def test_generate_returns_content_and_sources(self, generator):
        result = await generator.generate("What's in the contract?", "user1")
        assert result["content"] == "The contract includes X. [Source: contract_template_v1]"
        assert "contract_template_v1" in result["sources"]
        assert result["conversation_id"] == "conv_test"
        assert result["blocked"] is False

    async def test_generate_calls_retriever_with_user(self, generator):
        await generator.generate("question", "user1")
        generator.retriever.retrieve.assert_called_once_with("question", top_k=5, user_id="user1")

    async def test_generate_calls_llm(self, generator):
        await generator.generate("question", "user1")
        assert generator.llm.generate.called

    async def test_generate_saves_to_conversation(self, generator):
        await generator.generate("question", "user1")
        # user message + assistant message
        assert generator.conversation_manager.add_message.call_count == 2

    async def test_generate_blocks_unsafe_query(self, generator):
        generator.guardrails.check_input.return_value = (False, "injection")
        result = await generator.generate("bad query", "user1")
        assert result["blocked"] is True
        # retriever and llm should NOT be called
        generator.retriever.retrieve.assert_not_called()
        generator.llm.generate.assert_not_called()

class TestRAGGeneratorStream:
    async def test_stream_emits_start_tokens_end(self, generator):
        events = [e async for e in generator.generate_stream("question", "user1")]
        types = [e["type"] for e in events]
        assert types == ["start", "token", "token", "token", "end"]
        # end has sources
        assert events[-1]["type"] == "end"
        assert "contract_template_v1" in (events[-1]["sources"] or [])

    async def test_stream_emits_error_on_llm_failure(self, generator):
        def _failing_stream(messages):
            raise Exception("groq down")
        generator.llm.stream = MagicMock(side_effect=_failing_stream)
        events = [e async for e in generator.generate_stream("question", "user1")]
        assert events[-1]["type"] == "error"
        assert "unavailable" in events[-1]["error"].lower()

    async def test_stream_blocks_unsafe_query(self, generator):
        generator.guardrails.check_input.return_value = (False, "injection")
        events = [e async for e in generator.generate_stream("bad", "user1")]
        assert events[0]["type"] == "error"