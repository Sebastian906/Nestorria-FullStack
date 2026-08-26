"""Tests for ImagePreprocessor."""

import torch
from PIL import Image

from app.dl.image.preprocessor import ImagePreprocessor


class TestImagePreprocessor:
    """Test suite for ImagePreprocessor."""

    def setup_method(self):
        self.preprocessor = ImagePreprocessor()

    def test_preprocess_returns_correct_shape(self):
        """preprocess() should return tensor of shape (1, 3, 224, 224)."""
        image = Image.new("RGB", (640, 480), color=(128, 64, 32))
        tensor = self.preprocessor.preprocess(image)

        assert isinstance(tensor, torch.Tensor)
        assert tensor.shape == (1, 3, 224, 224)

    def test_preprocess_rgb_image(self):
        """RGB images should be processed without conversion."""
        image = Image.new("RGB", (300, 300), color=(100, 150, 200))
        tensor = self.preprocessor.preprocess(image)

        assert tensor.shape == (1, 3, 224, 224)
        assert tensor.dtype == torch.float32

    def test_preprocess_rgba_image_converts_to_rgb(self):
        """RGBA images should be converted to RGB."""
        image = Image.new("RGBA", (200, 200), color=(100, 150, 200, 128))
        tensor = self.preprocessor.preprocess(image)

        assert tensor.shape == (1, 3, 224, 224)

    def test_preprocess_grayscale_image_converts_to_rgb(self):
        """Grayscale images should be converted to RGB."""
        image = Image.new("L", (200, 200), color=128)
        tensor = self.preprocessor.preprocess(image)

        assert tensor.shape == (1, 3, 224, 224)

    def test_preprocess_normalization_range(self):
        """Output should be normalized (roughly in [-2.1, 2.5] range for ImageNet)."""
        image = Image.new("RGB", (224, 224), color=(128, 128, 128))
        tensor = self.preprocessor.preprocess(image)

        # After ImageNet normalization, values should be roughly in [-2.5, 2.5]
        assert tensor.min() >= -3.0
        assert tensor.max() <= 3.0

    def test_preprocess_different_sizes(self):
        """Should handle various input sizes."""
        for size in [(100, 100), (1920, 1080), (50, 300)]:
            image = Image.new("RGB", size, color=(128, 128, 128))
            tensor = self.preprocessor.preprocess(image)
            assert tensor.shape == (1, 3, 224, 224)
