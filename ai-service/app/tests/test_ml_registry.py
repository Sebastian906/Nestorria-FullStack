"""Tests for ModelRegistry."""

import json

import joblib
import pytest
from sklearn.linear_model import LinearRegression

from app.ml.models.registry import ModelRegistry

@pytest.fixture
def registry(tmp_path):
    """Registry using a temporary directory."""
    return ModelRegistry(artifacts_path=str(tmp_path))

class TestRegistrySaveAndLoad:
    def test_save_and_load(self, registry):
        model = LinearRegression()
        registry.save_model(model, "test_model", "1.0.0", {"mse": 0.5})

        loaded = registry.load_model("test_model", "1.0.0")
        assert isinstance(loaded, LinearRegression)

    def test_model_equality(self, registry):
        model = LinearRegression()
        model.fit([[1], [2], [3]], [1, 2, 3])
        registry.save_model(model, "test_model", "1.0.0", {"mse": 0.0})

        loaded = registry.load_model("test_model", "1.0.0")
        assert loaded.coef_.tolist() == model.coef_.tolist()
        assert loaded.intercept_ == pytest.approx(model.intercept_)

class TestRegistryMetadata:
    def test_metadata_created(self, registry, tmp_path):
        model = LinearRegression()
        registry.save_model(model, "test_model", "1.0.0", {"mse": 0.5})

        metadata_path = tmp_path / "test_model" / "1.0.0" / "metadata.json"
        assert metadata_path.exists()

        metadata = json.loads(metadata_path.read_text())
        assert metadata["name"] == "test_model"
        assert metadata["version"] == "1.0.0"
        assert metadata["metrics"]["mse"] == 0.5
        assert "date" in metadata
        assert "features" in metadata

    def test_metadata_with_features(self, registry):
        model = LinearRegression()
        registry.save_model(
            model, "test_model", "1.0.0", {"mse": 0.5},
            features=["price", "bedrooms"],
        )

        latest = registry.get_latest("test_model")
        assert latest["features"] == ["price", "bedrooms"]

class TestRegistryListAndLatest:
    def test_list_models(self, registry):
        model = LinearRegression()
        registry.save_model(model, "model_a", "1.0.0", {"mse": 0.1})
        registry.save_model(model, "model_b", "1.0.0", {"mse": 0.2})

        models = registry.list_models()
        assert len(models) == 2
        names = [m["name"] for m in models]
        assert "model_a" in names
        assert "model_b" in names

    def test_list_multiple_versions(self, registry):
        model = LinearRegression()
        registry.save_model(model, "test_model", "1.0.0", {"mse": 0.5})
        registry.save_model(model, "test_model", "2.0.0", {"mse": 0.3})

        models = registry.list_models()
        assert len(models) == 2

    def test_get_latest(self, registry):
        model = LinearRegression()
        registry.save_model(model, "test_model", "1.0.0", {"mse": 0.5})
        registry.save_model(model, "test_model", "2.0.0", {"mse": 0.3})

        latest = registry.get_latest("test_model")
        assert latest["version"] == "2.0.0"

    def test_get_latest_nonexistent(self, registry):
        result = registry.get_latest("nonexistent")
        assert result is None

class TestRegistrySecurity:
    def test_traversal_in_name_rejected(self, registry):
        model = LinearRegression()
        with pytest.raises(ValueError, match="Invalid model name"):
            registry.save_model(model, "../etc/passwd", "1.0.0", {})

    def test_traversal_in_version_rejected(self, registry):
        model = LinearRegression()
        with pytest.raises(ValueError, match="Invalid model version"):
            registry.save_model(model, "model", "../1.0.0", {})

    def test_slash_in_name_rejected(self, registry):
        model = LinearRegression()
        with pytest.raises(ValueError, match="Invalid model name"):
            registry.save_model(model, "model/sub", "1.0.0", {})

    def test_load_nonexistent_raises(self, registry):
        with pytest.raises(FileNotFoundError):
            registry.load_model("nonexistent", "1.0.0")