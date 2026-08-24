"""Pydantic schemas for price prediction API."""

from pydantic import BaseModel, Field

class PricePredictionRequest(BaseModel):
    """Request schema matching Spring Boot AiPredictionRequest.features."""
    area: int = Field(..., gt=0, description="Property area in m²")
    propertyType: str = Field(..., description="HOUSE, APARTMENT, VILLA, etc.")
    city: str = Field(..., description="City name")
    bedrooms: int = Field(..., ge=0)
    bathrooms: int = Field(..., ge=0)
    latitude: float | None = Field(None, ge=-90, le=90)
    longitude: float | None = Field(None, ge=-180, le=180)
    amenities: list[str] = Field(default_factory=list)

class PricePredictionResponse(BaseModel):
    """Response schema matching Spring Boot AiPredictionResponse."""
    predicted_price: float
    confidence: float = Field(..., ge=0.0, le=1.0)
    model: str
    features_used: int