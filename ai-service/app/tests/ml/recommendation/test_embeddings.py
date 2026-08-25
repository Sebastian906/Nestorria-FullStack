"""Tests for ContentEmbedder."""

import numpy as np
from app.ml.recommendation.embeddings import ContentEmbedder

class TestContentEmbedder:
    def test_fit_transform_basic(self):
        # Longer texts reduce degenerate SVD projections
        texts = [
            "Beautiful apartment near the beach with amazing ocean view and parking space",
            "Modern house with private pool garden terrace and gym facilities inside",
            "Cozy studio apartment in the city center close to restaurants and shops",
            "Luxury villa with large garden private pool elevator and air conditioning",
        ]
        embedder = ContentEmbedder(n_components=2)
        embeddings = embedder.fit_transform(texts)

        assert embeddings.shape == (4, 2)
        assert embedder.is_fitted
        # L2 normalized: non-zero rows should have unit norm
        norms = np.linalg.norm(embeddings, axis=1)
        for norm in norms:
            if norm > 1e-10:
                assert abs(norm - 1.0) < 1e-6

    def test_fit_transform_empty(self):
        embedder = ContentEmbedder(n_components=2)
        embeddings = embedder.fit_transform([])
        assert embeddings.shape == (0, 2)

    def test_fit_transform_small_dataset(self):
        # Only 2 documents, n_components=8 should be reduced
        texts = ["hello world", "foo bar"]
        embedder = ContentEmbedder(n_components=8)
        embeddings = embedder.fit_transform(texts)
        assert embeddings.shape[0] == 2
        assert embeddings.shape[1] <= 2  # reduced

    def test_transform_after_fit(self):
        texts = [
            "Beautiful apartment near beach",
            "Modern house with pool",
            "Cozy studio in city center",
        ]
        embedder = ContentEmbedder(n_components=2)
        embedder.fit_transform(texts)

        new_texts = ["Luxury villa with garden"]
        new_embeddings = embedder.transform(new_texts)
        assert new_embeddings.shape == (1, 2)

    def test_transform_before_fit_raises(self):
        embedder = ContentEmbedder(n_components=2)
        try:
            embedder.transform(["test"])
            assert False, "Should have raised RuntimeError"
        except RuntimeError:
            pass

    def test_compute_similarity(self):
        # Longer texts ensure non-degenerate embeddings
        texts = [
            "Beautiful apartment near the beach with amazing ocean view and parking space",
            "Modern house with private pool garden terrace and gym facilities inside",
            "Cozy studio apartment in the city center close to restaurants and shops",
            "Luxury villa with large garden private pool elevator and air conditioning",
        ]
        embedder = ContentEmbedder(n_components=3)
        embeddings = embedder.fit_transform(texts)

        query = embeddings[0]
        similarities = embedder.compute_similarity(query, embeddings)

        assert similarities.shape == (4,)
        assert all(0.0 <= s <= 1.0 for s in similarities)
        # At least one other text should be somewhat similar (all are property descriptions)
        assert max(similarities[1:]) > 0.0

    def test_save_load(self):
        texts = [
            "Beautiful apartment near beach",
            "Modern house with pool",
            "Cozy studio in city center",
        ]
        embedder = ContentEmbedder(n_components=2)
        embedder.fit_transform(texts)

        state = embedder.save()
        loaded = ContentEmbedder.load(state)

        assert loaded.is_fitted
        new_embeddings = loaded.transform(["test text"])
        assert new_embeddings.shape == (1, 2)