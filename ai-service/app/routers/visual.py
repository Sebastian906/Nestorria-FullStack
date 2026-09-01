"""Visual search endpoints.

GET  /dl/visual/similar/{propertyId} — find visually similar properties
POST /dl/visual/search               — search by uploaded image
"""

import asyncio
import io

import structlog
from fastapi import APIRouter, Depends, File, HTTPException, Query, Request, UploadFile
from PIL import Image

from app.dl.image.schemas import (
    SimilarProperty,
    VisualSearchResponse,
    VisualSearchResult,
    VisualSimilarResponse,
)

logger = structlog.get_logger("ai-service.routers.visual")

router = APIRouter(prefix="/dl/visual", tags=["visual-search"])

_engine = None
_engine_lock = asyncio.Lock()

async def get_engine():
    """Get or initialize the visual similarity engine."""
    global _engine
    if _engine is None:
        async with _engine_lock:
            if _engine is None:
                from app.dl.image.similarity import VisualSimilarityEngine
                _engine = VisualSimilarityEngine()
    return _engine

@router.get("/similar/{property_id}", response_model=VisualSimilarResponse)
async def find_similar(
    property_id: str,
    limit: int = Query(default=5, ge=1, le=20),
    engine=Depends(get_engine),
):
    """Find properties visually similar to a given property."""
    loop = asyncio.get_event_loop()
    results = await loop.run_in_executor(
        None, engine.find_similar, property_id, limit
    )

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
    engine=Depends(get_engine),
):
    """Search for properties visually similar to an uploaded image."""

    # Validate file type
    if image.content_type not in ("image/jpeg", "image/png"):
        raise HTTPException(
            status_code=422,
            detail="Only JPEG and PNG images are supported",
        )

    # Check size before reading — UploadFile.size may be None for some clients
    if image.size is not None and image.size > 10 * 1024 * 1024:
        raise HTTPException(status_code=422, detail="Image too large (max 10MB)")

    image_data = await image.read()

    if len(image_data) > 10 * 1024 * 1024:
        raise HTTPException(status_code=422, detail="Image too large (max 10MB)")

    try:
        pil_image = Image.open(io.BytesIO(image_data))
        pil_image.load()
    except Exception as e:
        raise HTTPException(status_code=422, detail="Invalid image file") from e

    if pil_image.format not in ("JPEG", "PNG"):
        raise HTTPException(
            status_code=422,
            detail="Only JPEG and PNG images are supported",
        )

    loop = asyncio.get_event_loop()
    results = await loop.run_in_executor(
        None, engine.search_by_image, pil_image, limit
    )

    return VisualSearchResponse(
        results=[
            VisualSearchResult(propertyId=r["property_id"], similarity=r["similarity"])
            for r in results
        ],
        model="resnet50",
    )
