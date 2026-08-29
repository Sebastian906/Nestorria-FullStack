"""Prompt templates for RAG generation."""

SYSTEM_PROMPT = """You are Nestorria's AI assistant for a real estate platform.

Rules:
- Answer BASED ONLY on the provided context, tool results, and conversation history.
- Cite sources using [Source: document_name] after relevant statements.
- If the information is not in the context or tool results, say "I don't have that information."
- Never invent data not present in the context or tool results.
- Never expose emails, phone numbers, payment data, or private user information.
- Never generate executable code or harmful content.
- Be concise, professional, and helpful.

Authorized context sources are provided below. Only trust information from these sources."""

TOOL_CALLING_INSTRUCTIONS = """

## Tool Calling

You have access to tools that can query Nestorria's live database.
When a question requires real-time data (property counts, prices, reviews, booking stats), use the appropriate tool.

Available tools:
{tool_descriptions}

To call a tool, output EXACTLY this format (and nothing else before it):
```tool_call
{{"name": "tool_name", "args": {{"param1": "value1"}}}}
```

After receiving tool results, incorporate them into your answer naturally.
If a tool returns an error, inform the user that the data is temporarily unavailable.
Only call one tool at a time. Wait for the result before calling another tool.
"""


def build_rag_prompt(
    context: str,
    history: list[dict],
    question: str,
    tool_descriptions: str | None = None,
) -> list[dict]:
    """Build messages array for LLM.

    Args:
        context: Formatted retrieval results.
        history: Conversation history (list of {role, content}).
        question: User's current question.
        tool_descriptions: Optional tool descriptions for prompt-based tool calling.

    Returns:
        Messages list for chat completion API.
    """
    system_content = SYSTEM_PROMPT
    if tool_descriptions:
        system_content += TOOL_CALLING_INSTRUCTIONS.format(
            tool_descriptions=tool_descriptions
        )

    messages = [{"role": "system", "content": system_content}]

    # Add conversation history
    for msg in history[-10:]:  # last 10 messages
        messages.append({"role": msg["role"], "content": msg["content"]})

    # Add user message with context
    user_content = f"""Context from Nestorria documents:
---
{context}
---

Question: {question}"""

    messages.append({"role": "user", "content": user_content})
    return messages

def format_context(results: list[dict]) -> str:
    """Format retrieval results into context string.

    Args:
        results: List of dicts with content, score, source, metadata.

    Returns:
        Formatted context string.
    """
    if not results:
        return "No relevant context found."

    parts = []
    for i, r in enumerate(results, 1):
        source = r.get("source", "unknown")
        parts.append(f"[{i}] (Source: {source})\n{r['content']}")

    return "\n\n".join(parts)