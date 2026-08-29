"""Tests for prompt building and context formatting."""

from app.rag.prompt import SYSTEM_PROMPT, build_rag_prompt, format_context

class TestFormatContext:
    def test_empty_results_returns_message(self):
        text = format_context([])
        assert text == "No relevant context found."

    def test_formats_single_result(self):
        results = [
            {"content": "Rental terms", "source": "contract_template_v1", "score": 0.9}
        ]
        text = format_context(results)
        assert "contract_template_v1" in text
        assert "[1]" in text
        assert "Rental terms" in text

    def test_formats_multiple_results(self):
        results = [
            {"content": "A", "source": "doc1", "score": 0.9},
            {"content": "B", "source": "doc2", "score": 0.8},
        ]
        text = format_context(results)
        assert "[1]" in text
        assert "[2]" in text
        assert "doc1" in text
        assert "doc2" in text

class TestBuildRagPrompt:
    def test_starts_with_system_prompt(self):
        messages = build_rag_prompt("ctx", [], "q")
        assert messages[0]["role"] == "system"
        assert messages[0]["content"] == SYSTEM_PROMPT

    def test_appends_history(self):
        history = [
            {"role": "user", "content": "hi"},
            {"role": "assistant", "content": "hello"},
        ]
        messages = build_rag_prompt("ctx", history, "new q")
        # system + 2 history + 1 user
        assert len(messages) == 4
        assert messages[1] == {"role": "user", "content": "hi"}
        assert messages[2] == {"role": "assistant", "content": "hello"}

    def test_history_limited_to_10(self):
        history = [{"role": "user", "content": f"m{i}"} for i in range(20)]
        messages = build_rag_prompt("ctx", history, "q")
        # system + 10 history + 1 user
        assert len(messages) == 12

    def test_last_user_message_contains_context_and_question(self):
        messages = build_rag_prompt("[1] ctx", [], "the question")
        last = messages[-1]
        assert last["role"] == "user"
        assert "[1] ctx" in last["content"]
        assert "the question" in last["content"]