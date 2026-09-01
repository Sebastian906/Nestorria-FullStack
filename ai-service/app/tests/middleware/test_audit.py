"""Tests for AuditLogMiddleware."""

import json

import pytest
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


def _parse_audit_events(capsys) -> list[dict]:
    """Parse structlog JSON output and return audit_request events."""
    captured = capsys.readouterr()
    events = []
    for line in captured.out.strip().splitlines():
        if not line:
            continue
        try:
            entry = json.loads(line)
        except json.JSONDecodeError:
            continue
        if entry.get("event") == "audit_request":
            events.append(entry)
    return events


@pytest.mark.anyio
async def test_audit_logs_request(client, capsys):
    """Every request should generate an audit_request event with structured fields."""
    response = await client.get("/health")
    assert response.status_code == 200

    events = _parse_audit_events(capsys)
    assert len(events) >= 1, "No audit_request event found in structlog output"

    event = events[-1]
    assert event["method"] == "GET"
    assert event["path"] == "/health"
    assert event["status"] == 200
    assert "client" in event
    assert "duration_ms" in event


@pytest.mark.anyio
async def test_audit_request_id_matches_header(client, capsys):
    """Audit event request_id must match the X-Request-ID response header."""
    response = await client.get("/health")
    assert response.status_code == 200

    header_id = response.headers.get("x-request-id")
    assert header_id, "X-Request-ID header missing from response"

    events = _parse_audit_events(capsys)
    assert len(events) >= 1, "No audit_request event found"

    event = events[-1]
    assert event.get("request_id") == header_id, (
        f"Audit request_id={event.get('request_id')} "
        f"does not match header X-Request-ID={header_id}"
    )


@pytest.mark.anyio
async def test_audit_logs_method_and_path(client, capsys):
    """Audit log should record HTTP method and path for different endpoints."""
    response = await client.get("/ready")
    assert response.status_code == 200

    events = _parse_audit_events(capsys)
    assert len(events) >= 1, "No audit_request event found"

    event = events[-1]
    assert event["method"] == "GET"
    assert event["path"] == "/ready"
