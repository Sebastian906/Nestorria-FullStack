"""Pydantic schemas for visual search API responses."""

from pydantic import BaseModel, Field

class SimilarProperty(BaseModel):
    """A property similar to the query."""
    property_id: str = Field(..., alias="propertyId")
    similarity: float = Field(..., ge=0.0, le=1.0)

class VisualSimilarResponse(BaseModel):
    """Response for GET /dl/visual/similar/{propertyId}."""
    property_id: str = Field(..., alias="propertyId")
    similar: list[SimilarProperty]
    model: str = "resnet50"

class VisualSearchResult(BaseModel):
    """A search result from image-based query."""
    property_id: str = Field(..., alias="propertyId")
    similarity: float = Field(..., ge=0.0, le=1.0)

class VisualSearchResponse(BaseModel):
    """Response for POST /dl/visual/search."""
    results: list[VisualSearchResult]
    model: str = "resnet50"