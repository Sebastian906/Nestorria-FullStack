"""Tests for Pydantic schemas."""

import pytest
from pydantic import ValidationError

from app.ml.price.schemas import PricePredictionRequest, PricePredictionResponse

class TestPricePredictionRequest:
    def test_valid_request(self):
        req = PricePredictionRequest(
            area=120, propertyType="HOUSE", city="Madrid",
            bedrooms=3, bathrooms=2,
        )
        assert req.area == 120
        assert req.amenities == []

    def test_with_amenities(self):
        req = PricePredictionRequest(
            area=80, propertyType="APARTMENT", city="Barcelona",
            bedrooms=2, bathrooms=1, amenities=["parking", "pool"],
        )
        assert len(req.amenities) == 2

    def test_optional_location(self):
        req = PricePredictionRequest(
            area=100, propertyType="VILLA", city="Valencia",
            bedrooms=4, bathrooms=3,
        )
        assert req.latitude is None
        assert req.longitude is None

    def test_area_must_be_positive(self):
        with pytest.raises(ValidationError):
            PricePredictionRequest(
                area=0, propertyType="HOUSE", city="Madrid",
                bedrooms=3, bathrooms=2,
            )

class TestPricePredictionResponse:
    def test_valid_response(self):
        resp = PricePredictionResponse(
            predicted_price=285000.0,
            confidence=0.82,
            model="price_rf_v1",
            features_used=12,
        )
        assert resp.predicted_price == 285000.0

    def test_confidence_bounds(self):
        with pytest.raises(ValidationError):
            PricePredictionResponse(
                predicted_price=100.0,
                confidence=1.5,  # > 1.0
                model="test",
                features_used=5,
            )