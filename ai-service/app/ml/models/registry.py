"""Model registry for persisting and loading serialized models.

Uses joblib for serialization. Models are stored in a configured
artifacts directory with JSON metadata.
"""

import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import joblib
import structlog

logger = structlog.get_logger("ai-service.ml")

class ModelRegistry:
    """Registry for saving, loading, and listing ML models.

    Models are persisted with joblib in the artifacts directory.
    Each model has a JSON metadata file with name, version, metrics,
    date, and feature information.
    """

    def __init__(self, artifacts_path: str = "artifacts"):
        self.artifacts_path = Path(artifacts_path)
        self.artifacts_path.mkdir(parents=True, exist_ok=True)

    def _validate_name(self, name: str) -> None:
        """Validate model name to prevent path traversal."""
        if ".." in name or "/" in name or "\\" in name:
            raise ValueError(f"Invalid model name: {name}")

    def _validate_version(self, version: str) -> None:
        """Validate model version to prevent path traversal."""
        if ".." in version or "/" in version or "\\" in version:
            raise ValueError(f"Invalid model version: {version}")

    def _model_dir(self, name: str, version: str) -> Path:
        """Get the directory for a specific model version."""
        self._validate_name(name)
        self._validate_version(version)
        return self.artifacts_path / name / version

    def save_model(
        self,
        model: Any,
        name: str,
        version: str,
        metrics: dict,
        features: list[str] | None = None,
    ) -> Path:
        """Save a model with metadata.

        Returns the path to the saved model file.
        """
        model_dir = self._model_dir(name, version)
        model_dir.mkdir(parents=True, exist_ok=True)

        model_path = model_dir / "model.joblib"
        metadata_path = model_dir / "metadata.json"

        # Save model
        joblib.dump(model, model_path)

        # Save metadata
        metadata = {
            "name": name,
            "version": version,
            "metrics": metrics,
            "date": datetime.now(timezone.utc).isoformat(),
            "features": features or [],
            "model_path": str(model_path),
        }
        metadata_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")

        logger.info(
            "model_saved",
            name=name,
            version=version,
            path=str(model_path),
        )
        return model_path

    def load_model(self, name: str, version: str) -> Any:
        """Load a model by name and version.

        Only loads from the configured artifacts directory.
        """
        model_dir = self._model_dir(name, version)
        model_path = model_dir / "model.joblib"

        if not model_path.exists():
            raise FileNotFoundError(
                f"Model not found: {name} version {version} at {model_path}"
            )

        logger.info("model_loaded", name=name, version=version)
        return joblib.load(model_path)

    def list_models(self) -> list[dict]:
        """List all models with their metadata."""
        models = []
        if not self.artifacts_path.exists():
            return models

        for name_dir in sorted(self.artifacts_path.iterdir()):
            if not name_dir.is_dir():
                continue
            for version_dir in sorted(name_dir.iterdir()):
                if not version_dir.is_dir():
                    continue
                metadata_path = version_dir / "metadata.json"
                if metadata_path.exists():
                    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
                    models.append(metadata)
        return models

    def get_latest(self, name: str) -> dict | None:
        """Get the latest version of a model by name."""
        self._validate_name(name)
        name_dir = self.artifacts_path / name

        if not name_dir.exists():
            return None

        versions = []
        for version_dir in name_dir.iterdir():
            if not version_dir.is_dir():
                continue
            metadata_path = version_dir / "metadata.json"
            if metadata_path.exists():
                metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
                versions.append(metadata)

        if not versions:
            return None

        # Sort by date, return latest
        versions.sort(key=lambda m: m.get("date", ""), reverse=True)
        return versions[0]