"""Tests for the tool system: definitions, registry, executor, parse_tool_calls."""

from __future__ import annotations

import json
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from app.rag.tools.base import BaseTool, ToolRegistry
from app.rag.tools.executor import (
    ToolExecutionError,
    ToolExecutor,
    _TOOL_ENDPOINT_MAP,
    parse_tool_calls,
)
from app.rag.tools.spring_client import SpringBootClient, SpringBootError
from app.rag.tools.property_tools import GetPropertyCount, GetAveragePrice, SearchProperties
from app.rag.tools.booking_tools import GetBookingStats
from app.rag.tools.review_tools import GetReviewAverage

# BaseTool
class TestBaseTool:
    def test_to_openai_tool_format(self):
        tool = GetPropertyCount()
        result = tool.to_openai_tool()
        assert result["type"] == "function"
        assert result["function"]["name"] == "get_property_count"
        assert "description" in result["function"]
        assert "parameters" in result["function"]

    def test_to_prompt_description(self):
        tool = GetPropertyCount()
        desc = tool.to_prompt_description()
        assert "get_property_count" in desc
        assert "city" in desc
        assert "propertyType" in desc
        assert "Filter by city" in desc

    def test_validate_args_passthrough(self):
        tool = GetPropertyCount()
        args = {"city": "Madrid"}
        assert tool.validate_args(args) == args

    def test_subclass_missing_name_raises(self):
        with pytest.raises(TypeError, match="must define 'name'"):
            class BadTool(BaseTool):
                description = "test"
                parameters = {}

    def test_subclass_missing_description_raises(self):
        with pytest.raises(TypeError, match="must define 'description'"):
            class BadTool(BaseTool):
                name = "bad"
                parameters = {}

    def test_subclass_missing_parameters_raises(self):
        with pytest.raises(TypeError, match="must define 'parameters'"):
            class BadTool(BaseTool):
                name = "bad"
                description = "test"

# ToolRegistry
class TestToolRegistry:
    def test_register_and_get(self):
        reg = ToolRegistry()
        tool = GetPropertyCount()
        reg.register(tool)
        assert reg.get("get_property_count") is tool

    def test_get_nonexistent_returns_none(self):
        reg = ToolRegistry()
        assert reg.get("nonexistent") is None

    def test_register_duplicate_raises(self):
        reg = ToolRegistry()
        reg.register(GetPropertyCount())
        with pytest.raises(ValueError, match="already registered"):
            reg.register(GetPropertyCount())

    def test_is_allowed(self):
        reg = ToolRegistry()
        reg.register(GetPropertyCount())
        assert reg.is_allowed("get_property_count") is True
        assert reg.is_allowed("nonexistent") is False

    def test_tool_names(self):
        reg = ToolRegistry()
        reg.register(GetPropertyCount())
        reg.register(GetBookingStats())
        assert sorted(reg.tool_names) == ["get_booking_stats", "get_property_count"]

    def test_to_openai_tools(self):
        reg = ToolRegistry()
        reg.register(GetPropertyCount())
        reg.register(GetBookingStats())
        tools = reg.to_openai_tools()
        assert len(tools) == 2
        names = {t["function"]["name"] for t in tools}
        assert names == {"get_property_count", "get_booking_stats"}

    def test_to_prompt_descriptions(self):
        reg = ToolRegistry()
        reg.register(GetPropertyCount())
        reg.register(GetBookingStats())
        desc = reg.to_prompt_descriptions()
        assert "get_property_count" in desc
        assert "get_booking_stats" in desc

    def test_to_prompt_descriptions_empty(self):
        reg = ToolRegistry()
        assert reg.to_prompt_descriptions() == ""

    def test_get_all(self):
        reg = ToolRegistry()
        reg.register(GetPropertyCount())
        reg.register(GetAveragePrice())
        assert len(reg.get_all()) == 2

# Tool Definitions
class TestPropertyTools:
    def test_get_property_count_schema(self):
        tool = GetPropertyCount()
        assert tool.name == "get_property_count"
        props = tool.parameters["properties"]
        assert "city" in props
        assert "propertyType" in props
        assert props["propertyType"]["enum"] == [
            "House", "Apartment", "Villa", "Penthouse",
            "Townhouse", "Commercial", "Land Plot"
        ]

    def test_get_average_price_schema(self):
        tool = GetAveragePrice()
        assert tool.name == "get_average_price"
        assert "city" in tool.parameters["properties"]

    def test_search_properties_schema(self):
        tool = SearchProperties()
        assert tool.name == "search_properties"
        props = tool.parameters["properties"]
        assert "minPrice" in props
        assert "maxPrice" in props
        assert props["minPrice"]["type"] == "integer"

class TestBookingTools:
    def test_get_booking_stats_schema(self):
        tool = GetBookingStats()
        assert tool.name == "get_booking_stats"
        assert tool.parameters["properties"] == {}

class TestReviewTools:
    def test_get_review_average_schema(self):
        tool = GetReviewAverage()
        assert tool.name == "get_review_average"
        assert "propertyId" in tool.parameters["properties"]
        assert tool.parameters["required"] == ["propertyId"]

# parse_tool_calls
class TestParseToolCalls:
    def test_parse_fenced_tool_call(self):
        text = '''Here is the result:
```tool_call
{"name": "get_property_count", "args": {"city": "Madrid"}}
```
'''
        calls = parse_tool_calls(text)
        assert len(calls) == 1
        assert calls[0]["name"] == "get_property_count"
        assert calls[0]["args"]["city"] == "Madrid"

    def test_parse_multiple_tool_calls(self):
        text = '''
```tool_call
{"name": "get_property_count", "args": {"city": "Madrid"}}
```

```tool_call
{"name": "get_average_price", "args": {"propertyType": "House"}}
```
'''
        calls = parse_tool_calls(text)
        assert len(calls) == 2
        assert calls[0]["name"] == "get_property_count"
        assert calls[1]["name"] == "get_average_price"

    def test_parse_no_args(self):
        text = '''
```tool_call
{"name": "get_booking_stats", "args": {}}
```
'''
        calls = parse_tool_calls(text)
        assert len(calls) == 1
        assert calls[0]["name"] == "get_booking_stats"
        assert calls[0]["args"] == {}

    def test_parse_no_tool_calls(self):
        text = "This is just a regular response with no tool calls."
        calls = parse_tool_calls(text)
        assert calls == []

    def test_parse_invalid_json(self):
        text = '''
```tool_call
{invalid json}
```
'''
        calls = parse_tool_calls(text)
        assert calls == []

    def test_parse_inline_format(self):
        text = 'Use the tool: {"tool_call": {"name": "get_booking_stats", "args": {}}}'
        calls = parse_tool_calls(text)
        assert len(calls) == 1
        assert calls[0]["name"] == "get_booking_stats"

    def test_parse_missing_name(self):
        text = '''
```tool_call
{"args": {"city": "Madrid"}}
```
'''
        calls = parse_tool_calls(text)
        assert calls == []

# ToolExecutor
class TestToolExecutor:
    @pytest.fixture
    def mock_client(self):
        client = AsyncMock(spec=SpringBootClient)
        client.get = AsyncMock(return_value={"count": 5})
        return client

    @pytest.fixture
    def registry(self):
        reg = ToolRegistry()
        reg.register(GetPropertyCount())
        reg.register(GetAveragePrice())
        reg.register(GetBookingStats())
        reg.register(GetReviewAverage())
        reg.register(SearchProperties())
        return reg

    @pytest.fixture
    def executor(self, registry, mock_client):
        return ToolExecutor(registry=registry, client=mock_client)

    @pytest.mark.asyncio
    async def test_execute_valid_tool(self, executor, mock_client):
        result = await executor.execute("get_property_count", {"city": "Madrid"})
        mock_client.get.assert_called_once_with(
            "/api/ai/tools/properties/count",
            params={"city": "Madrid"},
        )
        parsed = json.loads(result)
        assert parsed["count"] == 5

    @pytest.mark.asyncio
    async def test_execute_whitelist_rejects_unknown(self, executor):
        with pytest.raises(ToolExecutionError, match="Unknown tool"):
            await executor.execute("nonexistent_tool", {})

    @pytest.mark.asyncio
    async def test_execute_backend_error(self, mock_client, registry):
        mock_client.get = AsyncMock(side_effect=SpringBootError("connection refused"))
        executor = ToolExecutor(registry=registry, client=mock_client)
        with pytest.raises(ToolExecutionError, match="Tool execution failed"):
            await executor.execute("get_property_count", {})

    @pytest.mark.asyncio
    async def test_execute_empty_args(self, executor, mock_client):
        result = await executor.execute("get_booking_stats", {})
        mock_client.get.assert_called_once_with(
            "/api/ai/tools/bookings/stats",
            params=None,
        )

    @pytest.mark.asyncio
    async def test_execute_all_tools_mapped(self, executor, mock_client):
        """Every registered tool must have an endpoint mapping."""
        for name in executor.registry.tool_names:
            assert name in _TOOL_ENDPOINT_MAP, f"Tool '{name}' has no endpoint mapping"

    @pytest.mark.asyncio
    async def test_execute_logs_latency(self, executor, mock_client):
        # Should not raise
        await executor.execute("get_property_count", {})

# Endpoint Map Consistency
class TestEndpointMap:
    def test_all_endpoints_are_get(self):
        for name, (method, path) in _TOOL_ENDPOINT_MAP.items():
            assert method == "GET", f"Tool '{name}' uses {method}, expected GET"

    def test_all_endpoints_under_api_ai_tools(self):
        for name, (method, path) in _TOOL_ENDPOINT_MAP.items():
            assert path.startswith("/api/ai/tools/"), f"Tool '{name}' path '{path}' not under /api/ai/tools/"

    def test_five_tools_mapped(self):
        assert len(_TOOL_ENDPOINT_MAP) == 5
