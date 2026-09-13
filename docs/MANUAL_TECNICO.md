# Manual técnico Nestorria (ES)

> Repo-first. Cada versión cita su archivo fuente. No hallado = `NO VERIFICADO EN REPO`. Puertos: server `:4000`, ai-service `:8000` — nunca `8080`.

## 1. Backend — `server/` (Spring Boot)

Fuente: `server/pom.xml`, `server/src/main/resources/application.properties`, `server/Dockerfile`, `server/src/main/resources/logback-spring.xml`.

| Tecnología | Versión exacta | Módulo(s) | Dónde se usa (ruta/archivo) | Por qué se eligió |
|---|---|---|---|---|
| Spring Boot / Java | 4.1.0 / 21 | server | `pom.xml:8,30`, `Dockerfile:2 eclipse-temurin:21` | Gateway API, JPA, validación, WebMVC |
| OAuth2 resource server (Clerk JWT) | gestionada por Boot | server | `pom.xml:80-83`, `application.properties:38-39` | Auth sin estado, roles `USER`/`AGENCY_OWNER` |
| springdoc-openapi | 3.0.3 | server | `pom.xml:87` | Swagger UI |
| Resilience4j (CB + Retry) | 2.4.0 | server→ai-service | `pom.xml:33,155-173`, `application.properties:126-144` | CB umbral 50% / 30s abierto; retry 3 intentos con backoff |
| Bucket4j | 8.10.1 | server | `pom.xml:106`, `application.properties:77-88` | Límites por capa: lectura 100/min, escritura 10/min, reviews 5/min, IA 30/min |
| Caffeine (una instancia) | gestionada por Boot | server | `pom.xml:112-115`, `application.properties:93-94` | Cache-aside `maximumSize=1000,expireAfterWrite=5m` |
| POI / iText | 5.4.0 / 8.0.3 | report | `pom.xml:31-32` | Reportes Excel / PDF |
| stripe-java | 28.4.0 | payment | `pom.xml:101`, `application.properties:68-70` | Pagos; webhook 503 hasta configurar secreto (diferido) |
| Cloudinary | 2.2.0 (http5) | properties | `pom.xml:92` | Subida de imágenes |
| WebSocket/STOMP + mail (Brevo SMTP) | gestionadas por Boot | notification | `pom.xml:94-97,135-138`, `application.properties:52-58` | Campana + email |
| LogstashEncoder | 7.4 | server | `pom.xml:177`, `logback-spring.xml:15-18` | Logs JSON, `service=nestorria-server`, MDC `instanceId`/`requestId` (perfil docker) |
| Micrometer + Actuator | gestionadas por Boot | server | `pom.xml:126-134`, `application.properties:96-100` | `health,info` + métricas Prometheus |
| HikariCP | gestionada por Boot | server | `application.properties:22-28` | Pool 10/3, detección de leaks 60s |

Módulos de negocio (verificado `server/.../modules/`): `agency, booking, contract, favorite, notification, payment, properties, report, review, user`. Transversales (`common/`): `ai, datasource, outbox, cache, websocket, i18n, mail`.

Comandos:

```bash
cd server
./mvnw test            # el CI ejecuta antes chmod +x mvnw
./mvnw package -DskipTests
docker build -t nestorria-server .
```

## 2. AI service — `ai-service/` (FastAPI)

Fuente: `ai-service/requirements.txt`, `ai-service/pyproject.toml`, `ai-service/app/config.py`, `ai-service/app/main.py`, `ai-service/Dockerfile`.

| Tecnología | Versión exacta | Dónde se usa | Por qué |
|---|---|---|---|
| Python | >=3.12 (`python:3.12-slim`) | `pyproject.toml:6`, `Dockerfile:2` | Runtime |
| FastAPI / uvicorn (1 worker) | >=0.115<1.0 / >=0.30<1.0 | `requirements.txt:1-3`, `Dockerfile:47` | API; 1 worker evita duplicar torch en RAM |
| pydantic-settings / structlog | >=2<3 / >=24<26.1 | `requirements.txt:4-5` | Config + logs JSON |
| scikit-learn / imbalanced-learn / pandas / numpy / scipy | >=1.5<2 / >=0.12<1 / >=2.2<3.0.5 / >=2<3 / >=1.11<2 | `requirements.txt:8-12` | Precio / cancelación / recomendación — **experimental (~85 registros)** |
| torch / torchvision CPU + Pillow | 2.14.0+cpu / 0.29.0+cpu | `requirements.txt:16-17` | Búsqueda visual, desactivada por defecto (`visual_search_enabled=False`, `config.py:57`) |
| sentence-transformers (`all-MiniLM-L6-v2`, 384d, chunk 500/50, topK 5) | >=3<6.0.1 | `requirements.txt:23`, `config.py:64-72` | RAG en `rag_documents` (pgvector) |
| Groq (`openai/gpt-oss-20b`) + httpx | >=0.13<1.7 | `requirements.txt:25,27`, `config.py:75-80` | Chat en streaming |
| psycopg2-binary / prometheus_client / pytest | >=2.9<3 / >=0.20<1 / >=8<9.1.1 | `requirements.txt:21,29-34` | DB, métricas, tests |

Routers verificados (`app/routers/`): `admin, cancellation, chat, health, metrics, price, rag, recommendation, translate, visual`. Middlewares (`app/main.py:75-114`): chat 20/usuario/h, ingesta RAG 10/min, ML 30/min, admin 10/min.

Comandos:

```bash
cd ai-service
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 1
pytest
```

## 3. Frontend — `frontend/` (React)

Fuente: `frontend/package.json`, `frontend/vite.config.ts`, `frontend/pnpm-workspace.yaml`.

React 19.2.5, Vite 8.0.10, TS ~6.0.2, Tailwind 4.2.4, `@clerk/react ^6.7.1` + `@clerk/localizations ^4.16.0` (fijar — v6 no existe), `react-router-dom ^7.15.1`, `i18next ^26.4.2`, Leaflet `^1.9.4`, STOMP `^7.3.0`, Axios `^1.18.0`, Vitest `^4.1.7` + RTL + MSW. Puerto `:5173` (`vite.config.ts:23`). Mantener `allowBuilds: @clerk/shared, msw` (`pnpm-workspace.yaml`).

```bash
cd frontend
pnpm install --frozen-lockfile && pnpm dev   # :5173
pnpm test:run
pnpm build
```

## 4. Admin — `admin/` (Vue)

Fuente: `admin/package.json`, `admin/vite.config.js`. Vue 3.5.34 (Composition API), Vite 8.0.12, `vue-i18n ^11.4.10`, `@clerk/vue ^2.3.3`, STOMP `^7.3.0`. Puerto `:5174`.

```bash
cd admin
pnpm install --frozen-lockfile && pnpm dev   # :5174
pnpm build
```

## 5. Infra (`docker-compose.yml`, `devops/`)

`pgvector/pgvector:pg16` (`nestorria-primary`, nunca `postgres` plano), Prometheus `v3.1.0` (retención 7d), Grafana `11.3.0`, Loki `3.4.2`, Alloy `v1.8.3`, socket-proxy `0.3.1`. `DB_REPLICA_URL=""` en Docker es intencional (desactiva réplica). Docker multi-stage, usuarios non-root, healthchecks (`/actuator/health`, `/health`). CI: 4 workflows path-scoped (`.github/workflows/ci-server|frontend|admin|ai.yml`).

```bash
docker compose up -d postgres-primary
docker compose up -d --build
```

## 6. Convenciones

i18n obligatoria (cero strings hardcodeados en UI), logs JSON — consultar Loki con `{container="nestorria-server"}`, `instanceId` en MDC. Versiones Node/pnpm: `NO VERIFICADO EN REPO` (sin `.nvmrc` ni campo `packageManager`).
