"""Tool executor: validates + calls Spring Boot for authorized tools."""

from __future__ import annotations

import json
import re
import time

import structlog

from app.rag.tools.base import ToolRegistry
from app.rag.tools.spring_client import SpringBootClient, SpringBootError

logger = structlog.get_logger("ai-service.rag.tools.executor")

# Explicit mapping: tool_name → (HTTP method, path)
# This is the ONLY way tools can call Spring Boot. No arbitrary endpoints.
_TOOL_ENDPOINT_MAP: dict[str, tuple[str, str]] = {
    "get_property_count": ("GET", "/api/ai/tools/properties/count"),
    "get_average_price": ("GET", "/api/ai/tools/properties/avg-price"),
    "search_properties": ("GET", "/api/ai/tools/properties/search"),
    "get_booking_stats": ("GET", "/api/ai/tools/bookings/stats"),
    "get_review_average": ("GET", "/api/ai/tools/reviews/average"),
}

# Pattern to extract tool calls from LLM text output.
# Matches fenced code blocks: ```tool_call\n{...}\n```
_TOOL_CALL_PATTERN = re.compile(
    r"```tool_call\s*\n(.*?)\n```",
    re.DOTALL,
)


def _find_inline_tool_call(text: str) -> dict | None:
    """Find an inline tool call JSON in text.

    Looks for "tool_call" key in a JSON object within the text.
    Handles nested braces by counting brace depth.
    """
    idx = text.find('"tool_call"')
    if idx == -1:
        return None

    # Walk backward to find the opening brace
    start = idx
    while start > 0 and text[start - 1] != '{':
        start -= 1
    if start == 0:
        return None
    start -= 1  # Point to the '{' itself

    # Walk forward counting brace depth to find matching closing brace
    depth = 0
    end = start
    while end < len(text):
        if text[end] == '{':
            depth += 1
        elif text[end] == '}':
            depth -= 1
            if depth == 0:
                end += 1
                break
        end += 1

    json_str = text[start:end]
    try:
        parsed = json.loads(json_str)
        if isinstance(parsed, dict) and "tool_call" in parsed:
            tc = parsed["tool_call"]
            if isinstance(tc, dict) and "name" in tc:
                return {
                    "name": tc["name"],
                    "args": tc.get("args", {}),
                }
    except json.JSONDecodeError:
        pass
    return None


def parse_tool_calls(text: str) -> list[dict]:
    """Parse tool calls from LLM text output.

    The LLM is instructed to output tool calls in a structured format:
        ```tool_call
        {"name": "tool_name", "args": {"param": "value"}}
        ```

    Returns:
        List of parsed tool calls: [{"name": str, "args": dict}]
    """
    calls = []

    # Try fenced code block format first (primary)
    for match in _TOOL_CALL_PATTERN.finditer(text):
        try:
            parsed = json.loads(match.group(1).strip())
            if isinstance(parsed, dict) and "name" in parsed:
                calls.append({
                    "name": parsed["name"],
                    "args": parsed.get("args", {}),
                })
        except json.JSONDecodeError:
            continue

    # Fallback: inline JSON format with "tool_call" key
    if not calls:
        inline = _find_inline_tool_call(text)
        if inline:
            calls.append(inline)

    return calls


class ToolExecutor:
    """Execute tool calls by mapping tool_name → authorized Spring Boot endpoint.

    Security:
    - Only whitelisted tool names are allowed (ToolRegistry)
    - Only endpoints in _TOOL_ENDPOINT_MAP are callable
    - Parameters are validated before sending
    """

    def __init__(self, registry: ToolRegistry, client: SpringBootClient):
        self.registry = registry
        self.client = client

    async def execute(self, tool_name: str, args: dict) -> str:
        """Execute a tool call and return JSON-stringified result.

        Args:
            tool_name: Name of the tool to execute.
            args: Arguments from the LLM tool call.

        Returns:
            JSON string with the tool result.

        Raises:
            ToolExecutionError: If tool is unknown, endpoint missing, or execution fails.
        """
        t0 = time.perf_counter()

        # 1. Validate tool is in whitelist
        tool = self.registry.get(tool_name)
        if not tool:
            logger.warning("tool_not_whitelisted", tool=tool_name)
            raise ToolExecutionError(f"Unknown tool: {tool_name}")

        # 2. Validate args via tool's own validation
        validated_args = tool.validate_args(args)

        # 3. Check endpoint mapping exists
        if tool_name not in _TOOL_ENDPOINT_MAP:
            logger.error("tool_no_endpoint", tool=tool_name)
            raise ToolExecutionError(f"No endpoint mapped for tool: {tool_name}")

        method, path = _TOOL_ENDPOINT_MAP[tool_name]

        # 4. Execute against Spring Boot
        try:
            result = await self.client.get(path, params=validated_args or None)
            latency_ms = (time.perf_counter() - t0) * 1000

            logger.info(
                "tool_executed",
                tool=tool_name,
                args=validated_args,
                latency_ms=round(latency_ms, 1),
            )
            return json.dumps(result)

        except SpringBootError as e:
            latency_ms = (time.perf_counter() - t0) * 1000
            logger.error(
                "tool_execution_failed",
                tool=tool_name,
                args=validated_args,
                latency_ms=round(latency_ms, 1),
                error=str(e),
            )
            raise ToolExecutionError(f"Tool execution failed: {e}") from e

    async def close(self):
        await self.client.close()


class ToolExecutionError(Exception):
    """Error during tool execution."""
    pass