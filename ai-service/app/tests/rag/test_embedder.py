"""Tests for TextEmbedder (mocked model)."""

from unittest.mock import MagicMock, patch

import numpy as np
import pytest

from app.rag.embedder import TextEmbedder

@pytest.fixture
def mock_model():
    """Mock SentenceTransformer model.

    encode() returns N normalized 384-dim vectors matching input count.
    """
    with patch("app.rag.embedder._get_model") as mock_get:
        model = MagicMock()

        def _fake_encode(texts, **kwargs):
            n = len(texts)
            vecs = np.random.rand(n, 384).astype(np.float32)
            return vecs / np.linalg.norm(vecs, axis=1, keepdims=True)

        model.encode.side_effect = _fake_encode
        mock_get.return_value = model
        yield model

class TestTextEmbedder:
    """Tests for embedding generation."""

    def test_embed_returns_correct_dimension(self, mock_model):
        embedder = TextEmbedder(model_name="test-model", embedding_dim=384)
        result = embedder.embed(["hello", "world"])
        assert result.shape == (2, 384)

    def test_empty_list_raises(self):
        embedder = TextEmbedder()
        with pytest.raises(ValueError, match="empty"):
            embedder.embed([])

    def test_single_text(self, mock_model):
        embedder = TextEmbedder(model_name="test-model", embedding_dim=384)
        result = embedder.embed(["test"])
        assert result.shape == (1, 384)

    def test_normalized_embeddings(self, mock_model):
        embedder = TextEmbedder(model_name="test-model", embedding_dim=384)
        result = embedder.embed(["hello"])
        norm = np.linalg.norm(result[0])
        assert abs(norm - 1.0) < 1e-5

    def test_dimension_mismatch_raises(self, mock_model):
        # Model returns 100-dim but we expect 384 — override side_effect
        mock_model.encode.side_effect = lambda texts, **kw: np.random.rand(len(texts), 100).astype(np.float32)
        embedder = TextEmbedder(model_name="test-model", embedding_dim=384)
        with pytest.raises(ValueError, match="expected 384"):
            embedder.embed(["test"])