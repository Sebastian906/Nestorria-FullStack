# Nestorria — Software Architecture (EN)

> Repo-first. Verified against repo state 2026-09-12. Every claim cites a real path. Not found = `NOT VERIFIED IN REPO`. Diagrams are linked below but generated separately (pending).

## 1. Architecture style

**Monorepo with 4 independent deployables + 1 data plane + observability sidecar.** No microservices mesh, no K8s (mem0 decision: Railway + Netlify only).

| Container | Stack (source) | Port | Role |
|---|---|---|---|
| `frontend/` | React 19.2.5, Vite 8.0.10 (`frontend/package.json`, `vite.config.ts:23`) | 5173 | Public app: search, map, booking, contracts, payments, AI widget |
| `admin/` | Vue 3.5.34, Vite 8.0.12 (`admin/package.json`, `vite.config.js:12`) | 5174 | Back-office: properties, categories, reports, AI/MLOps |
| `server/` | Spring Boot 4.1.0, Java 21 (`server/pom.xml:8,30`) | 4000 (`application.properties:1`) | API gateway: auth, validation, orchestration, SSE proxy, Stripe, STOMP |
| `ai-service/` | FastAPI, Python ≥3.12, 1 uvicorn worker (`pyproject.toml:6`, `Dockerfile:47`) | 8000 | ML/RAG/LLM/translate/visual (experimental) |
| `postgres-primary` | `pgvector/pgvector:pg16` (`docker-compose.yml:7`) | 5432 | Primary DB + `rag_documents` vectors |
| Observability | Prometheus v3.1.0, Grafana 11.3.0, Loki 3.4.2, Alloy v1.8.3 (`docker-compose.yml`) | 9090/3000/3100 | Metrics, dashboards, JSON logs |

Flow: `frontend`/`admin` → `server` → (`postgres-primary` + `ai-service` → Groq `openai/gpt-oss-20b`). `server/Dockerfile` keeps `chmod +x mvnw`, non-root `spring`. `DB_REPLICA_URL=""` in Docker intentionally disables the replica. Stripe webhook returns 503 until the secret is set (deferred by design).

Backend is a **modular monolith**: bounded contexts in `server/src/main/java/com/nestorria/server/modules/` — `agency, booking, contract, favorite, notification, payment, properties, report, review, user` — plus cross-cutting `common/` — `ai, algorithm, cache, config, datasource, event, exception, i18n, mail, outbox, persistence, util, websocket`. AI plane routers (`ai-service/app/routers/`): `admin, cancellation, chat, health, metrics, price, rag, recommendation, translate, visual`.

## 2. SOLID principles in use (with evidence)

- **SRP — Single Responsibility.** Each module owns one aggregate (`booking/BookingService`, `payment/PaymentService`, `contract/ContractService`); layering is strict Controller → Service → Repository + DTOs (`modules/booking/dto/`, `modules/payment/dto/`). Cross-cutting lives in `common/` (e.g. `common/outbox/`, `common/datasource/`), never inside business services. In ai-service each router owns one capability (`routers/chat.py`, `routers/rag.py`, `routers/price.py`).
- **OCP — Open/Closed.** `common/outbox/EventHandler<T>` (`getEventType`/`getPayloadClass`/`handle`) lets new event types ship as new `handler/` classes without touching `OutboxEventProcessor` (which resolves `List<EventHandler<?>>` into a map). Read path extends via `@ReadFromReplica` + `ReadReplicaAspect` without editing services. `AiFallbackHandler` adds per-operation fallbacks (health/recommendations/predictions/chat) without editing `AiServiceClient`. Visual search is gated by `visual_search_enabled` (`app/config.py:57`, `app/main.py:168-178`).
- **LSP — Liskov Substitution.** `DynamicDataSource extends AbstractRoutingDataSource` (`common/datasource/DynamicDataSource.java:5`) is substitutable wherever a `DataSource` is expected; lookup key comes from `DataSourceContextHolder`. Any `EventHandler<?>` implementation plugs into `OutboxEventProcessor`'s `handlerMap` with no behavioral break.
- **ISP — Interface Segregation.** `EventHandler<T>` exposes only 3 methods; repositories are per-aggregate (`BookingRepository`, `InvoiceRepository`, `FavoriteRepository`); ai-service routers expose narrow surfaces per capability instead of one fat API. No god-interface observed.
- **DIP — Dependency Inversion.** Constructor injection everywhere: `AiServiceClient(properties, fallbackHandler)`, `AiFallbackHandler(recommendationService)`, `OutboxEventProcessor(outboxRepository, deadLetterRepository, handlers, …)`. Code depends on abstractions (`RestClient`, `EventHandler`, `Executor`, `TransactionTemplate`) with config injected via `@Value` / `pydantic-settings` (`get_settings()` cached with `lru_cache` in `app/config.py:132`).

## 3. Design patterns found (with evidence)

| Pattern | Where | Notes |
|---|---|---|
| API Gateway | `common/ai/AiServiceClient.java` (237 lines, `@Service`) | Single choke point server→ai-service; sync RestClient (3s/5s) + streaming RestClient (30s) |
| Circuit Breaker + Retry + Fallback | `AiServiceClient` annotations + `application.properties:126-144`, `AiFallbackHandler.java` | CB 50% / 30s open / 3 half-open; retry 3 attempts exp-backoff; per-operation fallback (health→degraded, recommendations→local algorithm, chat→unavailability msg) |
| Transactional Outbox + DLQ + Scheduler worker | `common/outbox/` (`OutboxEvent`, `OutboxEventProcessor`, `DeadLetterEvent`, `OutboxMetrics`) + `modules/payment/InvoiceTransactionWorker.java` | `@Scheduled` batch poll (`batch-size:100`), backoff ≤60s, poison → DLQ |
| Read/Write routing (AOP) | `common/datasource/` (`DynamicDataSource`, `ReadReplicaAspect`, `ReadFromReplica`, `DataSourceContextHolder`) | Method annotation switches lookup key; off in Docker via `DB_REPLICA_URL=""` |
| Cache-aside | `spring.cache.type=caffeine` (`application.properties:93-94`) + `common/cache/CategoryMemoizationCache.java` | Single-instance only; needs Redis/event-bus before horizontal scale |
| Observer / Pub-Sub | STOMP bell (`NotificationBell.tsx`, `spring-boot-starter-websocket`) + `EventHandler<?>` dispatch | Booking/payment/contract events fan out to handlers and UI |
| Strategy | `AiFallbackHandler` per-operation methods; recommendation weights (`config.py:47-49`) | Algorithm selectable without caller changes |
| Singleton (container + cache) | Spring `@Service/@Component` singletons; `get_settings()` `@lru_cache` (`app/config.py:132`) | One settings instance; one client per container |
| Facade | `AiServiceClient` over `/ml|/rag|/dl|/ai` endpoints | Callers see chat/predict/health, not HTTP details |
| Repository + DTO | `*Repository.java` + `dto/` per module | Persistence decoupled from API contracts |
| Middleware chain | `app/main.py:75-136` (rate-limit → API-key → CORS → audit → request-id → metrics) | Ordered; request-id innermost so audit can read it |
| Adapter (partial) | `modules/payment/StripeClient.java` (name only, body not read) | Verify before citing as pattern |

## 4. Cross-cutting decisions

- **Resilience:** CB + retry + fallback + `max-concurrent-streams=10` + 60s stream timeout. 4xx ignored by retry, 5xx retried.
- **Consistency:** JPA `ddl-auto=update`, Hikari pool 10/3, slow-query log >1000ms. Outbox gives at-least-once delivery for side effects.
- **Security:** Clerk JWT (resource server) + internal `ApiKeyAuthMiddleware` (health excluded); `NOT VERIFIED`: exact scope/role enforcement per endpoint.
- **Observability:** Actuator `health,info` + Micrometer/Prometheus; JSON logs (`LogstashEncoder`, `service=nestorria-server`, MDC `instanceId`/`requestId`); query Loki `{container="nestorria-server"}`.
- **Constraints honored:** never `8080`; never plain `postgres`; `@clerk/localizations ^4.16.0`; `allowBuilds: @clerk/shared, msw`; mandatory i18n; ML experimental (~85 rows).

## 5. Diagrams (pending — generated next)

Linked sources (to be created under `docs/diagrams/`):

- `diagrams/architecture-stack.mmd` — containers + ports + data flow
- `diagrams/erd-business.mmd` — JPA aggregates and relations
- `diagrams/bpmn-processes.mmd` — booking → payment → contract → notify (BPMN-lite)
- `diagrams/uml-use-cases.mmd` — actors ↔ use cases
- `diagrams/flow-main.mmd` — search → book → pay → notify
- `diagrams/sequence-booking.mmd` — React → Spring → DB → STOMP
- `diagrams/sequence-ai-chat.mmd` — SSE via `AiChatStreamingService` → FastAPI → Groq → tools
- `diagrams/sequence-payment.mmd` — Stripe + webhook + invoicing
- `diagrams/sequence-contract.mmd` — multi-role signing
- `diagrams/sequence-rag-ingest.mmd` — chunk → embed → pgvector

## 6. Strengths / risks

Strong: layered rate limits, auditable side effects, cheap single-instance ops, graceful AI/Stripe degradation. Risks: Caffeine not shared across instances; replica off in Docker; torch CPU RAM; 7d metrics retention; ML not production-grade. Validate with load tests before horizontal scaling.
