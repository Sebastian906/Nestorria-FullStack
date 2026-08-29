"""Review query tools."""

from __future__ import annotations

from app.rag.tools.base import BaseTool

class GetReviewAverage(BaseTool):
    """Get average review rating for a property."""
    name = "get_review_average"
    description = "Get the average review rating and total review count for a specific property on Nestorria."
    parameters = {
        "type": "object",
        "properties": {
            "propertyId": {
                "type": "string",
                "description": "The UUID of the property to get reviews for",
            },
        },
        "required": ["propertyId"],
    }