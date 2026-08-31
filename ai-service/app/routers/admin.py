from fastapi import APIRouter, UploadFile, File, HTTPException
from pydantic import BaseModel
from typing import Optional
import structlog
import uuid
from datetime import datetime, timezone

from app.config import get_settings
from app.rag.vector_store import PgVectorStore
from app.ml.models.registry import ModelRegistry

router = APIRouter(prefix="/admin", tags=["admin"])
logger = structlog.get_logger()

# Modelos de respuesta
class ModelInfo(BaseModel):
    name: str
    version: str
    status: str
    metrics: dict
    last_trained: Optional[str] = None

class TrainingResponse(BaseModel):
    job_id: str
    status: str

class DocumentInfo(BaseModel):
    id: str
    name: str
    chunks: int
    version: str

class ChatMetricsResponse(BaseModel):
    total_messages: int
    messages_by_user: dict
    average_response_time: float
    error_rate: float

class VersionInfo(BaseModel):
    version: str
    metrics: dict
    date: Optional[str] = None
    features: list[str] = []

class CompareResponse(BaseModel):
    model: str
    version_1: str
    version_2: str
    date_1: Optional[str] = None
    date_2: Optional[str] = None
    metrics_comparison: dict

class PromoteResponse(BaseModel):
    name: str
    previous_version: Optional[str]
    new_version: str

class RollbackResponse(BaseModel):
    name: str
    previous_version: Optional[str]
    new_version: str

# Endpoints
@router.get("/models")
async def list_models():
    """List available ML models and their status."""
    settings = get_settings()
    registry = ModelRegistry(artifacts_path=settings.artifacts_path)
    models_data = registry.list_models()
    
    models = []
    for model_data in models_data:
        models.append(ModelInfo(
            name=model_data.get("name", "unknown"),
            version=model_data.get("version", "1.0"),
            status="active",
            metrics=model_data.get("metrics", {}),
            last_trained=model_data.get("date"),
        ))
    
    return {"models": [m.model_dump() for m in models]}

@router.post("/models/{model_name}/train")
async def trigger_training(model_name: str):
    """Trigger a training job for the specified model."""
    job_id = f"train_{uuid.uuid4().hex[:8]}"
    logger.info("training_triggered", model=model_name, job_id=job_id)
    # Real implementation would enqueue a background job via Celery/arq
    return {"job_id": job_id, "status": "started"}

@router.get("/rag/documents")
async def list_rag_documents():
    """List all ingested RAG documents."""
    store = PgVectorStore()
    try:
        documents = await store.list_documents()
        return {"documents": documents}
    except Exception as e:
        logger.error("failed_to_list_documents", error=str(e))
        return {"documents": []}

@router.delete("/rag/documents/{document_id}")
async def delete_rag_document(document_id: str):
    """Delete a RAG document by ID."""
    store = PgVectorStore()
    try:
        deleted = await store.delete_document(document_id)
        if not deleted:
            raise HTTPException(status_code=404, detail="Document not found")
        return {"deleted": True}
    except HTTPException:
        raise
    except Exception as e:
        logger.error("failed_to_delete_document", document_id=document_id, error=str(e))
        raise HTTPException(status_code=500, detail="Failed to delete document")

@router.get("/chat/metrics")
async def get_chat_metrics():
    """Get chat usage metrics."""
    # Real implementation would query from metrics store/cache
    return {
        "total_messages": 0,
        "messages_by_user": {},
        "average_response_time": 0.0,
        "error_rate": 0.0,
    }

@router.get("/status")
async def ai_service_status():
    """Return ai-service status including model availability."""
    settings = get_settings()
    registry = ModelRegistry(artifacts_path=settings.artifacts_path)
    models = registry.list_models()
    models_loaded = [m.get("name") for m in models if m.get("name")]
    
    return {
        "status": "ok",
        "models_loaded": models_loaded,
        "rag_enabled": settings.database_url is not None,
        "llm_enabled": bool(settings.llm_api_key),
    }

@router.get("/models/{model_name}/versions")
async def list_model_versions(model_name: str):
    """List all versions of a specific model."""
    settings = get_settings()
    registry = ModelRegistry(artifacts_path=settings.artifacts_path)
    versions = registry.list_versions(model_name)
    if not versions:
        raise HTTPException(status_code=404, detail=f"Model '{model_name}' not found")
    return {
        "model": model_name,
        "versions": [
            VersionInfo(
                version=v.get("version", ""),
                metrics=v.get("metrics", {}),
                date=v.get("date"),
                features=v.get("features", []),
            ).model_dump()
            for v in versions
        ],
    }

@router.get("/models/{model_name}/active")
async def get_active_version(model_name: str):
    """Get the active (production) version of a model."""
    settings = get_settings()
    registry = ModelRegistry(artifacts_path=settings.artifacts_path)
    active = registry.get_active(model_name)
    if not active:
        raise HTTPException(status_code=404, detail=f"Model '{model_name}' not found")
    return active

@router.post("/models/{model_name}/promote/{version}")
async def promote_model(model_name: str, version: str):
    """Promote a model version to production."""
    settings = get_settings()
    registry = ModelRegistry(artifacts_path=settings.artifacts_path)
    try:
        result = registry.promote(model_name, version)
        return PromoteResponse(**result).model_dump()
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))

@router.post("/models/{model_name}/rollback/{version}")
async def rollback_model(model_name: str, version: str):
    """Rollback to a previous model version."""
    settings = get_settings()
    registry = ModelRegistry(artifacts_path=settings.artifacts_path)
    try:
        result = registry.rollback(model_name, version)
        return RollbackResponse(**result).model_dump()
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))

@router.get("/models/{model_name}/compare")
async def compare_versions(model_name: str, v1: str, v2: str):
    """Compare metrics between two model versions."""
    settings = get_settings()
    registry = ModelRegistry(artifacts_path=settings.artifacts_path)
    try:
        result = registry.compare_versions(model_name, v1, v2)
        return CompareResponse(**result).model_dump()
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))