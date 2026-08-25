"""Pydantic schemas for recommendation scoring API."""

from pydantic import BaseModel, Field

class RecommendationContext(BaseModel):
    """Optional context for recommendation scoring."""
    checkInDate: str | None = None
    guests: int | None = Field(None, ge=1)

class RecommendationRequest(BaseModel):
    """Request schema for hybrid recommendation scoring.
    
    Matches the contract expected by Spring Boot AiServiceClient.
    """
    userId: str = Field(..., min_length=1)
    candidatePropertyIds: list[str] = Field(..., min_length=1)
    context: RecommendationContext = Field(default_factory=RecommendationContext)

class ScoreBreakdown(BaseModel):
    """Individual score components for a property."""
    graph: float = Field(..., ge=0.0, le=1.0)
    content: float = Field(..., ge=0.0, le=1.0)
    collab: float = Field(..., ge=0.0, le=1.0)

class PropertyRanking(BaseModel):
    """Ranked property with score breakdown."""
    propertyId: str
    score: float = Field(..., ge=0.0, le=1.0)
    breakdown: ScoreBreakdown

class RecommendationResponse(BaseModel):
    """Response schema for recommendation scoring."""
    rankings: list[PropertyRanking]
    model: str = Field(default="hybrid_v1")
    variant: str = Field(default="control")