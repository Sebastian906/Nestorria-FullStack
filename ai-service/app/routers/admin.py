from fastapi import APIRouter, UploadFile, File, HTTPException
from pydantic import BaseModel
from typing import Optional
import structlog

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
    models = [
        ModelInfo(
            name="price_prediction",
            version="1.0",
            status="active",
            metrics={"rmse": 15000, "r2": 0.85},
            last_trained=None,
        ),
        ModelInfo(
            name="cancellation_prediction",
            version="1.0",
            status="active",
            metrics={"accuracy": 0.78, "f1": 0.72},
            last_trained=None,
        ),
    ]
    return {"models": [m.model_dump() for m in models]}

@router.post("/models/{model_name}/train")
async def trigger_training(model_name: str):
    """Trigger a training job for the specified model."""
    import uuid
    job_id = f"train_{uuid.uuid4().hex[:8]}"
    logger.info("training_triggered", model=model_name, job_id=job_id)
    # Real implementation would enqueue a background job via Celery/arq
    return {"job_id": job_id, "status": "started"}

@router.get("/rag/documents")
async def list_rag_documents():
    """List all ingested RAG documents."""
    from app.rag.vector_store import PgVectorStore
    store = PgVectorStore()
    documents = await store.list_documents()
    return {"documents": documents}

@router.delete("/rag/documents/{document_id}")
async def delete_rag_document(document_id: str):
    """Delete a RAG document by ID."""
    from app.rag.vector_store import PgVectorStore
    store = PgVectorStore()
    deleted = await store.delete_document(document_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="Document not found")
    return {"deleted": True}

@router.get("/chat/metrics")
async def get_chat_metrics():
    """Get chat usage metrics."""
    return {
        "total_messages": 0,
        "messages_by_user": {},
        "average_response_time": 0.0,
        "error_rate": 0.0,
    }

@router.get("/status")
async def ai_service_status():
    """Return ai-service status including model availability."""
    return {
        "status": "ok",
        "models_loaded": ["price_prediction", "cancellation_prediction"],
        "rag_enabled": True,
        "llm_enabled": True,
    }