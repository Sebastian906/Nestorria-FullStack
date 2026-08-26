"""Visual search endpoints.

GET  /dl/visual/similar/{propertyId} — find visually similar properties
POST /dl/visual/search               — search by uploaded image
"""

import structlog
from fastapi import APIRouter, Depends, File, HTTPException, Query, Request, UploadFile

from app.dependencies import get_config
from app.dl.image.schemas import (
    SimilarProperty,
    VisualSearchResponse,
    VisualSearchResult,
    VisualSimilarResponse,
)

logger = structlog.get_logger("ai-service.routers.visual")

router = APIRouter(prefix="/dl/visual", tags=["visual-search"])

# Singleton engine — loaded once at startup
_engine = None

def get_engine():
    """Get or initialize the visual similarity engine."""
    global _engine
    if _engine is None:
        from app.dl.image.similarity import VisualSimilarityEngine
        _engine = VisualSimilarityEngine()
    return _engine

@router.get("/similar/{property_id}", response_model=VisualSimilarResponse)
async def find_similar(
    property_id: str,
    limit: int = Query(default=5, ge=1, le=20),
    http_request: Request = None,
    engine=Depends(get_engine),
):
    """Find properties visually similar to a given property.

    Requires authentication (API key).
    """
    config = await get_config()
    api_key = http_request.headers.get("X-API-Key")
    if config.api_key and api_key != config.api_key:
        raise HTTPException(status_code=401, detail="Invalid API key")

    if not config.visual_search_enabled:
        raise HTTPException(status_code=503, detail="Visual search is disabled")

    results = engine.find_similar(property_id, limit=limit)

    return VisualSimilarResponse(
        propertyId=property_id,
        similar=[
            SimilarProperty(propertyId=r["property_id"], similarity=r["similarity"])
            for r in results
        ],
        model="resnet50",
    )

@router.post("/search", response_model=VisualSearchResponse)
async def search_by_image(
    image: UploadFile = File(...),
    limit: int = Query(default=5, ge=1, le=20),
    http_request: Request = None,
    engine=Depends(get_engine),
):
    """Search for properties visually similar to an uploaded image.

    Requires authentication (API key).
    Accepts JPEG, PNG images.
    """
    config = await get_config()
    api_key = http_request.headers.get("X-API-Key")
    if config.api_key and api_key != config.api_key:
        raise HTTPException(status_code=401, detail="Invalid API key")

    if not config.visual_search_enabled:
        raise HTTPException(status_code=503, detail="Visual search is disabled")

    # Validate file type
    if image.content_type not in ("image/jpeg", "image/png"):
        raise HTTPException(
            status_code=422,
            detail="Only JPEG and PNG images are supported",
        )

    # Read and process image
    from PIL import Image
    import io

    image_data = await image.read()
    if len(image_data) > 10 * 1024 * 1024:  # 10MB limit
        raise HTTPException(status_code=422, detail="Image too large (max 10MB)")

    try:
        pil_image = Image.open(io.BytesIO(image_data))
    except Exception:
        raise HTTPException(status_code=422, detail="Invalid image file")

    results = engine.search_by_image(pil_image, limit=limit)

    return VisualSearchResponse(
        results=[
            VisualSearchResult(propertyId=r["property_id"], similarity=r["similarity"])
            for r in results
        ],
        model="resnet50",
    )