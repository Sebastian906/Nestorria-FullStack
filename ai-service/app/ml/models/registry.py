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
        if not name or not name.strip():
            raise ValueError("Model name must not be empty")
        if name.startswith(".") or name.endswith("."):
            raise ValueError(f"Invalid model name: {name}")
        if ".." in name or "/" in name or "\\" in name:
            raise ValueError(f"Invalid model name: {name}")

    def _validate_version(self, version: str) -> None:
        """Validate model version to prevent path traversal."""
        if not version or not version.strip():
            raise ValueError("Model version must not be empty")
        if version.startswith(".") or version.endswith("."):
            raise ValueError(f"Invalid model version: {version}")
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

        # Build and validate metadata before persisting anything
        metadata = {
            "name": name,
            "version": version,
            "metrics": metrics,
            "date": datetime.now(timezone.utc).isoformat(),
            "features": features or [],
        }
        metadata_json = json.dumps(metadata, indent=2)
        # Validate round-trip
        json.loads(metadata_json)

        # Persist model first, then metadata
        joblib.dump(model, model_path)
        metadata_path.write_text(metadata_json, encoding="utf-8")

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

    def _active_versions_file(self) -> Path:
        """Path to the active versions index."""
        return self.artifacts_path / "active_versions.json"
    
    def _load_active(self) -> dict:
        """Load active version mappings."""
        path = self._active_versions_file()
        if path.exists():
            return json.loads(path.read_text(encoding="utf-8"))
        return {}
    
    def _save_active(self, active: dict) -> None:
        """Persist active version mappings."""
        self.artifacts_path.mkdir(parents=True, exist_ok=True)
        self._active_versions_file().write_text(
            json.dumps(active, indent=2), encoding="utf-8"
        )
    
    def get_active(self, name: str) -> dict | None:
        """Get the active (production) version of a model."""
        active = self._load_active()
        version = active.get(name)
        if not version:
            return self.get_latest(name)
        return self.get_version(name, version)
    
    def get_version(self, name: str, version: str) -> dict | None:
        """Get metadata for a specific model version."""
        self._validate_name(name)
        self._validate_version(version)
        metadata_path = self.artifacts_path / name / version / "metadata.json"
        if metadata_path.exists():
            return json.loads(metadata_path.read_text(encoding="utf-8"))
        return None
    
    def list_versions(self, name: str) -> list[dict]:
        """List all versions of a model with metadata."""
        self._validate_name(name)
        name_dir = self.artifacts_path / name
        if not name_dir.exists():
            return []
    
        versions = []
        for version_dir in sorted(name_dir.iterdir()):
            if not version_dir.is_dir():
                continue
            metadata_path = version_dir / "metadata.json"
            if metadata_path.exists():
                versions.append(
                    json.loads(metadata_path.read_text(encoding="utf-8"))
                )
        return versions
    
    def promote(self, name: str, version: str) -> dict:
        """Promote a model version to active/production."""
        current = self.get_version(name, version)
        if not current:
            raise FileNotFoundError(
                f"Model {name} version {version} not found"
            )
    
        active = self._load_active()
        previous_version = active.get(name)
        active[name] = version
        self._save_active(active)
    
        logger.info(
            "model_promoted",
            name=name,
            version=version,
            previous_version=previous_version,
        )
        return {
            "name": name,
            "previous_version": previous_version,
            "new_version": version,
        }
    
    def rollback(self, name: str, target_version: str) -> dict:
        """Rollback to a previous version (alias for promote to older version)."""
        return self.promote(name, target_version)
    
    def compare_versions(self, name: str, v1: str, v2: str) -> dict:
        """Compare metrics between two model versions."""
        m1 = self.get_version(name, v1)
        m2 = self.get_version(name, v2)
    
        if not m1:
            raise FileNotFoundError(f"Model {name} version {v1} not found")
        if not m2:
            raise FileNotFoundError(f"Model {name} version {v2} not found")
    
        metrics1 = m1.get("metrics", {})
        metrics2 = m2.get("metrics", {})
    
        # Compute deltas for common metric keys
        all_keys = set(metrics1.keys()) | set(metrics2.keys())
        comparison = {}
        for key in all_keys:
            val1 = metrics1.get(key)
            val2 = metrics2.get(key)
            if isinstance(val1, (int, float)) and isinstance(val2, (int, float)):
                comparison[key] = {
                    v1: val1,
                    v2: val2,
                    "delta": val2 - val1,
                    "improved": val2 > val1 if "r2" in key or "f1" in key else val2 < val1,
                }
            else:
                comparison[key] = {v1: val1, v2: val2}
    
        return {
            "model": name,
            "version_1": v1,
            "version_2": v2,
            "date_1": m1.get("date"),
            "date_2": m2.get("date"),
            "metrics_comparison": comparison,
        }