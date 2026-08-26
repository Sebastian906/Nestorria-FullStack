"""Tests for VisualSimilarityEngine (pgvector backend)."""

import importlib
from unittest.mock import MagicMock, patch

import numpy as np
import pytest
from PIL import Image

from app.dl.image.embeddings import EMBEDDING_DIMENSION

# Ensure the module is importable before patching
import app.dl.image.similarity as sim_mod
from app.dl.image.similarity import VisualSimilarityEngine


def _fake_embedding(seed: int = 0) -> np.ndarray:
    """Return a deterministic normalized embedding."""
    rng = np.random.RandomState(seed)
    vec = rng.rand(EMBEDDING_DIMENSION).astype(np.float32)
    vec /= np.linalg.norm(vec)
    return vec


def _make_engine():
    """Create engine with mocked dependencies."""
    with patch.object(sim_mod, "get_settings") as mock_settings, \
         patch.object(sim_mod, "psycopg2") as mock_psycopg2, \
         patch.object(sim_mod, "ImagePreprocessor") as mock_pp_cls, \
         patch.object(sim_mod, "ImageEmbeddingExtractor") as mock_ext_cls:

        mock_settings.return_value.database_url = "postgresql://test:test@localhost/test"

        mock_pp = MagicMock()
        mock_pp_cls.return_value = mock_pp

        mock_ext = MagicMock()
        mock_ext_cls.return_value = mock_ext

        mock_conn = MagicMock()
        mock_conn.closed = False
        mock_psycopg2.connect.return_value = mock_conn

        engine = VisualSimilarityEngine()
        engine._conn = mock_conn

        return engine, mock_conn, mock_ext


class TestVisualSimilarityEngine:
    """Integration-style tests for the pgvector engine."""

    def test_index_property_stores_embeddings(self):
        """index_property should execute INSERT for each image URL."""
        engine, mock_conn, mock_ext = _make_engine()

        mock_cursor = MagicMock()
        mock_conn.cursor.return_value.__enter__ = lambda s: mock_cursor
        mock_conn.cursor.return_value.__exit__ = MagicMock(return_value=False)

        engine._extract_from_url = MagicMock(return_value=_fake_embedding(0))

        result = engine.index_property("prop-123", ["http://img1.jpg", "http://img2.jpg"])

        assert result == 2
        # DELETE + 2 INSERTs
        assert mock_cursor.execute.call_count >= 3

    def test_index_property_rollback_on_error(self):
        """index_property should rollback when DELETE fails."""
        engine, mock_conn, mock_ext = _make_engine()
        engine._extract_from_url = MagicMock(return_value=_fake_embedding(0))

        mock_cursor = MagicMock()
        # DELETE fails — triggers outer except → rollback
        mock_cursor.execute.side_effect = Exception("DB error")
        mock_conn.cursor.return_value.__enter__ = lambda s: mock_cursor
        mock_conn.cursor.return_value.__exit__ = MagicMock(return_value=False)

        with pytest.raises(Exception):
            engine.index_property("prop-123", ["http://img1.jpg"])

        mock_conn.rollback.assert_called_once()

    def test_find_similar_returns_ordered_results(self):
        """find_similar should return results ordered by similarity descending."""
        engine, mock_conn, mock_ext = _make_engine()

        mock_cursor = MagicMock()
        mock_cursor.fetchone.return_value = (_fake_embedding(0).tolist(),)
        mock_cursor.fetchall.return_value = [
            ("prop-456", 0.95),
            ("prop-789", 0.82),
            ("prop-012", 0.71),
        ]
        mock_conn.cursor.return_value.__enter__ = lambda s: mock_cursor
        mock_conn.cursor.return_value.__exit__ = MagicMock(return_value=False)

        results = engine.find_similar("prop-123", limit=5)

        assert len(results) == 3
        assert results[0]["similarity"] >= results[1]["similarity"]
        assert results[1]["similarity"] >= results[2]["similarity"]

    def test_find_similar_excludes_query_property(self):
        """find_similar should not return the query property itself."""
        engine, mock_conn, mock_ext = _make_engine()

        mock_cursor = MagicMock()
        mock_cursor.fetchone.return_value = (_fake_embedding(0).tolist(),)
        mock_cursor.fetchall.return_value = [
            ("prop-456", 0.95),
        ]
        mock_conn.cursor.return_value.__enter__ = lambda s: mock_cursor
        mock_conn.cursor.return_value.__exit__ = MagicMock(return_value=False)

        results = engine.find_similar("prop-123", limit=5)
        property_ids = [r["property_id"] for r in results]

        assert "prop-123" not in property_ids

    def test_find_similar_empty_result(self):
        """find_similar should return empty list when no embeddings exist."""
        engine, mock_conn, mock_ext = _make_engine()

        mock_cursor = MagicMock()
        mock_cursor.fetchone.return_value = None
        mock_conn.cursor.return_value.__enter__ = lambda s: mock_cursor
        mock_conn.cursor.return_value.__exit__ = MagicMock(return_value=False)

        results = engine.find_similar("prop-123", limit=5)
        assert results == []

    def test_search_by_image_returns_results(self):
        """search_by_image should return property similarity results."""
        engine, mock_conn, mock_ext = _make_engine()
        mock_ext.extract.return_value = _fake_embedding(0)

        mock_cursor = MagicMock()
        mock_cursor.fetchall.return_value = [
            ("prop-456", 0.88),
            ("prop-789", 0.75),
        ]
        mock_conn.cursor.return_value.__enter__ = lambda s: mock_cursor
        mock_conn.cursor.return_value.__exit__ = MagicMock(return_value=False)

        image = Image.new("RGB", (224, 224), color=(128, 128, 128))
        results = engine.search_by_image(image, limit=5)

        assert len(results) == 2
        assert all("property_id" in r and "similarity" in r for r in results)

    def test_similarity_range(self):
        """Similarity scores should be in [0, 1] range."""
        engine, mock_conn, mock_ext = _make_engine()

        mock_cursor = MagicMock()
        mock_cursor.fetchone.return_value = (_fake_embedding(0).tolist(),)
        mock_cursor.fetchall.return_value = [
            ("prop-456", 0.95),
            ("prop-789", 0.0),
        ]
        mock_conn.cursor.return_value.__enter__ = lambda s: mock_cursor
        mock_conn.cursor.return_value.__exit__ = MagicMock(return_value=False)

        results = engine.find_similar("prop-123", limit=5)

        for r in results:
            assert 0.0 <= r["similarity"] <= 1.0

    def test_get_index_size(self):
        """get_index_size should return count from database."""
        engine, mock_conn, mock_ext = _make_engine()

        mock_cursor = MagicMock()
        mock_cursor.fetchone.return_value = (42,)
        mock_conn.cursor.return_value.__enter__ = lambda s: mock_cursor
        mock_conn.cursor.return_value.__exit__ = MagicMock(return_value=False)

        count = engine.get_index_size()
        assert count == 42

    def test_close_connection(self):
        """close() should close the database connection."""
        engine, mock_conn, mock_ext = _make_engine()
        mock_conn.closed = False

        engine.close()
        mock_conn.close.assert_called_once()

    def test_connection_established_on_demand(self):
        """Engine should create connection on first use."""
        engine, mock_conn, mock_ext = _make_engine()
        # Connection was already created in _make_engine via _get_connection
        assert engine._conn is mock_conn
