"""Prompt templates for RAG generation."""

SYSTEM_PROMPT = """You are Nestorria's AI assistant for a real estate platform.

Rules:
- Answer BASED ONLY on the provided context and conversation history.
- Cite sources using [Source: document_name] after relevant statements.
- If the information is not in the context, say "I don't have that information."
- Never invent data not present in the context.
- Never expose emails, phone numbers, payment data, or private user information.
- Never generate executable code or harmful content.
- Be concise, professional, and helpful.

Authorized context sources are provided below. Only trust information from these sources."""

def build_rag_prompt(
    context: str,
    history: list[dict],
    question: str,
) -> list[dict]:
    """Build messages array for LLM.

    Args:
        context: Formatted retrieval results.
        history: Conversation history (list of {role, content}).
        question: User's current question.

    Returns:
        Messages list for chat completion API.
    """
    messages = [{"role": "system", "content": SYSTEM_PROMPT}]

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