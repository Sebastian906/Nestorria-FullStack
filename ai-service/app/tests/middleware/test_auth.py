"""Tests for ApiKeyAuthMiddleware."""

import pytest
from unittest.mock import patch
from httpx import ASGITransport, AsyncClient

from app.main import app

@pytest.fixture
def anyio_backend():
    return "asyncio"

@pytest.fixture
async def client():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac

@pytest.mark.anyio
async def test_health_excluded_from_auth(client):
    """/health must work without API key (K8s probe)."""
    response = await client.get("/health")
    assert response.status_code == 200

@pytest.mark.anyio
async def test_ready_excluded_from_auth(client):
    """/ready must work without API key (K8s probe)."""
    response = await client.get("/ready")
    assert response.status_code == 200

@pytest.mark.anyio
async def test_request_without_api_key_dev_allows(client):
    """In development (no API key configured), protected routes are allowed."""
    # Default config has api_key=None and environment="development"
    # /ai/admin/status is protected but middleware passes through in dev
    response = await client.get("/ai/admin/status")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"

@pytest.mark.anyio
@patch("app.middleware.auth.get_settings")
async def test_request_without_api_key_prod_rejects(mock_settings):
    """In production without API key configured, returns 500."""
    from app.config import Settings

    mock_settings.return_value = Settings(
        environment="production",
        api_key=None,
    )

    from fastapi.testclient import TestClient
    with TestClient(app=app, raise_server_exceptions=False) as tc:
        response = tc.get("/ml/price/predict")
        assert response.status_code == 500
        assert response.json()["detail"] == "API key not configured"

@pytest.mark.anyio
@patch("app.middleware.auth.get_settings")
async def test_request_with_valid_api_key(mock_settings):
    """Request with valid X-API-Key header is allowed."""
    from app.config import Settings

    mock_settings.return_value = Settings(
        environment="development",
        api_key="test-secret-key-123",
    )

    from fastapi.testclient import TestClient
    with TestClient(app=app, raise_server_exceptions=False) as tc:
        response = tc.get(
            "/ai/admin/status",
            headers={"X-API-Key": "test-secret-key-123"},
        )
        assert response.status_code == 200
        assert response.json()["status"] == "ok"

@pytest.mark.anyio
@patch("app.middleware.auth.get_settings")
async def test_request_with_invalid_api_key_rejects(mock_settings):
    """Request with wrong X-API-Key header returns 401."""
    from app.config import Settings

    mock_settings.return_value = Settings(
        environment="development",
        api_key="correct-key",
    )

    from fastapi.testclient import TestClient
    with TestClient(app=app, raise_server_exceptions=False) as tc:
        response = tc.get(
            "/ml/price/predict",
            headers={"X-API-Key": "wrong-key"},
        )
        assert response.status_code == 401

@pytest.mark.anyio
@patch("app.middleware.auth.get_settings")
async def test_request_with_empty_api_key_rejects(mock_settings):
    """Request with empty X-API-Key header returns 401."""
    from app.config import Settings

    mock_settings.return_value = Settings(
        environment="development",
        api_key="some-key",
    )

    from fastapi.testclient import TestClient
    with TestClient(app=app, raise_server_exceptions=False) as tc:
        response = tc.get(
            "/ml/price/predict",
            headers={"X-API-Key": ""},
        )
        assert response.status_code == 401