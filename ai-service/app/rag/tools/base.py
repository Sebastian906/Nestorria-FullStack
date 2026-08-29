"""Base tool class and tool registry."""

from __future__ import annotations

import json

import structlog
from typing import Any

logger = structlog.get_logger("ai-service.rag.tools")


class BaseTool:
    """Base class for all tools."""

    name: str
    description: str
    parameters: dict[str, Any]

    def __init_subclass__(cls, **kwargs):
        super().__init_subclass__(**kwargs)
        # Validate required attributes
        if not hasattr(cls, 'name') or not cls.name:
            raise TypeError(f"Tool {cls.__name__} must define 'name'")
        if not hasattr(cls, 'description') or not cls.description:
            raise TypeError(f"Tool {cls.__name__} must define 'description'")
        if not hasattr(cls, 'parameters') or not cls.parameters:
            raise TypeError(f"Tool {cls.__name__} must define 'parameters'")

    def to_prompt_description(self) -> str:
        """Format tool as text for system prompt injection.

        Used when the LLM model does NOT support native function calling.
        The LLM reads this description and outputs structured JSON to invoke tools.
        """
        props = self.parameters.get("properties", {})
        required = self.parameters.get("required", [])
        param_lines = []
        for pname, pdef in props.items():
            req = " (required)" if pname in required else ""
            enum_hint = f" enum={pdef['enum']}" if "enum" in pdef else ""
            param_lines.append(f"    - {pname}: {pdef.get('description', '')}{enum_hint}{req}")
        params_str = "\n".join(param_lines) if param_lines else "    (no parameters)"
        return f"- {self.name}: {self.description}\n  Parameters:\n{params_str}"

    def to_openai_tool(self) -> dict:
        """Convert to OpenAI function calling format (for models that support it)."""
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": self.parameters,
            },
        }

    def validate_args(self, args: dict) -> dict:
        """Validate and sanitize arguments. Override for custom validation."""
        return args


class ToolRegistry:
    """Registry of available tools. Whitelist-based: only registered tools can execute."""

    def __init__(self):
        self._tools: dict[str, BaseTool] = {}

    def register(self, tool: BaseTool) -> None:
        """Register a tool. Raises if already registered."""
        if tool.name in self._tools:
            raise ValueError(f"Tool '{tool.name}' already registered")
        self._tools[tool.name] = tool
        logger.info("tool_registered", tool=tool.name)

    def get(self, name: str) -> BaseTool | None:
        """Get a tool by name. Returns None if not found."""
        return self._tools.get(name)

    def get_all(self) -> list[BaseTool]:
        """Get all registered tools."""
        return list(self._tools.values())

    def to_openai_tools(self) -> list[dict]:
        """Export all tools in OpenAI function calling format."""
        return [tool.to_openai_tool() for tool in self._tools.values()]

    def to_prompt_descriptions(self) -> str:
        """Export all tools as text for system prompt injection.

        Used for models that don't support native function calling.
        """
        if not self._tools:
            return ""
        descriptions = [tool.to_prompt_description() for tool in self._tools.values()]
        return "\n".join(descriptions)

    def is_allowed(self, name: str) -> bool:
        """Check if a tool name is in the whitelist."""
        return name in self._tools

    @property
    def tool_names(self) -> list[str]:
        return list(self._tools.keys())