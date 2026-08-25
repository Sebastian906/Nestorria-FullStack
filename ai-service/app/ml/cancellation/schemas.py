"""Pydantic schemas for cancellation prediction API."""

from pydantic import BaseModel, ConfigDict, Field


class CancellationPredictionRequest(BaseModel):
    """Request schema matching Spring Boot AiPredictionRequest for cancellation.

    The features map contains booking details sent by Spring Boot.
    """
    model_config = ConfigDict(json_schema_extra={
        "examples": [
            {
                "bookingId": "abc-123",
                "features": {
                    "checkInDate": "2026-09-15",
                    "checkOutDate": "2026-09-20",
                    "guests": 2,
                    "totalPrice": 500,
                    "propertyType": "HOUSE",
                    "city": "Madrid",
                },
            }
        ]
    })

    bookingId: str = Field(..., description="Booking ID")
    features: dict = Field(default_factory=dict, description="Booking features from Spring Boot")

class CancellationPredictionResponse(BaseModel):
    """Response schema matching Spring Boot AiPredictionResponse."""
    cancellation_probability: float = Field(..., ge=0.0, le=1.0)
    risk_level: str = Field(..., pattern="^(low|medium|high)$")
    model: str
    features_used: int