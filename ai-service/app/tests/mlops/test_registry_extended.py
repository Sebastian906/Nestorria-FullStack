"""Tests for extended ModelRegistry methods: promote, rollback, compare, versions."""

import json

import joblib
import pytest
from sklearn.linear_model import LinearRegression

from app.ml.models.registry import ModelRegistry

@pytest.fixture
def registry(tmp_path):
    """Registry using a temporary directory."""
    return ModelRegistry(artifacts_path=str(tmp_path))

@pytest.fixture
def registry_with_models(registry):
    """Registry pre-populated with two versions of a model."""
    model = LinearRegression()
    model.fit([[1], [2], [3]], [1, 2, 3])
    registry.save_model(model, "price_model", "1.0.0", {"mse": 0.5, "r2": 0.9})
    registry.save_model(model, "price_model", "2.0.0", {"mse": 0.3, "r2": 0.95})
    registry.save_model(model, "price_model", "2.1.0", {"mse": 0.25, "r2": 0.97})
    return registry

class TestGetVersion:
    def test_get_version_exists(self, registry_with_models):
        meta = registry_with_models.get_version("price_model", "1.0.0")
        assert meta is not None
        assert meta["name"] == "price_model"
        assert meta["version"] == "1.0.0"
        assert meta["metrics"]["mse"] == 0.5

    def test_get_version_not_found(self, registry_with_models):
        assert registry_with_models.get_version("price_model", "9.0.0") is None

    def test_get_version_invalid_name(self, registry):
        with pytest.raises(ValueError):
            registry.get_version("../etc", "1.0")

class TestListVersions:
    def test_list_versions(self, registry_with_models):
        versions = registry_with_models.list_versions("price_model")
        assert len(versions) == 3
        version_numbers = [v["version"] for v in versions]
        assert "1.0.0" in version_numbers
        assert "2.0.0" in version_numbers
        assert "2.1.0" in version_numbers

    def test_list_versions_empty(self, registry):
        assert registry.list_versions("nonexistent") == []

class TestPromote:
    def test_promote_first_time(self, registry_with_models):
        result = registry_with_models.promote("price_model", "2.0.0")
        assert result["name"] == "price_model"
        assert result["new_version"] == "2.0.0"
        assert result["previous_version"] is None

    def test_promote_changes_active(self, registry_with_models):
        registry_with_models.promote("price_model", "2.0.0")
        result = registry_with_models.promote("price_model", "2.1.0")
        assert result["previous_version"] == "2.0.0"
        assert result["new_version"] == "2.1.0"

    def test_promote_nonexistent_raises(self, registry):
        with pytest.raises(FileNotFoundError):
            registry.promote("nonexistent", "1.0.0")

    def test_promote_persists(self, registry_with_models):
        registry_with_models.promote("price_model", "2.0.0")
        active = registry_with_models._load_active()
        assert active["price_model"] == "2.0.0"

class TestRollback:
    def test_rollback_to_previous(self, registry_with_models):
        registry_with_models.promote("price_model", "2.1.0")
        result = registry_with_models.rollback("price_model", "2.0.0")
        assert result["new_version"] == "2.0.0"
        assert result["previous_version"] == "2.1.0"

class TestGetActive:
    def test_get_active_returns_latest_when_none_promoted(self, registry_with_models):
        active = registry_with_models.get_active("price_model")
        assert active is not None
        # get_latest returns the most recent by date
        assert active["version"] in ("1.0.0", "2.0.0", "2.1.0")

    def test_get_active_returns_promoted(self, registry_with_models):
        registry_with_models.promote("price_model", "1.0.0")
        active = registry_with_models.get_active("price_model")
        assert active["version"] == "1.0.0"

    def test_get_active_nonexistent(self, registry):
        assert registry.get_active("nonexistent") is None

class TestCompareVersions:
    def test_compare_metrics(self, registry_with_models):
        result = registry_with_models.compare_versions(
            "price_model", "1.0.0", "2.0.0"
        )
        assert result["model"] == "price_model"
        assert result["version_1"] == "1.0.0"
        assert result["version_2"] == "2.0.0"

        comp = result["metrics_comparison"]
        # mse: lower is better, so v2 improved
        assert comp["mse"]["improved"] is True
        assert comp["mse"]["delta"] == pytest.approx(-0.2)
        # r2: higher is better
        assert comp["r2"]["improved"] is True
        assert comp["r2"]["delta"] == pytest.approx(0.05)

    def test_compare_nonexistent_raises(self, registry):
        with pytest.raises(FileNotFoundError):
            registry.compare_versions("nonexistent", "1.0", "2.0")

class TestActiveVersionsFile:
    def test_active_file_created_on_promote(self, registry_with_models, tmp_path):
        registry_with_models.promote("price_model", "1.0.0")
        active_file = tmp_path / "active_versions.json"
        assert active_file.exists()
        data = json.loads(active_file.read_text())
        assert data["price_model"] == "1.0.0"
