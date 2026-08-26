"""Visual similarity search using pgvector.

Stores image embeddings in PostgreSQL and performs cosine similarity
search. Each property can have multiple images, each with its own
embedding. Search results are aggregated per property (max similarity).

ponytail: sequential scan without index — fine for ~50 images.
When scale demands it (1K+ images), upgrade pgvector to 0.8.0+
and add HNSW index (requires >2000 dimension support).
"""

import math
import time
import urllib.request
import io

import numpy as np
import psycopg2
import structlog
from PIL import Image

from app.config import get_settings
from app.dl.image.embeddings import EMBEDDING_DIMENSION, ImageEmbeddingExtractor
from app.dl.image.preprocessor import ImagePreprocessor

logger = structlog.get_logger("ai-service.dl.image.similarity")

# SQL statements — sequential scan, no index
SQL_INSERT_EMBEDDING = """
    INSERT INTO property_image_embeddings (property_id, image_url, embedding)
    VALUES (%s, %s, %s::vector)
    ON CONFLICT (property_id, image_url)
    DO UPDATE SET embedding = EXCLUDED.embedding
"""

SQL_DELETE_PROPERTY_EMBEDDINGS = """
    DELETE FROM property_image_embeddings WHERE property_id = %s
"""

SQL_FIND_SIMILAR = """
    SELECT property_id, MAX(1 - (embedding <=> %s::vector)) AS similarity
    FROM property_image_embeddings
    WHERE property_id != %s
    GROUP BY property_id
    ORDER BY similarity DESC
    LIMIT %s
"""

SQL_SEARCH_BY_IMAGE = """
    SELECT property_id, MAX(1 - (embedding <=> %s::vector)) AS similarity
    FROM property_image_embeddings
    GROUP BY property_id
    ORDER BY similarity DESC
    LIMIT %s
"""

SQL_COUNT_EMBEDDINGS = """
    SELECT COUNT(*) FROM property_image_embeddings
"""


def _normalize_similarity(value: float) -> float:
    """Clamp similarity to [0.0, 1.0], convert non-finite to 0.0."""
    if not math.isfinite(value):
        return 0.0
    return max(0.0, min(1.0, value))


class VisualSimilarityEngine:
    """Manages embedding storage and similarity search via pgvector.

    Uses cosine similarity (1 - L2 distance for normalized vectors).
    Sequential scan without index — acceptable for experimental scale.
    """

    def __init__(self):
        self.preprocessor = ImagePreprocessor()
        self.extractor = ImageEmbeddingExtractor()
        # ponytail: single connection, fine for experimental/low volume.
        # Upgrade to psycopg2.pool.ThreadedConnectionPool if concurrent requests matter.
        self._conn = None
        logger.info("similarity_engine_initialized")

    def _get_connection(self):
        """Get or create database connection."""
        if self._conn is None or self._conn.closed:
            settings = get_settings()
            if not settings.database_url:
                raise RuntimeError("DATABASE_URL not configured for pgvector")
            self._conn = psycopg2.connect(settings.database_url)
            logger.info("pgvector_connection_established")
        return self._conn

    def index_property(self, property_id: str, image_urls: list[str]) -> int:
        """Download images, extract embeddings, and store in pgvector.

        Extracts all embeddings first, then replaces existing data.
        If no embeddings are extracted, existing data is preserved.

        Args:
            property_id: The property UUID.
            image_urls: List of Cloudinary URLs for this property.

        Returns:
            Number of embeddings stored.
        """
        conn = self._get_connection()
        stored = 0
        embeddings = []

        for url in image_urls:
            try:
                t0 = time.perf_counter()
                embedding = self._extract_from_url(url)
                elapsed_ms = (time.perf_counter() - t0) * 1000

                embeddings.append((url, embedding))
                stored += 1

                logger.info(
                    "embedding_extracted",
                    property_id=property_id,
                    url=url,
                    elapsed_ms=round(elapsed_ms, 1),
                    dimension=EMBEDDING_DIMENSION,
                )
            except Exception as e:
                logger.warning(
                    "embedding_extraction_failed",
                    property_id=property_id,
                    url=url,
                    error=str(e),
                )
                continue

        if not embeddings:
            logger.warning(
                "no_embeddings_extracted",
                property_id=property_id,
                images_total=len(image_urls),
            )
            return 0

        try:
            with conn.cursor() as cur:
                cur.execute(SQL_DELETE_PROPERTY_EMBEDDINGS, (property_id,))

                for url, embedding in embeddings:
                    cur.execute(
                        SQL_INSERT_EMBEDDING,
                        (property_id, url, embedding.tolist()),
                    )

            conn.commit()
            logger.info(
                "property_indexed",
                property_id=property_id,
                images_total=len(image_urls),
                images_stored=stored,
            )
        except Exception as e:
            conn.rollback()
            logger.error(
                "indexing_failed",
                property_id=property_id,
                error=str(e),
            )
            raise

        return stored

    def _extract_from_url(self, url: str) -> np.ndarray:
        """Download image from URL and extract embedding."""
        with urllib.request.urlopen(url, timeout=30) as response:
            image_data = response.read()

        image = Image.open(io.BytesIO(image_data))
        tensor = self.preprocessor.preprocess(image)
        return self.extractor.extract(tensor)

    def find_similar(
        self, property_id: str, limit: int = 5
    ) -> list[dict]:
        """Find properties visually similar to the given property.

        Args:
            property_id: The property to find similar ones for.
            limit: Maximum results to return.

        Returns:
            List of {"property_id": str, "similarity": float} dicts.
        """
        conn = self._get_connection()

        # Get one embedding from the target property (deterministic via id ordering)
        with conn.cursor() as cur:
            cur.execute(
                "SELECT embedding FROM property_image_embeddings "
                "WHERE property_id = %s ORDER BY id LIMIT 1",
                (property_id,),
            )
            row = cur.fetchone()
            if row is None:
                logger.warning("no_embeddings_found", property_id=property_id)
                return []

            query_embedding = row[0]

        t0 = time.perf_counter()
        with conn.cursor() as cur:
            cur.execute(SQL_FIND_SIMILAR, (query_embedding, property_id, limit))
            results = [
                {"property_id": r[0], "similarity": _normalize_similarity(float(r[1]))}
                for r in cur.fetchall()
            ]
        elapsed_ms = (time.perf_counter() - t0) * 1000

        logger.info(
            "similar_search_completed",
            query_property=property_id,
            results_count=len(results),
            elapsed_ms=round(elapsed_ms, 1),
        )
        return results

    def search_by_image(
        self, image: Image.Image, limit: int = 5
    ) -> list[dict]:
        """Search for properties similar to an uploaded image.

        Args:
            image: PIL Image to search with.
            limit: Maximum results to return.

        Returns:
            List of {"property_id": str, "similarity": float} dicts.
        """
        t0 = time.perf_counter()
        tensor = self.preprocessor.preprocess(image)
        query_embedding = self.extractor.extract(tensor)
        extract_ms = (time.perf_counter() - t0) * 1000

        conn = self._get_connection()
        t1 = time.perf_counter()
        with conn.cursor() as cur:
            cur.execute(SQL_SEARCH_BY_IMAGE, (query_embedding.tolist(), limit))
            results = [
                {"property_id": r[0], "similarity": _normalize_similarity(float(r[1]))}
                for r in cur.fetchall()
            ]
        query_ms = (time.perf_counter() - t1) * 1000

        logger.info(
            "image_search_completed",
            results_count=len(results),
            extract_ms=round(extract_ms, 1),
            query_ms=round(query_ms, 1),
        )
        return results

    def get_index_size(self) -> int:
        """Return the total number of stored embeddings."""
        conn = self._get_connection()
        with conn.cursor() as cur:
            cur.execute(SQL_COUNT_EMBEDDINGS)
            return cur.fetchone()[0]

    def close(self):
        """Close database connection."""
        if self._conn and not self._conn.closed:
            self._conn.close()
            logger.info("pgvector_connection_closed")
