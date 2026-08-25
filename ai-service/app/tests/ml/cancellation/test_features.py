"""Tests for BookingFeatureExtractor."""

from datetime import date, timedelta

import pytest

from app.ml.cancellation.features import BookingFeatureExtractor

@pytest.fixture
def extractor():
    return BookingFeatureExtractor()

class TestBookingFeatureExtractor:
    def test_extract_basic(self, extractor):
        booking = {
            "checkInDate": (date.today() + timedelta(days=10)).isoformat(),
            "checkOutDate": (date.today() + timedelta(days=15)).isoformat(),
            "guests": 2,
            "totalPrice": 500,
            "propertyType": "HOUSE",
            "city": "Madrid",
        }
        features = extractor.extract(booking)

        assert features["guests"] == 2
        assert features["total_price"] == 500
        assert features["stay_duration"] == 5
        assert features["price_per_night"] == 100.0
        assert features["property_type"] == "HOUSE"
        assert features["city"] == "Madrid"
        assert features["days_until_checkin"] == 10
        assert features["is_weekend_checkin"] in (0, 1)

    def test_extract_wrapped_request(self, extractor):
        """Spring Boot sends features inside a 'features' key."""
        request = {
            "bookingId": "abc-123",
            "features": {
                "checkInDate": (date.today() + timedelta(days=5)).isoformat(),
                "checkOutDate": (date.today() + timedelta(days=8)).isoformat(),
                "guests": 3,
                "totalPrice": 300,
            },
        }
        features = extractor.extract(request)
        assert features["guests"] == 3
        assert features["total_price"] == 300
        assert features["stay_duration"] == 3

    def test_extract_missing_dates(self, extractor):
        booking = {"guests": 2, "totalPrice": 100}
        features = extractor.extract(booking)
        assert features["days_until_checkin"] == 0
        assert features["stay_duration"] == 1
        assert features["price_per_night"] == 100.0

    def test_extract_invalid_date_format(self, extractor):
        booking = {
            "checkInDate": "not-a-date",
            "checkOutDate": "also-not-a-date",
            "guests": 1,
            "totalPrice": 50,
        }
        features = extractor.extract(booking)
        assert features["stay_duration"] == 1
        assert features["days_until_checkin"] == 0

    def test_extract_weekend_checkin(self, extractor):
        # Find next Saturday
        today = date.today()
        days_until_saturday = (5 - today.weekday()) % 7
        if days_until_saturday == 0:
            days_until_saturday = 7
        saturday = today + timedelta(days=days_until_saturday)

        booking = {
            "checkInDate": saturday.isoformat(),
            "checkOutDate": (saturday + timedelta(days=2)).isoformat(),
            "guests": 1,
            "totalPrice": 100,
        }
        features = extractor.extract(booking)
        assert features["is_weekend_checkin"] == 1

    def test_extract_weekday_checkin(self, extractor):
        # Find next Monday
        today = date.today()
        days_until_monday = (7 - today.weekday()) % 7
        if days_until_monday == 0:
            days_until_monday = 7
        monday = today + timedelta(days=days_until_monday)

        booking = {
            "checkInDate": monday.isoformat(),
            "checkOutDate": (monday + timedelta(days=3)).isoformat(),
            "guests": 1,
            "totalPrice": 100,
        }
        features = extractor.extract(booking)
        assert features["is_weekend_checkin"] == 0

    def test_extract_minimal_nights(self, extractor):
        """Same check-in and check-out should yield 1 night."""
        today = date.today().isoformat()
        booking = {
            "checkInDate": today,
            "checkOutDate": today,
            "guests": 1,
            "totalPrice": 100,
        }
        features = extractor.extract(booking)
        assert features["stay_duration"] == 1

    def test_extract_batch(self, extractor):
        bookings = [
            {"guests": 1, "totalPrice": 100},
            {"guests": 2, "totalPrice": 200},
        ]
        results = extractor.extract_batch(bookings)
        assert len(results) == 2
        assert results[0]["guests"] == 1
        assert results[1]["guests"] == 2

    def test_get_feature_columns(self, extractor):
        cols = extractor.get_feature_columns()
        assert "days_until_checkin" in cols
        assert "property_type" in cols
        assert "city" in cols
        assert "is_weekend_checkin" in cols
        assert len(cols) == 8