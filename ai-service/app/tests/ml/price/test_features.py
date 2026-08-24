"""Tests for PropertyFeatureExtractor."""

import pytest

from app.ml.price.features import BINARY_AMENITIES, PropertyFeatureExtractor

@pytest.fixture
def extractor():
    return PropertyFeatureExtractor()

@pytest.fixture
def sample_property():
    return {
        "area": 120,
        "propertyType": "HOUSE",
        "city": "Madrid",
        "bedrooms": 3,
        "bathrooms": 2,
        "latitude": 40.4168,
        "longitude": -3.7038,
        "amenities": ["parking", "pool", "garden"],
    }

class TestFeatureExtraction:
    def test_extracts_all_features(self, extractor, sample_property):
        features = extractor.extract(sample_property)
        assert features["area"] == 120
        assert features["bedrooms"] == 3
        assert features["bathrooms"] == 2
        assert features["property_type"] == "HOUSE"
        assert features["city"] == "Madrid"
        assert features["latitude"] == 40.4168
        assert features["longitude"] == -3.7038
        assert features["amenities_count"] == 3

    def test_binary_amenities(self, extractor, sample_property):
        features = extractor.extract(sample_property)
        assert features["has_parking"] == 1
        assert features["has_pool"] == 1
        assert features["has_garden"] == 1
        assert features["has_terrace"] == 0

    def test_empty_amenities(self, extractor):
        features = extractor.extract({"area": 50, "amenities": []})
        assert features["amenities_count"] == 0
        for amenity in BINARY_AMENITIES:
            assert features[f"has_{amenity}"] == 0

    def test_missing_fields_defaults(self, extractor):
        features = extractor.extract({})
        assert features["area"] == 0
        assert features["bedrooms"] == 0
        assert features["property_type"] == "UNKNOWN"
        assert features["city"] == "UNKNOWN"

    def test_amenities_case_insensitive(self, extractor):
        features = extractor.extract({"amenities": ["Parking", "POOL"]})
        assert features["has_parking"] == 1
        assert features["has_pool"] == 1

    def test_extract_batch(self, extractor):
        props = [{"area": 50}, {"area": 100}]
        results = extractor.extract_batch(props)
        assert len(results) == 2
        assert results[0]["area"] == 50
        assert results[1]["area"] == 100

    def test_no_data_leakage(self, extractor, sample_property):
        """price_per_sqm must NOT be in features."""
        features = extractor.extract(sample_property)
        assert "price_per_sqm" not in features
        assert "price" not in features

    def test_feature_columns_complete(self, extractor):
        cols = extractor.get_feature_columns()
        assert "area" in cols
        assert "property_type" in cols
        assert "has_parking" in cols
        assert len(cols) > 10