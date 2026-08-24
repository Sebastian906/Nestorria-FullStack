"""Feature extraction for property price prediction.

Adapts the real Property entity fields to ML features.
No data leakage: price_per_sqm is NOT used as a feature.
"""

import structlog

logger = structlog.get_logger("ai-service.ml.price")

# Amenities that map to binary features
BINARY_AMENITIES = {"parking", "pool", "garden", "terrace", "gym", "elevator"}

class PropertyFeatureExtractor:
    """Extract ML features from property data dict.

    Input format matches the JSON sent by Spring Boot AiPredictionRequest:
    {
        "area": 120,
        "propertyType": "HOUSE",
        "city": "Madrid",
        "bedrooms": 3,
        "bathrooms": 2,
        "latitude": 40.4168,
        "longitude": -3.7038,
        "amenities": ["parking", "pool"]
    }
    """

    def extract(self, property_data: dict) -> dict:
        """Extract features from a single property dict.

        Returns a flat dict suitable for sklearn.
        """
        amenities = property_data.get("amenities", [])

        features = {
            "area": property_data.get("area", 0),
            "bedrooms": property_data.get("bedrooms", 0),
            "bathrooms": property_data.get("bathrooms", 0),
            "property_type": property_data.get("propertyType", "UNKNOWN"),
            "city": property_data.get("city", "UNKNOWN"),
            "latitude": property_data.get("latitude", 0.0),
            "longitude": property_data.get("longitude", 0.0),
            "amenities_count": len(amenities),
        }

        # Binary amenity flags
        amenities_lower = {a.lower() for a in amenities}
        for amenity in BINARY_AMENITIES:
            features[f"has_{amenity}"] = 1 if amenity in amenities_lower else 0

        return features

    def extract_batch(self, properties: list[dict]) -> list[dict]:
        """Extract features from multiple properties."""
        return [self.extract(p) for p in properties]

    @staticmethod
    def get_numeric_columns() -> list[str]:
        """Columns that need numeric preprocessing."""
        return [
            "area", "bedrooms", "bathrooms",
            "latitude", "longitude", "amenities_count",
        ]

    @staticmethod
    def get_categorical_columns() -> list[str]:
        """Columns that need categorical encoding."""
        return ["property_type", "city"]

    @staticmethod
    def get_feature_columns() -> list[str]:
        """All feature column names in order."""
        numeric = PropertyFeatureExtractor.get_numeric_columns()
        categorical = PropertyFeatureExtractor.get_categorical_columns()
        binary = [f"has_{a}" for a in BINARY_AMENITIES]
        return numeric + categorical + binary