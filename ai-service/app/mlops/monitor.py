"""Lightweight model monitoring — prediction counts and performance tracking.

Stores metrics in a JSON file alongside artifacts. No external dependencies.
"""

import json
from datetime import datetime, timezone
from pathlib import Path

import structlog

logger = structlog.get_logger("ai-service.mlops")

class ModelMonitor:
    """Track prediction counts and performance per model version."""

    def __init__(self, artifacts_path: str = "artifacts"):
        self.artifacts_path = Path(artifacts_path)
        self._metrics_file = self.artifacts_path / "prediction_metrics.json"
        self._metrics = self._load()

    def _load(self) -> dict:
        if self._metrics_file.exists():
            return json.loads(self._metrics_file.read_text(encoding="utf-8"))
        return {}

    def _save(self) -> None:
        self.artifacts_path.mkdir(parents=True, exist_ok=True)
        self._metrics_file.write_text(
            json.dumps(self._metrics, indent=2), encoding="utf-8"
        )

    def record_prediction(
        self, model_name: str, version: str, latency_ms: float, success: bool
    ) -> None:
        """Record a single prediction event."""
        key = f"{model_name}:{version}"
        if key not in self._metrics:
            self._metrics[key] = {
                "model_name": model_name,
                "version": version,
                "total_predictions": 0,
                "failures": 0,
                "total_latency_ms": 0.0,
                "first_prediction": datetime.now(timezone.utc).isoformat(),
                "last_prediction": None,
            }
        entry = self._metrics[key]
        entry["total_predictions"] += 1
        if not success:
            entry["failures"] += 1
        entry["total_latency_ms"] += latency_ms
        entry["last_prediction"] = datetime.now(timezone.utc).isoformat()
        self._save()

    def get_metrics(self, model_name: str) -> list[dict]:
        """Get metrics for all versions of a model."""
        return [
            v for v in self._metrics.values() if v["model_name"] == model_name
        ]

    def get_version_metrics(self, model_name: str, version: str) -> dict | None:
        """Get metrics for a specific model version."""
        key = f"{model_name}:{version}"
        return self._metrics.get(key)