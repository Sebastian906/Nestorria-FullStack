# Changelog — Nestorria

Formato: Keep a Changelog. Todo lo anterior a `[Unreleased]` está
`⚠️ NO VERIFICADO EN REPO` (el repo no documenta releases previas).

## [Unreleased]

### Added

- Documentación en `docs/`: `USER_MANUAL.md`, `MANUAL_DE_USUARIO.md`,
  `TECHNICAL_MANUAL.md`, `MANUAL_TECNICO.md`, `ARCHITECTURE.md`, `ARQUITECTURA.md`,
  `FAQ.md`, `GLOSSARY.md`, `SECURITY.md` y este `CHANGELOG.md`.
- Corpus RAG de cliente: `MANUAL_DE_USUARIO.md`, `USER_MANUAL.md`, `FAQ.md`, `GLOSSARY.md`
  ingeribles vía `POST /rag/ingest` (`ai-service/app/routers/rag.py`).

### Stack verificado en esta revisión

- `server/`: Spring Boot 4.1.0, Java 21 (`server/pom.xml:8,30`).
- `ai-service/`: Python ≥3.12, FastAPI, torch CPU 2.14.0 (`pyproject.toml`, `requirements.txt`).
- `frontend/`: React 19.2.5, Vite 8.0.10 (`frontend/package.json`).
- `admin/`: Vue 3.5.34, Vite 8.0.12 (`admin/package.json`).
- DB: `pgvector/pgvector:pg16`, contenedor `nestorria-primary` (`docker-compose.yml:7`).
- Puertos: server `:4000`, ai-service `:8000` (`application.properties:1`, `ai-service/Dockerfile`).

### Known limitations (por diseño)

- `DB_REPLICA_URL=""` en Docker desactiva la réplica (`docker-compose.yml:36`).
- Webhook Stripe 503 hasta configurar `STRIPE_WEBHOOK_SECRET`.
- Modelos ML experimentales (~85 registros).
