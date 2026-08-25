"""Content embeddings using TF-IDF + SVD.

Transforms property text into dense vectors for similarity computation.
Handles small datasets by reducing n_components dynamically.
"""

from __future__ import annotations

import numpy as np
import structlog
from sklearn.decomposition import TruncatedSVD
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.preprocessing import normalize

logger = structlog.get_logger("ai-service.ml.recommendation")

class ContentEmbedder:
    """TF-IDF + SVD dimensionality reduction for property text.
    
    Pipeline:
    1. TfidfVectorizer → sparse matrix (n_samples × n_features)
    2. TruncatedSVD → dense matrix (n_samples × n_components)
    3. L2 normalization → unit vectors for cosine similarity
    
    Handles edge cases:
    - Empty corpus: returns zero vectors
    - Small corpus: reduces n_components automatically
    - Single document: returns single-row matrix
    """

    def __init__(
        self,
        n_components: int = 8,
        max_features: int = 500,
        random_state: int = 42,
    ):
        self.n_components = n_components
        self.max_features = max_features
        self.random_state = random_state
        self._tfidf: TfidfVectorizer | None = None
        self._svd: TruncatedSVD | None = None
        self._fitted = False
        self._actual_components: int = 0

    def fit_transform(self, texts: list[str]) -> np.ndarray:
        """Fit TF-IDF + SVD and return dense embeddings.
        
        Args:
            texts: list of property text (title + description)
            
        Returns:
            np.ndarray of shape (n_samples, n_components)
        """
        n_samples = len(texts)

        if n_samples == 0:
            logger.warning("embedder_empty_corpus")
            return np.zeros((0, self.n_components))

        # Adjust n_components for small datasets
        # SVD requires: n_components < min(n_samples, n_features)
        effective_components = min(self.n_components, max(1, n_samples - 1))
        if effective_components < self.n_components:
            logger.info(
                "embedder_reduced_components",
                requested=self.n_components,
                actual=effective_components,
                n_samples=n_samples,
            )

        # Fit TF-IDF
        self._tfidf = TfidfVectorizer(
            max_features=self.max_features,
            stop_words="english",
            sublinear_tf=True,
            min_df=1,
            max_df=0.95,
        )
        tfidf_matrix = self._tfidf.fit_transform(texts)
        n_features = tfidf_matrix.shape[1]

        # Further reduce if n_features is small
        effective_components = min(effective_components, max(1, n_features - 1))
        self._actual_components = effective_components

        # Fit SVD
        self._svd = TruncatedSVD(
            n_components=effective_components,
            random_state=self.random_state,
        )
        embeddings = self._svd.fit_transform(tfidf_matrix)

        # L2 normalize for cosine similarity
        embeddings = normalize(embeddings, norm="l2")

        self._fitted = True

        explained = float(self._svd.explained_variance_ratio_.sum())
        logger.info(
            "embedder_fitted",
            n_samples=n_samples,
            n_features=n_features,
            n_components=effective_components,
            explained_variance=round(explained, 4),
        )

        return embeddings

    def transform(self, texts: list[str]) -> np.ndarray:
        """Transform new texts using fitted TF-IDF + SVD.
        
        Raises RuntimeError if not fitted.
        """
        if not self._fitted or self._tfidf is None or self._svd is None:
            raise RuntimeError("Embedder not fitted. Call fit_transform() first.")

        if not texts:
            return np.zeros((0, self._actual_components))

        tfidf_matrix = self._tfidf.transform(texts)
        embeddings = self._svd.transform(tfidf_matrix)
        embeddings = normalize(embeddings, norm="l2")
        return embeddings

    def compute_similarity(
        self, query_embedding: np.ndarray, candidate_embeddings: np.ndarray
    ) -> np.ndarray:
        """Compute cosine similarity between query and candidates.
        
        Args:
            query_embedding: shape (n_components,)
            candidate_embeddings: shape (n_candidates, n_components)
            
        Returns:
            np.ndarray of shape (n_candidates,) with similarity scores in [0, 1]
        """
        if candidate_embeddings.shape[0] == 0:
            return np.array([])

        # Cosine similarity via dot product (vectors are L2-normalized)
        scores = candidate_embeddings @ query_embedding
        # Clamp to [0, 1] for safety
        return np.clip(scores, 0.0, 1.0)

    @property
    def is_fitted(self) -> bool:
        return self._fitted

    def get_explained_variance(self) -> float:
        """Total explained variance ratio (0-1)."""
        if self._svd is None:
            return 0.0
        return float(self._svd.explained_variance_ratio_.sum())

    def save(self) -> dict:
        """Return serializable state for persistence."""
        return {
            "n_components": self.n_components,
            "max_features": self.max_features,
            "random_state": self.random_state,
            "actual_components": self._actual_components,
            "fitted": self._fitted,
            "tfidf": self._tfidf,
            "svd": self._svd,
        }

    @classmethod
    def load(cls, state: dict) -> "ContentEmbedder":
        """Restore from saved state."""
        embedder = cls(
            n_components=state["n_components"],
            max_features=state["max_features"],
            random_state=state["random_state"],
        )
        embedder._actual_components = state["actual_components"]
        embedder._fitted = state["fitted"]
        embedder._tfidf = state["tfidf"]
        embedder._svd = state["svd"]
        return embedder