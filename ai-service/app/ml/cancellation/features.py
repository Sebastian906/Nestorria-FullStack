"""Feature extraction for booking cancellation prediction.

Adapts the real Booking entity fields to ML features.
Input format matches the features map sent by Spring Boot AiPredictionRequest.
"""

from datetime import date, datetime

import structlog

logger = structlog.get_logger("ai-service.ml.cancellation")

class BookingFeatureExtractor:
    """Extract ML features from booking data dict.

    Input format matches the JSON features map sent by Spring Boot:
    {
        "checkInDate": "2026-09-15",
        "checkOutDate": "2026-09-20",
        "guests": 2,
        "totalPrice": 500,
        "propertyType": "HOUSE",
        "city": "Madrid"
    }
    """

    def extract(self, booking_data: dict) -> dict:
        """Extract features from a single booking dict.

        Accepts either the raw features map or the full Spring Boot request
        (with "features" key).
        """
        # Support both raw features map and wrapped request
        features = booking_data.get("features", booking_data)

        check_in = features.get("checkInDate")
        check_out = features.get("checkOutDate")
        total_price = features.get("totalPrice", 0)
        guests = features.get("guests", 1)

        stay_duration = self._stay_duration(check_in, check_out)
        price_per_night = total_price / max(stay_duration, 1)

        return {
            "days_until_checkin": self._days_until(check_in),
            "stay_duration": stay_duration,
            "guests": guests,
            "total_price": total_price,
            "price_per_night": round(price_per_night, 2),
            "property_type": features.get("propertyType", "UNKNOWN"),
            "city": features.get("city", "UNKNOWN"),
            "is_weekend_checkin": self._is_weekend(check_in),
        }

    def extract_batch(self, bookings: list[dict]) -> list[dict]:
        """Extract features from multiple bookings."""
        return [self.extract(b) for b in bookings]

    @staticmethod
    def _days_until(check_in_date: str | None) -> int:
        """Days from today until check-in."""
        if not check_in_date:
            return 0
        try:
            target = date.fromisoformat(check_in_date)
            delta = (target - date.today()).days
            return max(delta, 0)
        except (ValueError, TypeError):
            return 0

    @staticmethod
    def _stay_duration(check_in: str | None, check_out: str | None) -> int:
        """Number of nights between check-in and check-out."""
        if not check_in or not check_out:
            return 1
        try:
            ci = date.fromisoformat(check_in)
            co = date.fromisoformat(check_out)
            nights = (co - ci).days
            return max(nights, 1)
        except (ValueError, TypeError):
            return 1

    @staticmethod
    def _is_weekend(check_in_date: str | None) -> int:
        """1 if check-in is Saturday or Sunday, 0 otherwise."""
        if not check_in_date:
            return 0
        try:
            target = date.fromisoformat(check_in_date)
            return 1 if target.weekday() >= 5 else 0
        except (ValueError, TypeError):
            return 0

    @staticmethod
    def _price_per_night(booking_data: dict) -> float:
        """Calculate price per night."""
        total = booking_data.get("totalPrice", 0)
        nights = BookingFeatureExtractor._stay_duration(
            booking_data.get("checkInDate"),
            booking_data.get("checkOutDate"),
        )
        return total / max(nights, 1)

    @staticmethod
    def get_numeric_columns() -> list[str]:
        """Columns that need numeric preprocessing."""
        return [
            "days_until_checkin", "stay_duration", "guests",
            "total_price", "price_per_night",
        ]

    @staticmethod
    def get_categorical_columns() -> list[str]:
        """Columns that need categorical encoding."""
        return ["property_type", "city"]

    @staticmethod
    def get_feature_columns() -> list[str]:
        """All feature column names in order."""
        return (
            BookingFeatureExtractor.get_numeric_columns()
            + ["is_weekend_checkin"]
            + BookingFeatureExtractor.get_categorical_columns()
        )