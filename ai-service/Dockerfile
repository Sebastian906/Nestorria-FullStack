# ---- Stage 1: Build dependencies ----
FROM python:3.12-slim AS builder

WORKDIR /app

# Install build dependencies if needed (e.g., for psycopg2)
RUN apt-get update && \
    apt-get install -y --no-install-recommends gcc libpq-dev && \
    rm -rf /var/lib/apt/lists/*

# Install Python dependencies (una sola vez, con cache de capa)
ENV PIP_NO_CACHE_DIR=1 PYTHONDONTWRITEBYTECODE=1
ARG PIP_EXTRA_INDEX_URL=https://download.pytorch.org/whl/cpu
COPY requirements.txt .
RUN pip install --prefix=/install -r requirements.txt \
    && rm -rf /root/.cache/pip /tmp/* \
    && pip list | grep -Ei "torch|nvidia|triton|cuda" || true

# ---- Stage 2: Runtime ----
FROM python:3.12-slim

WORKDIR /app

# Install runtime dependencies only
RUN apt-get update && \
    apt-get install -y --no-install-recommends libpq5 && \
    rm -rf /var/lib/apt/lists/*

# Copy installed dependencies from builder
COPY --from=builder /install /usr/local

# Copy application code
COPY app/ ./app/

# Create non-root user
RUN groupadd -r aiservice && useradd -r -g aiservice aiservice
USER aiservice

# Expose port
EXPOSE 8000

# Health check (respeta $PORT de la plataforma)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD python -c "import os,urllib.request; urllib.request.urlopen('http://localhost:'+os.environ.get('PORT','8000')+'/health')" || exit 1

# Run with uvicorn (respeta $PORT, 1 worker para no duplicar torch en RAM)
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000} --workers 1"]