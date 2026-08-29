"""LLM client abstraction for Groq."""

import time
from typing import AsyncIterator

import structlog
from groq import AsyncGroq

from app.config import get_settings

logger = structlog.get_logger("ai-service.rag.llm")

class LLMClient:
    """Async LLM client wrapping Groq SDK.

    Supports generate() for single response and stream() for SSE.
    """

    def __init__(self):
        settings = get_settings()
        self.client = AsyncGroq(api_key=settings.llm_api_key)
        self.model = settings.llm_model
        self.max_tokens = settings.llm_max_tokens
        self.temperature = settings.llm_temperature
        self.timeout = settings.llm_timeout

    async def generate(self, messages: list[dict]) -> str:
        """Generate a single completion (non-streaming)."""
        t0 = time.perf_counter()
        try:
            response = await self.client.chat.completions.create(
                model=self.model,
                messages=messages,
                max_tokens=self.max_tokens,
                temperature=self.temperature,
                timeout=self.timeout,
                stream=False,
            )
            latency_ms = (time.perf_counter() - t0) * 1000
            content = response.choices[0].message.content or ""
            usage = response.usage

            logger.info(
                "llm_generate_completed",
                model=self.model,
                latency_ms=round(latency_ms, 1),
                tokens_prompt=usage.prompt_tokens if usage else 0,
                tokens_completion=usage.completion_tokens if usage else 0,
            )
            return content

        except Exception as e:
            latency_ms = (time.perf_counter() - t0) * 1000
            logger.error(
                "llm_generate_failed",
                model=self.model,
                latency_ms=round(latency_ms, 1),
                error_type=type(e).__name__,
            )
            raise

    async def stream(self, messages: list[dict]) -> AsyncIterator[str]:
        """Stream tokens one by one (for SSE)."""
        t0 = time.perf_counter()
        try:
            response = await self.client.chat.completions.create(
                model=self.model,
                messages=messages,
                max_tokens=self.max_tokens,
                temperature=self.temperature,
                timeout=self.timeout,
                stream=True,
            )
            async for chunk in response:
                delta = chunk.choices[0].delta if chunk.choices else None
                if delta and delta.content:
                    yield delta.content

            latency_ms = (time.perf_counter() - t0) * 1000
            logger.info("llm_stream_completed", model=self.model, latency_ms=round(latency_ms, 1))

        except Exception as e:
            latency_ms = (time.perf_counter() - t0) * 1000
            logger.error(
                "llm_stream_failed",
                model=self.model,
                latency_ms=round(latency_ms, 1),
                error_type=type(e).__name__,
            )
            raise