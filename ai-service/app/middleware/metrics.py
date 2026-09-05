"""Central Prometheus registry — baja cardinalidad."""
import re, time
from prometheus_client import Counter, Histogram
from starlette.middleware.base import BaseHTTPMiddleware
from fastapi import Request

HTTP_COUNT = Counter("http_requests_total", "Total HTTP", ["method","route","status"])
HTTP_LAT = Histogram("http_request_duration_seconds", "Latency", ["method","route"])
RATE_REJECTED = Counter("rate_limit_rejected_total", "Rate limit 429", ["scope"])
LLM_COUNT = Counter("llm_requests_total", "LLM calls", ["model","op","status"])
LLM_LAT = Histogram("llm_request_duration_seconds", "LLM latency", ["model","op"])
RAG_COUNT = Counter("rag_queries_total", "RAG queries", ["status"])

def normalize(path: str) -> str:
    path = re.sub(r"/\d+", "/{id}", path)
    if path.startswith("/ml/"): return "/ml/*"
    if path.startswith("/rag/chat"): return "/rag/chat"
    if path.startswith("/rag/"): return "/rag/*"
    if path.startswith("/dl/"): return "/dl/*"
    return path

class MetricsMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        if request.url.path == "/metrics":
            return await call_next(request)
        t0 = time.perf_counter()
        try:
            resp = await call_next(request)
            status = str(resp.status_code)
        except Exception:
            route = request.scope.get("route")
            tmpl = getattr(route, "path", None) or normalize(request.url.path) or "/unmatched"
            HTTP_COUNT.labels(request.method, tmpl, "500").inc()
            HTTP_LAT.labels(request.method, tmpl).observe(time.perf_counter()-t0)
            raise
        route = request.scope.get("route")
        tmpl = getattr(route, "path", None) or normalize(request.url.path) or "/unmatched"
        HTTP_COUNT.labels(request.method, tmpl, status).inc()
        HTTP_LAT.labels(request.method, tmpl).observe(time.perf_counter()-t0)
        return resp