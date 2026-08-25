"""Feature engineering for hybrid recommendation system.

Builds property and user feature vectors for content-based scoring.
Respects the existing Property and Booking entity structures.
"""

from __future__ import annotations

import numpy as np
import structlog

logger = structlog.get_logger("ai-service.ml.recommendation")

# Property types and cities — must match Spring Boot enums
PROPERTY_TYPES = ["HOUSE", "APARTMENT", "VILLA", "CONDO", "TOWNHOUSE", "STUDIO"]
AMENITIES = ["parking", "pool", "garden", "terrace", "gym", "elevator", "wifi", "ac"]

class RecommendationFeatureBuilder:
    """Build feature vectors for properties and users.
    
    Property features:
    - Text: title + description (for TF-IDF)
    - Categorical: property_type, city (one-hot)
    - Numeric: price, area, bedrooms, bathrooms, amenities_count (normalized)
    - Geo: latitude, longitude (normalized)
    
    User features:
    - Booking history: preferred types, cities, price range
    - Favorites: category distribution
    """

    def build_property_text(self, property_data: dict) -> str:
        """Combine title and description for TF-IDF.
        
        Returns empty string if both are missing (handled by embedder).
        """
        title = property_data.get("title", "")
        description = property_data.get("description", "")
        return f"{title} {description}".strip()

    def build_property_features(self, property_data: dict) -> dict:
        """Extract property features as a flat dict.
        
        Input format matches Spring Boot PropertyResponse:
        {
            "id": "...",
            "title": "...",
            "description": "...",
            "price": 250000,
            "area": 120,
            "bedrooms": 3,
            "bathrooms": 2,
            "propertyType": "HOUSE",
            "city": "Madrid",
            "latitude": 40.4168,
            "longitude": -3.7038,
            "amenities": ["parking", "pool"]
        }
        """
        amenities = property_data.get("amenities", [])
        amenities_lower = {a.lower() for a in amenities}

        features = {
            # Numeric (will be normalized later)
            "price": float(property_data.get("price", 0) or 0),
            "area": float(property_data.get("area", 0) or 0),
            "bedrooms": float(property_data.get("bedrooms", 0) or 0),
            "bathrooms": float(property_data.get("bathrooms", 0) or 0),
            "amenities_count": float(len(amenities)),
            "latitude": float(property_data.get("latitude", 0) or 0),
            "longitude": float(property_data.get("longitude", 0) or 0),
            # Categorical (will be one-hot encoded)
            "property_type": property_data.get("propertyType", "UNKNOWN"),
            "city": property_data.get("city", "UNKNOWN"),
            # Binary amenity flags
            **{f"has_{a}": 1.0 if a in amenities_lower else 0.0 for a in AMENITIES},
        }
        return features

    def build_user_features(self, user_history: dict) -> dict:
        """Extract user preference features from booking/favorite history.
        
        Input format:
        {
            "bookings": [
                {"propertyType": "HOUSE", "city": "Madrid", "price": 250000, ...},
                ...
            ],
            "favorites": [
                {"propertyType": "APARTMENT", "city": "Barcelona", ...},
                ...
            ],
            "reviews": [
                {"rating": 4.5, ...},
                ...
            ]
        }
        """
        bookings = user_history.get("bookings", [])
        favorites = user_history.get("favorites", [])
        reviews = user_history.get("reviews", [])

        # Preferred property types (from bookings + favorites)
        all_properties = bookings + favorites
        type_counts: dict[str, int] = {}
        city_counts: dict[str, int] = {}
        prices: list[float] = []

        for p in all_properties:
            pt = p.get("propertyType", "UNKNOWN")
            type_counts[pt] = type_counts.get(pt, 0) + 1
            city = p.get("city", "UNKNOWN")
            city_counts[city] = city_counts.get(city, 0) + 1
            price = p.get("price")
            if price and price > 0:
                prices.append(float(price))

        # Normalize type preferences
        total = max(len(all_properties), 1)
        type_prefs = {f"type_{t}": count / total for t, count in type_counts.items()}

        # Normalize city preferences
        city_prefs = {f"city_{c}": count / total for c, count in city_counts.items()}

        # Price range statistics
        avg_price = float(np.mean(prices)) if prices else 0.0
        std_price = float(np.std(prices)) if len(prices) > 1 else 0.0

        # Review behavior
        avg_rating = (
            float(np.mean([r.get("rating", 0) for r in reviews]))
            if reviews else 0.0
        )

        return {
            **type_prefs,
            **city_prefs,
            "avg_price": avg_price,
            "std_price": std_price,
            "booking_count": float(len(bookings)),
            "favorite_count": float(len(favorites)),
            "review_count": float(len(reviews)),
            "avg_rating": avg_rating,
        }

    @staticmethod
    def get_numeric_columns() -> list[str]:
        """Columns that need normalization."""
        return [
            "price", "area", "bedrooms", "bathrooms",
            "amenities_count", "latitude", "longitude",
        ]

    @staticmethod
    def get_categorical_columns() -> list[str]:
        """Columns that need one-hot encoding."""
        return ["property_type", "city"]

    @staticmethod
    def get_amenity_columns() -> list[str]:
        """Binary amenity columns."""
        return [f"has_{a}" for a in AMENITIES]