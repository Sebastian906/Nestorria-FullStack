"""Property query tools."""

from __future__ import annotations

from app.rag.tools.base import BaseTool

class GetPropertyCount(BaseTool):
    """Get total number of properties, optionally filtered."""
    name = "get_property_count"
    description = "Get the total number of properties available on Nestorria. Can filter by city and property type."
    parameters = {
        "type": "object",
        "properties": {
            "city": {
                "type": "string",
                "description": "Filter by city name (e.g., 'Madrid', 'Barcelona')",
            },
            "propertyType": {
                "type": "string",
                "description": "Filter by property type",
                "enum": ["House", "Apartment", "Villa", "Penthouse", "Townhouse", "Commercial", "Land Plot"],
            },
        },
        "required": [],
    }

class GetAveragePrice(BaseTool):
    """Get average property price, optionally filtered."""
    name = "get_average_price"
    description = "Get the average property price on Nestorria. Can filter by city and property type."
    parameters = {
        "type": "object",
        "properties": {
            "city": {
                "type": "string",
                "description": "Filter by city name",
            },
            "propertyType": {
                "type": "string",
                "description": "Filter by property type",
                "enum": ["House", "Apartment", "Villa", "Penthouse", "Townhouse", "Commercial", "Land Plot"],
            },
        },
        "required": [],
    }

class SearchProperties(BaseTool):
    """Search properties by filters."""
    name = "search_properties"
    description = "Search for properties on Nestorria with optional filters for city, property type, and price range. Returns a list of matching properties."
    parameters = {
        "type": "object",
        "properties": {
            "city": {
                "type": "string",
                "description": "Filter by city name",
            },
            "propertyType": {
                "type": "string",
                "description": "Filter by property type",
                "enum": ["House", "Apartment", "Villa", "Penthouse", "Townhouse", "Commercial", "Land Plot"],
            },
            "minPrice": {
                "type": "integer",
                "description": "Minimum price filter",
            },
            "maxPrice": {
                "type": "integer",
                "description": "Maximum price filter",
            },
        },
        "required": [],
    }