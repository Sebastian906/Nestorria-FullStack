"""ResNet50 embedding extraction for property images.

Uses transfer learning: ResNet50 pretrained on ImageNet, with the
classification head removed to produce 2048-dimensional feature vectors.
"""

import structlog
import numpy as np
import torch
import torch.nn as nn
from torchvision import models

logger = structlog.get_logger("ai-service.dl.image.embeddings")

EMBEDDING_DIMENSION = 2048

class ImageEmbeddingExtractor:
    """Extract 2048-dimensional embeddings from images using ResNet50.

    The model runs in eval mode with torch.no_grad() for inference.
    Supports CPU only (no GPU dependency).
    """

    def __init__(self):
        self.device = torch.device("cpu")
        self.model = self._load_model()
        logger.info(
            "embedding_extractor_initialized",
            model="resnet50",
            device=str(self.device),
            embedding_dim=EMBEDDING_DIMENSION,
        )

    def _load_model(self) -> nn.Module:
        """Load pretrained ResNet50 and remove classification head."""
        # Use weights parameter (modern API, not deprecated pretrained=True)
        try:
            model = models.resnet50(weights=models.ResNet50_Weights.IMAGENET1K_V1)
        except AttributeError:
            # Fallback for older torchvision versions
            model = models.resnet50(pretrained=True)

        # Remove the last FC layer → keep avgpool output (2048-dim)
        model = nn.Sequential(*list(model.children())[:-1])
        model.to(self.device)
        model.eval()
        return model

    @torch.no_grad()
    def extract(self, tensor: torch.Tensor) -> np.ndarray:
        """Extract embedding from preprocessed image tensor.

        Args:
            tensor: Preprocessed tensor of shape (1, 3, 224, 224).

        Returns:
            numpy array of shape (2048,).

        Raises:
            RuntimeError: If model inference fails.
        """
        tensor = tensor.to(self.device)
        embedding = self.model(tensor)
        return embedding.flatten().cpu().numpy()

    def extract_batch(self, tensors: torch.Tensor) -> np.ndarray:
        """Extract embeddings from a batch of tensors.

        Args:
            tensors: Batch of tensors of shape (N, 3, 224, 224).

        Returns:
            numpy array of shape (N, 2048).
        """
        tensors = tensors.to(self.device)
        embeddings = self.model(tensors)
        return embeddings.squeeze(-1).squeeze(-1).cpu().numpy()