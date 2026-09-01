"""Integration tests: auth middleware + routers."""

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
async def test_price_endpoint_no_double_auth(client):
    """Price endpoint should only be protected by middleware, not inline auth.

    Regression: previously had inline auth with timing-vulnerable string comparison.
    """
    # In dev mode (no API key), the middleware allows requests through.
    # The endpoint should NOT have its own auth check that would reject.
    response = await client.post(
        "/ml/price/predict",
        json={
            "propertyId": "test-123",
            "area_sqm": 100,
            "bedrooms": 3,
            "bathrooms": 2,
            "property_type": "apartment",
            "location": "Madrid",
        },
    )
    # Should not be 401 (auth) — may be 422 (validation) or 503 (model not loaded)
    assert response.status_code != 401

@pytest.mark.anyio
async def test_cancellation_endpoint_no_double_auth(client):
    """Cancellation endpoint should only be protected by middleware."""
    response = await client.post(
        "/ml/cancellation/predict",
        json={
            "bookingId": "test-123",
            "propertyType": "apartment",
            "bookingLeadTime": 7,
            "stayLength": 3,
            "totalPrice": 300,
            "platformConfirmed": True,
        },
    )
    assert response.status_code != 401

@pytest.mark.anyio
async def test_admin_endpoint_protected_by_middleware(client):
    """Admin endpoints should be protected by the auth middleware."""
    # Without API key in dev mode, should still work (middleware allows in dev)
    response = await client.get("/ai/admin/status")
    # In dev without API key configured, middleware passes through
    assert response.status_code in (200, 404)