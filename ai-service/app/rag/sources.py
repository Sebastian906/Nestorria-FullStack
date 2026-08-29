"""Source extraction and validation."""

import re

import structlog

logger = structlog.get_logger("ai-service.rag.sources")

# Pattern for [Source: name] citations in response
_CITATION_PATTERN = r"\[Source:\s*([^\]]+)\]"

def extract_sources(response: str, valid_sources: list[str]) -> list[str]:
    """Extract cited sources from response and validate against retrieved.

    Args:
        response: LLM response text.
        valid_sources: Sources actually retrieved from vector store.

    Returns:
        List of validated source names.
    """
    matches = re.findall(_CITATION_PATTERN, response)
    cited = [m.strip() for m in matches]

    # Only keep sources that were actually retrieved
    validated = [s for s in cited if s in valid_sources]

    if len(validated) != len(cited):
        removed = set(cited) - set(validated)
        logger.warning("sources_validation_removed", removed=list(removed))

    return validated

def strip_citations(response: str) -> str:
    """Remove citation markers from response text."""
    return re.sub(_CITATION_PATTERN, "", response).strip()

def sanitize_citations(response: str, valid_sources: list[str]) -> str:
    """Remove citation markers that reference unverified sources.

    Keeps markers whose source name is in valid_sources (actually retrieved),
    and strips the rest so unverified citations are not persisted or returned.
    """
    def _repl(match: re.Match) -> str:
        name = match.group(1).strip()
        return match.group(0) if name in valid_sources else ""
    return re.sub(_CITATION_PATTERN, _repl, response)