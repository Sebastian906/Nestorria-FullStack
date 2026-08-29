"""Tests for conversation manager."""

import pytest

from app.rag.conversation import ConversationManager

class TestConversationManager:
    def test_create_conversation(self):
        cm = ConversationManager()
        conv = cm.get_or_create(None, "user1")
        assert conv.user_id == "user1"
        assert conv.id.startswith("conv_")

    def test_add_message_limits_history(self):
        cm = ConversationManager()
        conv = cm.get_or_create(None, "user1")
        for i in range(15):
            cm.add_message(conv.id, "user" if i % 2 == 0 else "assistant", f"msg {i}")
        history = cm.get_history(conv.id)
        assert len(history) == 10  # max_messages

    def test_user_cannot_access_other_conversation(self):
        cm = ConversationManager()
        conv = cm.get_or_create(None, "user1")
        result = cm.get_conversation(conv.id, "user2")
        assert result is None