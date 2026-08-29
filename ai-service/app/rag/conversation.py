"""In-memory conversation manager with TTL."""

import time
from dataclasses import dataclass, field

import structlog

from app.config import get_settings

logger = structlog.get_logger("ai-service.rag.conversation")

@dataclass
class Conversation:
    """Single conversation with messages and metadata."""
    id: str
    user_id: str
    messages: list[dict] = field(default_factory=list)
    created_at: float = field(default_factory=time.time)
    last_activity: float = field(default_factory=time.time)

class ConversationManager:
    """In-memory conversation store with TTL eviction.

    Conversations are isolated by user_id. A user cannot access
    another user's conversation.
    """

    def __init__(self):
        settings = get_settings()
        self._conversations: dict[str, Conversation] = {}
        self._ttl = settings.conversation_ttl
        self._max_messages = settings.conversation_max_messages

    def _evict_expired(self) -> None:
        """Remove conversations older than TTL."""
        now = time.time()
        expired = [
            cid for cid, conv in self._conversations.items()
            if now - conv.last_activity > self._ttl
        ]
        for cid in expired:
            del self._conversations[cid]
            logger.info("conversation_evicted", conversation_id=cid)

    def get_or_create(self, conversation_id: str | None, user_id: str) -> Conversation:
        """Get existing conversation or create new one.

        Validates that the conversation belongs to the user.
        """
        self._evict_expired()

        if conversation_id and conversation_id in self._conversations:
            conv = self._conversations[conversation_id]
            if conv.user_id != user_id:
                logger.warning(
                    "conversation_access_denied",
                    conversation_id=conversation_id,
                    requested_by=user_id,
                    owner=conv.user_id,
                )
                raise ValueError("Conversation not found")
            conv.last_activity = time.time()
            return conv

        # Create new conversation
        import uuid
        new_id = conversation_id or f"conv_{uuid.uuid4().hex[:12]}"
        conv = Conversation(id=new_id, user_id=user_id)
        self._conversations[new_id] = conv
        logger.info("conversation_created", conversation_id=new_id, user_id=user_id)
        return conv

    def add_message(self, conversation_id: str, role: str, content: str) -> None:
        """Add a message to conversation history.

        Trims to max_messages (keeps last N).
        """
        conv = self._conversations.get(conversation_id)
        if not conv:
            return

        conv.messages.append({"role": role, "content": content})
        conv.last_activity = time.time()

        # Trim to max
        if len(conv.messages) > self._max_messages:
            conv.messages = conv.messages[-self._max_messages:]

    def get_history(self, conversation_id: str) -> list[dict]:
        """Get conversation history."""
        conv = self._conversations.get(conversation_id)
        return conv.messages if conv else []

    def get_conversation(self, conversation_id: str, user_id: str) -> Conversation | None:
        """Get conversation if it belongs to user."""
        conv = self._conversations.get(conversation_id)
        if conv and conv.user_id == user_id:
            return conv
        return None