# Nestorria — Real Estate FullStack Platform

> Monorepo with 4 independent modules: Spring Boot gateway/tools, FastAPI AI service, React public portal, Vue admin panel. PostgreSQL + pgvector, Clerk JWT, Stripe, Cloudinary, Prometheus/Grafana/Loki/Alloy.

## 1. What is Nestorria
Buy/sell/rent property platform: search + map exploration, bookings with availability validation, Stripe payments, digital contract signature, AI RAG chatbot (SSE streaming with fallback), price/cancellation/recommendation ML (experimental, ~85 records), admin operations + MLOps dashboard, i18n EN/ES.

## 2. Monorepo layout
| Path | Runtime | Responsibility |
|---|---|---|
| `server/` | Spring Boot 4.1.0, Java 21, Maven | REST API, Clerk JWT, 10 domains, Outbox+DLQ, read/write routing, SSE proxy, Bucket4j, Stripe/Cloudinary/SMTP |
| `ai-service/` | FastAPI, Python 3.12 | `/health /chat /rag /price /cancellation /recommend /visual /translate /metrics`, RAG pipeline, Groq LLM, experimental ML/DL |
| `frontend/` | React 19 + TS + Vite 8 + Tailwind 4 | Public portal, Leaflet maps, ChatWidget SSE, i18next |
| `admin/` | Vue 3 + Vite 8 + Tailwind 4 | Operations panel, vue-i18n, MLOps views |
| `docker-compose.yml` | Docker | postgres-primary, server, ai-service, prometheus, grafana, loki, socket-proxy, alloy |
| `.github/workflows/` | GitHub Actions | `ci-server.yml`, `ci-ai.yml`, `ci-frontend.yml`, `ci-admin.yml` (path-scoped) |
| `devops/` | Config | prometheus, grafana provisioning/dashboards, loki, alloy |
| `docs/` | Docs ES | Spanish mirror of root EN docs |

## 3. Subproject responsibilities
- **server**: `modules/{agency,booking,contract,favorite,notification,payment,properties,report,review,user}`, `common/{ai,datasource,event,mail,cache,outbox,websocket}`. Controllers expose `/api/*`; `AiController` proxies `/api/ai/*` to ai-service.
- **ai-service**: `app/routers/*`, `app/rag/{chunker,embedder,retriever,vector_store,generator,guardrails}`, `app/ml/{price,cancellation,recommendation}`, `app/dl/*` (visual similarity), `app/mlops/*`.
- **frontend**: `src/pages/{Home,Listing,PropertyDetail,MapExplorer,Compare,Agencies,MyBookings,MyHistory,MyReviews,ContractDetail,Guides,Contact}`, `src/components/chat/{ChatWidget,ChatMessage,ChatInput,ChatSources}`, `src/hooks/useChat`, `src/services/*`.
- **admin**: `src/pages/{Dashboard,ListProperties,AddProperty,Categories,Reports,AiDashboard,MlopsDash}`, `src/services/{aiService,http}`.

## 4. Stack per module
| Module | Key versions (verified) |
|---|---|
| server | Boot 4.1.0, Java 21, oauth2-resource-server (Clerk), springdoc 3.0.3, cloudinary-http5 2.2.0, stripe-java 28.4.0, bucket4j-core 8.10.1, caffeine, resilience4j 2.4.0, POI 5.4.0, iText 8.0.3, H2 (test) |
| ai-service | Python 3.12 (Docker), fastapi>=0.115, sklearn>=1.5, torch==2.14.0+cpu, sentence-transformers>=3.0, groq>=0.13, prometheus_client>=0.20, pytest>=8 |
| frontend | react 19.2.5, vite 8.0.10, @clerk/react 6.7.1 + localizations 4.16.0, stompjs 7.3.0, tailwind 4.2.4, i18next 26.4.2, leaflet 1.9.4, router 7.15.1, vitest 4.1.7, msw 2.14.6 |
| admin | vue 3.5.34, vite 8.0.12, @clerk/vue 2.3.3, vue-i18n 11.4.10, router 5.1.0 |
| infra | pgvector/pgvector:pg16:5432, prom/prometheus:v3.1.0:9090, grafana/grafana:11.3.0:3000, grafana/loki:3.4.2:3100, grafana/alloy:v1.8.3 |

## 5. Quickstart
### Prerequisites
Docker + Compose, Node 22 + pnpm 9, JDK 21, Python 3.12 (only for native ai runs). Ports free: 5432, 4000, 8000, 9090, 3000, 3100.

### Env vars (names only — secrets in gitignored `.env`)
Root: `POSTGRES_DB/USER/PASSWORD`, `GRAFANA_ADMIN_*`. `server/.env`: `DB_URI, DB_USERNAME, DB_PASSWORD, CLERK_ISSUER_URI, CLERK_JWKS_URL, CLDN_*, SMTP_*, SENDER_EMAIL, STRIPE_API_KEY, STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET, AI_SERVICE_URL, AI_SERVICE_API_KEY, CORS_ORIGINS, PORT`. `ai-service/.env`: `DATABASE_URL, SPRING_BOOT_BASE_URL, GROQ_API_KEY, AI_SERVICE_API_KEY`. Frontends: `VITE_*` (Clerk publishable key, API base URLs).

### Local with Docker (backends) + native frontends
```bash
cp .env.example .env          # fill secrets
docker compose up -d postgres-primary
docker compose up -d server ai-service
docker compose up -d prometheus grafana loki alloy socket-proxy
curl http://localhost:4000/actuator/health
curl http://localhost:8000/health
```

terminals 2-3 (hot-reload outside Docker by design)
```bash
cd frontend && pnpm install && pnpm dev   # http://localhost:5173
cd admin && pnpm install && pnpm dev      # http://localhost:5174 (check vite config)
Production (current decision: Railway + Netlify)
server + ai-service → Railway (separate services, own domains, Postgres plugin). frontend + admin → Netlify. Stripe webhook configured post-deploy (currently 503-disabled mode without secret).
```

## 6. Docs map
- docs/USER_MANUAL.md — portal + admin flows.
- docs/TECHNICAL_MANUAL.md — versions, infra, CI/CD.
- docs/ARCHITECTURE.md — patterns, snippets, Mermaid diagrams.
- docs/*-ES.md — Spanish mirrors.