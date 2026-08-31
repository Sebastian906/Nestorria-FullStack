"""Prediction logging for future analysis.

Append-only JSONL file. One line per prediction.
90-day TTL: old log files are cleaned up on write.
"""

import json
import os
from datetime import datetime, timezone, timedelta
from pathlib import Path
from typing import Optional

import structlog

logger = structlog.get_logger("ai-service.mlops")

RETENTION_DAYS = 90

class PredictionLogger:
    """Log predictions to a JSONL file for analysis."""

    def __init__(self, artifacts_path: str = "artifacts"):
        self.artifacts_path = Path(artifacts_path)
        self._log_file = self.artifacts_path / "predictions.jsonl"
        self.artifacts_path.mkdir(parents=True, exist_ok=True)

    def _cleanup_old_logs(self) -> None:
        """Remove prediction log files older than RETENTION_DAYS."""
        cutoff = datetime.now(timezone.utc) - timedelta(days=RETENTION_DAYS)
        for log_file in self.artifacts_path.glob("predictions*.jsonl"):
            try:
                mtime = datetime.fromtimestamp(
                    log_file.stat().st_mtime, tz=timezone.utc
                )
                if mtime < cutoff:
                    log_file.unlink()
                    logger.info("old_prediction_log_removed", path=str(log_file))
            except OSError:
                pass

    def log_prediction(
        self,
        model_name: str,
        version: str,
        input_data: dict,
        prediction: float,
        actual: Optional[float] = None,
        latency_ms: Optional[float] = None,
    ) -> None:
        """Append a prediction record."""
        record = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "model_name": model_name,
            "version": version,
            "input_data": input_data,
            "prediction": prediction,
            "actual": actual,
            "latency_ms": latency_ms,
        }
        with open(self._log_file, "a", encoding="utf-8") as f:
            f.write(json.dumps(record) + "\n")

        logger.info("prediction_logged", model=model_name, version=version)
        self._cleanup_old_logs()

    def get_predictions(
        self,
        model_name: Optional[str] = None,
        start_date: Optional[str] = None,
        end_date: Optional[str] = None,
        limit: int = 1000,
    ) -> list[dict]:
        """Read predictions with optional filters."""
        if not self._log_file.exists():
            return []

        results = []
        with open(self._log_file, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                record = json.loads(line)

                if model_name and record.get("model_name") != model_name:
                    continue
                if start_date and record.get("timestamp", "") < start_date:
                    continue
                if end_date and record.get("timestamp", "") > end_date:
                    continue

                results.append(record)
                if len(results) >= limit:
                    break

        return results