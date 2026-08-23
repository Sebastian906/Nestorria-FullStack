"""Shared test fixtures for ai-service."""

import pytest
from httpx import ASGITransport, AsyncClient

from app.main import app

@pytest.fixture
def anyio_backend():
    """Use asyncio as the async backend for anyio/pytest-anyio."""
    return "asyncio"

@pytest.fixture
async def client():
    """Async test client for FastAPI app.

    Uses httpx.ASGITransport to test without starting a real server.
    """
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac