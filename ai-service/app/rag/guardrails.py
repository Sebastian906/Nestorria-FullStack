"""Input/output guardrails for chat safety."""

import re

import structlog

logger = structlog.get_logger("ai-service.rag.guardrails")

# Patterns for detection
_PROMPT_INJECTION_PATTERNS = [
    r"ignore (previous|all|above) instructions",
    r"you are now",
    r"system prompt",
    r"reveal (your|the) (instructions|prompt|rules)",
    r"act as",
    r"pretend (you|to be)",
    r"jailbreak",
    r"DAN mode",
    r"developer mode",
]

_PII_PATTERNS = [
    (r"\b[\w.+-]+@[\w-]+\.[\w.]+\b", "EMAIL"),
    (r"\b(?:\+?\d{1,3}[-.\s]?)?\(?\d{2,4}\)?[-.\s]?\d{3,4}[-.\s]?\d{3,4}\b", "PHONE"),
    (r"\b\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}\b", "CARD_NUMBER"),
    (r"\b\d{3}-\d{2}-\d{4}\b", "SSN"),
]

_HARMFUL_CODE_PATTERNS = [
    r"import os; os\.system",
    r"subprocess\.call",
    r"eval\(",
    r"exec\(",
    r"__import__",
    r"rm -rf",
    r"DROP TABLE",
    r"DELETE FROM",
]

class Guardrails:
    """Deterministic, testable guardrails for input/output safety."""

    def check_input(self, query: str) -> tuple[bool, str | None]:
        """Check user input for safety issues.

        Returns:
            (is_safe, reason) — reason is None if safe.
        """
        query_lower = query.lower()

        # Prompt injection
        for pattern in _PROMPT_INJECTION_PATTERNS:
            if re.search(pattern, query_lower):
                reason = f"Prompt injection detected: {pattern}"
                logger.warning("guardrail_input_blocked", reason=reason)
                return False, reason

        # PII in input
        for pattern, pii_type in _PII_PATTERNS:
            if re.search(pattern, query):
                reason = f"PII detected in input: {pii_type}"
                logger.warning("guardrail_input_blocked", reason=reason)
                return False, reason

        # Harmful code
        for pattern in _HARMFUL_CODE_PATTERNS:
            if re.search(pattern, query, re.IGNORECASE):
                reason = "Harmful code pattern detected"
                logger.warning("guardrail_input_blocked", reason=reason)
                return False, reason

        return True, None

    def check_output(self, response: str) -> str:
        """Filter PII and sensitive data from LLM output.

        Returns:
            Filtered response string.
        """
        filtered = response

        # Mask emails
        for pattern, pii_type in _PII_PATTERNS:
            filtered = re.sub(pattern, f"[REDACTED {pii_type}]", filtered)

        return filtered