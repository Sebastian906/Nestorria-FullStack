"""Image preprocessing for ResNet50 transfer learning.

Handles resize, normalization, and tensor conversion following
ImageNet statistics.
"""

import structlog
import torch
from PIL import Image
from torchvision import transforms

logger = structlog.get_logger("ai-service.dl.image.preprocessor")

# ImageNet normalization constants
IMAGENET_MEAN = [0.485, 0.456, 0.406]
IMAGENET_STD = [0.229, 0.224, 0.225]

class ImagePreprocessor:
    """Preprocess images for ResNet50 inference.

    Input: PIL.Image (any size)
    Output: torch.Tensor of shape (1, 3, 224, 224)
    """

    def __init__(self, size: tuple[int, int] = (224, 224)):
        self.transform = transforms.Compose([
            transforms.Resize(size),
            transforms.ToTensor(),
            transforms.Normalize(mean=IMAGENET_MEAN, std=IMAGENET_STD),
        ])
        self.target_size = size
        logger.info("preprocessor_initialized", target_size=size)

    def preprocess(self, image: Image.Image) -> torch.Tensor:
        """Convert PIL Image to preprocessed tensor.

        Non-RGB images are automatically converted to RGB.

        Args:
            image: PIL Image (any mode).

        Returns:
            Tensor of shape (1, 3, 224, 224) with batch dimension.
        """
        if image.mode != "RGB":
            logger.info("converting_image_mode", from_mode=image.mode, to_mode="RGB")
            image = image.convert("RGB")

        tensor = self.transform(image).unsqueeze(0)  # Add batch dim
        logger.debug(
            "image_preprocessed",
            original_size=image.size,
            tensor_shape=tuple(tensor.shape),
        )
        return tensor