# Nestorria — Plataforma FullStack Inmobiliaria

> Monorepo con 4 módulos independientes: gateway/herramientas Spring Boot, servicio IA FastAPI, portal público React, panel admin Vue. PostgreSQL + pgvector, JWT Clerk, Stripe, Cloudinary, Prometheus/Grafana/Loki/Alloy.

## 1. Qué es Nestorria
Plataforma de compra/venta/alquiler: búsqueda + exploración en mapa, reservas con validación de disponibilidad, pagos Stripe, firma digital de contratos, chatbot IA RAG (streaming SSE con fallback), ML de precio/cancelación/recomendación (experimental, ~85 registros), operación admin + dashboard MLOps, i18n EN/ES.

## 2. Estructura del monorepo
| Ruta | Runtime | Responsabilidad |
|---|---|---|
| `server/` | Spring Boot 4.1.0, Java 21, Maven | API REST, JWT Clerk, 10 dominios, Outbox+DLQ, ruteo lectura/escritura, proxy SSE, Bucket4j, Stripe/Cloudinary/SMTP |
| `ai-service/` | FastAPI, Python 3.12 | `/health /chat /rag /price /cancellation /recommend /visual /translate /metrics`, pipeline RAG, LLM Groq, ML/DL experimental |
| `frontend/` | React 19 + TS + Vite 8 + Tailwind 4 | Portal público, mapas Leaflet, ChatWidget SSE, i18next |
| `admin/` | Vue 3 + Vite 8 + Tailwind 4 | Panel operativo, vue-i18n, vistas MLOps |
| `docker-compose.yml` | Docker | postgres-primary, server, ai-service, prometheus, grafana, loki, socket-proxy, alloy |
| `.github/workflows/` | GitHub Actions | `ci-server.yml`, `ci-ai.yml`, `ci-frontend.yml`, `ci-admin.yml` (por rutas) |
| `devops/` | Config | prometheus, grafana, loki, alloy |
| `docs/` | Docs ES | Espejo en español de los docs EN de raíz |

## 3. Responsabilidad por subproyecto
- **server**: `modules/{agency,booking,contract,favorite,notification,payment,properties,report,review,user}`, `common/{ai,datasource,event,mail,cache,outbox,websocket}`. `/api/*`; `AiController` proxifica `/api/ai/*` hacia ai-service.
- **ai-service**: `app/routers/*`, `app/rag/{chunker,embedder,retriever,vector_store,generator,guardrails}`, `app/ml/{price,cancellation,recommendation}`, `app/dl/*`, `app/mlops/*`.
- **frontend**: `src/pages/{Home,Listing,PropertyDetail,MapExplorer,Compare,Agencies,MyBookings,MyHistory,MyReviews,ContractDetail,Guides,Contact}`, `src/components/chat/*`, `src/hooks/useChat`, `src/services/*`.
- **admin**: `src/pages/{Dashboard,ListProperties,AddProperty,Categories,Reports,AiDashboard,MlopsDash}`, `src/services/{aiService,http}`.

## 4. Stack por módulo
| Módulo | Versiones clave (verificadas) |
|---|---|
| server | Boot 4.1.0, Java 21, oauth2-resource-server (Clerk), springdoc 3.0.3, cloudinary 2.2.0, stripe 28.4.0, bucket4j 8.10.1, caffeine, resilience4j 2.4.0, POI 5.4.0, iText 8.0.3, H2 (test) |
| ai-service | Python 3.12 (Docker), fastapi>=0.115, sklearn>=1.5, torch==2.14.0+cpu, sentence-transformers>=3.0, groq>=0.13, prometheus_client>=0.20, pytest>=8 |
| frontend | react 19.2.5, vite 8.0.10, @clerk/react 6.7.1 + localizations 4.16.0, stompjs 7.3.0, tailwind 4.2.4, i18next 26.4.2, leaflet 1.9.4, router 7.15.1, vitest 4.1.7, msw 2.14.6 |
| admin | vue 3.5.34, vite 8.0.12, @clerk/vue 2.3.3, vue-i18n 11.4.10, router 5.1.0 |
| infra | pgvector/pgvector:pg16:5432, prometheus:v3.1.0:9090, grafana:11.3.0:3000, loki:3.4.2:3100, alloy:v1.8.3 |

## 5. Inicio rápido
### Prerrequisitos
Docker + Compose, Node 22 + pnpm 9, JDK 21, Python 3.12 (solo nativo IA). Puertos libres: 5432, 4000, 8000, 9090, 3000, 3100.

### Variables (solo nombres — secretos en `.env` ignorados por git)
Raíz: `POSTGRES_DB/USER/PASSWORD`, `GRAFANA_ADMIN_*`. `server/.env`: `DB_URI, DB_USERNAME, DB_PASSWORD, CLERK_ISSUER_URI, CLERK_JWKS_URL, CLDN_*, SMTP_*, SENDER_EMAIL, STRIPE_*, AI_SERVICE_URL, AI_SERVICE_API_KEY, CORS_ORIGINS, PORT`. `ai-service/.env`: `DATABASE_URL, SPRING_BOOT_BASE_URL, GROQ_API_KEY, AI_SERVICE_API_KEY`. Frontends: `VITE_*`.

### Local con Docker (backends) + frontends nativos
```bash
cp .env.example .env          # completar secretos
docker compose up -d postgres-primary
docker compose up -d server ai-service
docker compose up -d prometheus grafana loki alloy socket-proxy
curl http://localhost:4000/actuator/health
curl http://localhost:8000/health
``` 

terminales 2-3 (hot-reload fuera de Docker por diseño)
```bash
cd frontend && pnpm install && pnpm dev
cd admin && pnpm install && pnpm dev
Producción (decisión vigente: Railway + Netlify)
server + ai-service → Railway (servicios separados, dominios propios, plugin Postgres). frontend + admin → Netlify. Webhook Stripe se configura post-deploy (hoy modo 503 deshabilitado sin secreto).
```

## 6. Mapa de docs
- docs/USER_MANUAL.md — flujos portal + admin.
- docs/TECHNICAL_MANUAL.md — versiones, infra, CI/CD.
- docs/ARCHITECTURE.md — patrones, snippets, diagramas Mermaid.
- docs/*-ES.md — espejos en español.