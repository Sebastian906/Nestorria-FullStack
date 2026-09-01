"""Tests for health and readiness endpoints."""

import pytest

@pytest.mark.anyio
async def test_health_returns_200(client):
    """GET /health must return 200 with valid structure."""
    response = await client.get("/health")

    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert data["service"] == "ai-service"
    assert "version" in data
    assert "timestamp" in data

@pytest.mark.anyio
async def test_health_has_timestamp(client):
    """Health response must include an ISO timestamp."""
    response = await client.get("/health")
    data = response.json()

    # Timestamp should be a non-empty string (ISO format)
    assert isinstance(data["timestamp"], str)
    assert len(data["timestamp"]) > 0

@pytest.mark.anyio
async def test_ready_returns_200_without_database(client):
    """GET /ready returns 200 when DATABASE_URL is not configured."""
    from unittest.mock import patch
    from app.config import Settings

    mock_settings = Settings(database_url=None)
    with patch("app.routers.health.get_settings", return_value=mock_settings):
        response = await client.get("/ready")

    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ready"
    assert "checks" in data
    assert data["checks"]["database"] == "not_configured"

@pytest.mark.anyio
async def test_ready_returns_valid_structure(client):
    """Readiness response must always include status and checks."""
    response = await client.get("/ready")
    data = response.json()

    assert "status" in data
    assert "checks" in data
    assert isinstance(data["checks"], dict)

@pytest.mark.anyio
async def test_health_does_not_expose_secrets(client):
    """Health response must not leak internal configuration."""
    response = await client.get("/health")
    data = response.json()

    # Ensure no sensitive keys are in the response
    serialized = str(data)
    assert "password" not in serialized.lower()
    assert "secret" not in serialized.lower()
    assert "api_key" not in serialized.lower()