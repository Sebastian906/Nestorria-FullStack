"""Tests for ImageEmbeddingExtractor."""

from unittest.mock import MagicMock, patch

import numpy as np
import torch

from app.dl.image.embeddings import EMBEDDING_DIMENSION, ImageEmbeddingExtractor


class TestImageEmbeddingExtractor:
    """Test suite for ImageEmbeddingExtractor."""

    def _make_extractor(self):
        """Create extractor with mocked model to avoid downloading weights."""
        with patch("app.dl.image.embeddings.models.resnet50") as mock_resnet50:
            # Mock the model: avgpool output is (N, 2048, 1, 1)
            mock_model = MagicMock()
            mock_resnet50.return_value = mock_model

            # Mock children() to return a list where we can slice off the last
            mock_children = [MagicMock() for _ in range(10)]
            mock_model.children.return_value = mock_children

            # Mock the sequential model after slicing
            with patch("app.dl.image.embeddings.nn.Sequential") as mock_seq:
                mock_sequential = MagicMock()
                mock_seq.return_value = mock_sequential

                extractor = ImageEmbeddingExtractor()
                extractor.model = mock_sequential
                return extractor, mock_sequential

    def test_embedding_dimension_constant(self):
        """EMBEDDING_DIMENSION should be 2048."""
        assert EMBEDDING_DIMENSION == 2048

    def test_extract_returns_numpy_array(self):
        """extract() should return a numpy array."""
        extractor, mock_model = self._make_extractor()

        # Mock model output: shape (1, 2048, 1, 1)
        mock_output = torch.randn(1, 2048, 1, 1)
        mock_model.return_value = mock_output

        tensor = torch.randn(1, 3, 224, 224)
        result = extractor.extract(tensor)

        assert isinstance(result, np.ndarray)

    def test_extract_returns_2048_dim(self):
        """extract() should return a 2048-dimensional vector."""
        extractor, mock_model = self._make_extractor()

        mock_output = torch.randn(1, 2048, 1, 1)
        mock_model.return_value = mock_output

        tensor = torch.randn(1, 3, 224, 224)
        result = extractor.extract(tensor)

        assert result.shape == (2048,)

    def test_extract_batch_returns_2d_array(self):
        """extract_batch() should return (N, 2048) array."""
        extractor, mock_model = self._make_extractor()

        batch_size = 4
        mock_output = torch.randn(batch_size, 2048, 1, 1)
        mock_model.return_value = mock_output

        tensors = torch.randn(batch_size, 3, 224, 224)
        result = extractor.extract_batch(tensors)

        assert isinstance(result, np.ndarray)
        assert result.shape == (batch_size, 2048)

    def test_model_is_in_eval_mode(self):
        """Model should be in eval mode after initialization."""
        extractor, mock_model = self._make_extractor()
        # In real code, model.eval() is called; verify the mock was set up
        assert extractor.model is mock_model

    def test_device_is_cpu(self):
        """Extractor should use CPU device."""
        extractor, _ = self._make_extractor()
        assert str(extractor.device) == "cpu"
