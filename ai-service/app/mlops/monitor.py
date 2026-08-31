"""Lightweight model monitoring — prediction counts and performance tracking.

Stores metrics in a JSON file alongside artifacts. Uses file locking for
concurrent access and atomic writes to prevent partial reads.
"""

import json
import os
import tempfile
import time
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path

import structlog

logger = structlog.get_logger("ai-service.mlops")

LOCK_TIMEOUT = 10  # seconds

@contextmanager
def _file_lock(path: Path):
    """Cross-platform advisory file lock using a .lock sidecar file."""
    lock_path = path.with_suffix(".lock")
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(str(lock_path), os.O_CREAT | os.O_RDWR)
    try:
        acquired = False
        deadline = time.monotonic() + LOCK_TIMEOUT
        while time.monotonic() < deadline:
            try:
                # LOCK_EX | LOCK_NB — exclusive, non-blocking
                if os.name == "nt":
                    import msvcrt
                    msvcrt.locking(fd, msvcrt.LK_NBLCK, 1)
                else:
                    import fcntl
                    fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
                acquired = True
                break
            except OSError:
                time.sleep(0.01)
        if not acquired:
            raise TimeoutError(f"Could not acquire lock on {lock_path}")
        yield
    finally:
        if acquired:
            try:
                if os.name == "nt":
                    import msvcrt
                    msvcrt.locking(fd, msvcrt.LK_UNLCK, 1)
                else:
                    import fcntl
                    fcntl.flock(fd, fcntl.LOCK_UNLCK)
            except OSError:
                pass
        os.close(fd)

class ModelMonitor:
    """Track prediction counts and performance per model version."""

    def __init__(self, artifacts_path: str = "artifacts"):
        self.artifacts_path = Path(artifacts_path)
        self._metrics_file = self.artifacts_path / "prediction_metrics.json"
        self.artifacts_path.mkdir(parents=True, exist_ok=True)

    def _load(self) -> dict:
        if self._metrics_file.exists():
            return json.loads(self._metrics_file.read_text(encoding="utf-8"))
        return {}

    def _save(self, metrics: dict) -> None:
        """Persist metrics atomically via temp file + replace."""
        fd, tmp_path = tempfile.mkstemp(
            dir=str(self.artifacts_path), suffix=".tmp", prefix="metrics_"
        )
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as f:
                json.dump(metrics, f, indent=2)
            os.replace(tmp_path, str(self._metrics_file))
        except BaseException:
            try:
                os.unlink(tmp_path)
            except OSError:
                pass
            raise

    def record_prediction(
        self, model_name: str, version: str, latency_ms: float, success: bool
    ) -> None:
        """Record a single prediction event.

        Acquires an advisory lock, reloads current metrics, applies the
        update, and saves atomically so readers never see truncated JSON.
        """
        with _file_lock(self._metrics_file):
            metrics = self._load()
            key = f"{model_name}:{version}"
            if key not in metrics:
                metrics[key] = {
                    "model_name": model_name,
                    "version": version,
                    "total_predictions": 0,
                    "failures": 0,
                    "total_latency_ms": 0.0,
                    "first_prediction": datetime.now(timezone.utc).isoformat(),
                    "last_prediction": None,
                }
            entry = metrics[key]
            entry["total_predictions"] += 1
            if not success:
                entry["failures"] += 1
            entry["total_latency_ms"] += latency_ms
            entry["last_prediction"] = datetime.now(timezone.utc).isoformat()
            self._save(metrics)

    def get_metrics(self, model_name: str) -> list[dict]:
        """Get metrics for all versions of a model."""
        metrics = self._load()
        return [
            v for v in metrics.values() if v["model_name"] == model_name
        ]

    def get_version_metrics(self, model_name: str, version: str) -> dict | None:
        """Get metrics for a specific model version."""
        metrics = self._load()
        key = f"{model_name}:{version}"
        return metrics.get(key)
