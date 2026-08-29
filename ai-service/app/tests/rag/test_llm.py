"""Tests for LLM client (mocked Groq SDK)."""

import asyncio
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from app.rag.llm import LLMClient

@pytest.fixture
def mock_groq():
    """Mock Groq client."""
    with patch("app.rag.llm.AsyncGroq") as MockGroq:
        mock_client = MagicMock()
        MockGroq.return_value = mock_client
        yield mock_client

class TestLLMClient:
    def test_generate_returns_content(self, mock_groq):
        mock_response = MagicMock()
        mock_response.choices = [MagicMock(message=MagicMock(content="Hello world"))]
        mock_response.usage = MagicMock(prompt_tokens=10, completion_tokens=5)
        mock_groq.chat.completions.create = AsyncMock(return_value=mock_response)

        client = LLMClient()
        result = asyncio.run(client.generate([{"role": "user", "content": "Hi"}]))

        assert result == "Hello world"

    def test_generate_raises_on_error(self, mock_groq):
        mock_groq.chat.completions.create = AsyncMock(side_effect=Exception("API error"))

        client = LLMClient()
        with pytest.raises(Exception):
            asyncio.run(client.generate([{"role": "user", "content": "Hi"}]))