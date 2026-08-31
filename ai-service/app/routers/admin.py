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