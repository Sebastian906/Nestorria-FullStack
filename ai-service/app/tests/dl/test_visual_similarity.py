"""Tests for VisualSimilarityEngine (pgvector backend)."""

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


@pytest.fixture()
def engine_deps():
    """Patch all external deps for the full test scope."""
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

        yield {
            "settings": mock_settings,
            "psycopg2": mock_psycopg2,
            "preprocessor": mock_pp,
            "extractor": mock_ext,
            "conn": mock_conn,
        }


@pytest.fixture()
def engine(engine_deps):
    """Create VisualSimilarityEngine with mocked dependencies."""
    eng = VisualSimilarityEngine()
    eng._conn = engine_deps["conn"]
    return eng


class TestVisualSimilarityEngine:
    """Integration-style tests for the pgvector engine."""

    def test_index_property_stores_embeddings(self, engine, engine_deps):
        """index_property should execute INSERT for each image URL."""
        mock_cursor = MagicMock()
        engine_deps["conn"].cursor.return_value.__enter__ = lambda s: mock_cursor
        engine_deps["conn"].cursor.return_value.__exit__ = MagicMock(return_value=False)

        engine._extract_from_url = MagicMock(return_value=_fake_embedding(0))

        result = engine.index_property("prop-123", ["http://img1.jpg", "http://img2.jpg"])

        assert result == 2
        # DELETE + 2 INSERTs
        assert mock_cursor.execute.call_count >= 3

    def test_index_property_rollback_on_error(self, engine, engine_deps):
        """index_property should rollback when DELETE fails."""
        engine._extract_from_url = MagicMock(return_value=_fake_embedding(0))

        mock_cursor = MagicMock()
        mock_cursor.execute.side_effect = Exception("DB error")
        engine_deps["conn"].cursor.return_value.__enter__ = lambda s: mock_cursor
        engine_deps["conn"].cursor.return_value.__exit__ = MagicMock(return_value=False)

        with pytest.raises(Exception):
            engine.index_property("prop-123", ["http://img1.jpg"])

        engine_deps["conn"].rollback.assert_called_once()

    def test_index_property_preserves_existing_when_no_extractions(self, engine, engine_deps):
        """index_property should not delete when no embeddings extracted."""
        engine._extract_from_url = MagicMock(side_effect=Exception("download failed"))

        result = engine.index_property("prop-123", ["http://bad.jpg"])

        assert result == 0
        # Should not have called execute at all (no DELETE)
        engine_deps["conn"].cursor.assert_not_called()

    def test_find_similar_returns_ordered_results(self, engine, engine_deps):
        """find_similar should return results ordered by similarity descending."""
        mock_cursor = MagicMock()
        mock_cursor.fetchone.return_value = (_fake_embedding(0).tolist(),)
        mock_cursor.fetchall.return_value = [
            ("prop-456", 0.95),
            ("prop-789", 0.82),
            ("prop-012", 0.71),
        ]
        engine_deps["conn"].cursor.return_value.__enter__ = lambda s: mock_cursor
        engine_deps["conn"].cursor.return_value.__exit__ = MagicMock(return_value=False)

        results = engine.find_similar("prop-123", limit=5)

        assert len(results) == 3
        assert results[0]["similarity"] >= results[1]["similarity"]
        assert results[1]["similarity"] >= results[2]["similarity"]

    def test_find_similar_excludes_query_property(self, engine, engine_deps):
        """find_similar should not return the query property itself."""
        mock_cursor = MagicMock()
        mock_cursor.fetchone.return_value = (_fake_embedding(0).tolist(),)
        mock_cursor.fetchall.return_value = [
            ("prop-456", 0.95),
        ]
        engine_deps["conn"].cursor.return_value.__enter__ = lambda s: mock_cursor
        engine_deps["conn"].cursor.return_value.__exit__ = MagicMock(return_value=False)

        results = engine.find_similar("prop-123", limit=5)
        property_ids = [r["property_id"] for r in results]

        assert "prop-123" not in property_ids

    def test_find_similar_empty_result(self, engine, engine_deps):
        """find_similar should return empty list when no embeddings exist."""
        mock_cursor = MagicMock()
        mock_cursor.fetchone.return_value = None
        engine_deps["conn"].cursor.return_value.__enter__ = lambda s: mock_cursor
        engine_deps["conn"].cursor.return_value.__exit__ = MagicMock(return_value=False)

        results = engine.find_similar("prop-123", limit=5)
        assert results == []

    def test_search_by_image_returns_results(self, engine, engine_deps):
        """search_by_image should return property similarity results."""
        engine_deps["extractor"].extract.return_value = _fake_embedding(0)

        mock_cursor = MagicMock()
        mock_cursor.fetchall.return_value = [
            ("prop-456", 0.88),
            ("prop-789", 0.75),
        ]
        engine_deps["conn"].cursor.return_value.__enter__ = lambda s: mock_cursor
        engine_deps["conn"].cursor.return_value.__exit__ = MagicMock(return_value=False)

        image = Image.new("RGB", (224, 224), color=(128, 128, 128))
        results = engine.search_by_image(image, limit=5)

        assert len(results) == 2
        assert all("property_id" in r and "similarity" in r for r in results)

    def test_similarity_range(self, engine, engine_deps):
        """Similarity scores should be in [0, 1] range."""
        mock_cursor = MagicMock()
        mock_cursor.fetchone.return_value = (_fake_embedding(0).tolist(),)
        mock_cursor.fetchall.return_value = [
            ("prop-456", 0.95),
            ("prop-789", 0.0),
        ]
        engine_deps["conn"].cursor.return_value.__enter__ = lambda s: mock_cursor
        engine_deps["conn"].cursor.return_value.__exit__ = MagicMock(return_value=False)

        results = engine.find_similar("prop-123", limit=5)

        for r in results:
            assert 0.0 <= r["similarity"] <= 1.0

    def test_get_index_size(self, engine, engine_deps):
        """get_index_size should return count from database."""
        mock_cursor = MagicMock()
        mock_cursor.fetchone.return_value = (42,)
        engine_deps["conn"].cursor.return_value.__enter__ = lambda s: mock_cursor
        engine_deps["conn"].cursor.return_value.__exit__ = MagicMock(return_value=False)

        count = engine.get_index_size()
        assert count == 42

    def test_close_connection(self, engine, engine_deps):
        """close() should close the database connection."""
        engine.close()
        engine_deps["conn"].close.assert_called_once()

    def test_connection_established_on_demand(self, engine_deps):
        """Engine should create connection via _get_connection."""
        eng = VisualSimilarityEngine()
        conn = eng._get_connection()
        assert conn is engine_deps["conn"]
        engine_deps["psycopg2"].connect.assert_called_once()
