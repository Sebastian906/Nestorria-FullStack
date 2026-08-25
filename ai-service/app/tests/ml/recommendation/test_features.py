"""Tests for RecommendationFeatureBuilder."""

from app.ml.recommendation.features import RecommendationFeatureBuilder

class TestPropertyFeatures:
    def setup_method(self):
        self.builder = RecommendationFeatureBuilder()

    def test_build_property_text(self):
        text = self.builder.build_property_text({
            "title": "Beautiful apartment",
            "description": "Near the beach with pool",
        })
        assert "Beautiful apartment" in text
        assert "Near the beach" in text

    def test_build_property_text_empty(self):
        text = self.builder.build_property_text({})
        assert text == ""

    def test_build_property_features(self):
        features = self.builder.build_property_features({
            "id": "1",
            "title": "Test",
            "price": 250000,
            "area": 120,
            "bedrooms": 3,
            "bathrooms": 2,
            "propertyType": "HOUSE",
            "city": "Madrid",
            "latitude": 40.4168,
            "longitude": -3.7038,
            "amenities": ["parking", "pool"],
        })
        assert features["price"] == 250000.0
        assert features["area"] == 120.0
        assert features["property_type"] == "HOUSE"
        assert features["city"] == "Madrid"
        assert features["has_parking"] == 1.0
        assert features["has_pool"] == 1.0
        assert features["has_gym"] == 0.0

    def test_build_property_features_missing_fields(self):
        features = self.builder.build_property_features({})
        assert features["price"] == 0.0
        assert features["property_type"] == "UNKNOWN"

    def test_build_user_features(self):
        user_history = {
            "bookings": [
                {"propertyType": "HOUSE", "city": "Madrid", "price": 250000},
                {"propertyType": "APARTMENT", "city": "Madrid", "price": 150000},
            ],
            "favorites": [
                {"propertyType": "HOUSE", "city": "Barcelona", "price": 300000},
            ],
            "reviews": [{"rating": 4.5}],
        }
        features = self.builder.build_user_features(user_history)
        assert features["booking_count"] == 2.0
        assert features["favorite_count"] == 1.0
        assert features["type_HOUSE"] == 2.0 / 3.0
        assert features["city_Madrid"] == 2.0 / 3.0
        assert features["avg_rating"] == 4.5

    def test_build_user_features_empty(self):
        features = self.builder.build_user_features({})
        assert features["booking_count"] == 0.0
        assert features["avg_price"] == 0.0