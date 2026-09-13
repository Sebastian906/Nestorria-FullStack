# Nestorria — Arquitectura de software (ES)

> Repo-first. Verificado contra el repo el 2026-09-12. Cada afirmación cita una ruta real. No hallado = `⚠️ NO VERIFICADO EN REPO`. Los diagramas se enlazan abajo pero se generan después (pendientes).

## 1. Estilo arquitectónico

**Monorepo con 4 desplegables independientes + 1 plano de datos + sidecar de observabilidad.** Sin malla de microservicios ni K8s (decisión registrada: solo Railway + Netlify).

| Contenedor | Stack (fuente) | Puerto | Rol |
|---|---|---|---|
| `frontend/` | React 19.2.5, Vite 8.0.10 (`frontend/package.json`, `vite.config.ts:23`) | 5173 | App pública: búsqueda, mapa, reserva, contratos, pagos, widget IA |
| `admin/` | Vue 3.5.34, Vite 8.0.12 (`admin/package.json`, `vite.config.js:12`) | 5174 | Back-office: propiedades, categorías, reportes, IA/MLOps |
| `server/` | Spring Boot 4.1.0, Java 21 (`server/pom.xml:8,30`) | 4000 (`application.properties:1`) | Gateway API: auth, validación, orquestación, proxy SSE, Stripe, STOMP |
| `ai-service/` | FastAPI, Python ≥3.12, 1 worker uvicorn (`pyproject.toml:6`, `Dockerfile:47`) | 8000 | ML/RAG/LLM/traducción/visual (experimental) |
| `postgres-primary` | `pgvector/pgvector:pg16` (`docker-compose.yml:7`) | 5432 | DB primaria + vectores `rag_documents` |
| Observabilidad | Prometheus v3.1.0, Grafana 11.3.0, Loki 3.4.2, Alloy v1.8.3 (`docker-compose.yml`) | 9090/3000/3100 | Métricas, dashboards, logs JSON |

Flujo: `frontend`/`admin` → `server` → (`postgres-primary` + `ai-service` → Groq `openai/gpt-oss-20b`). `server/Dockerfile` conserva `chmod +x mvnw`, usuario non-root `spring`. `DB_REPLICA_URL=""` en Docker desactiva la réplica a propósito. El webhook Stripe devuelve 503 hasta configurar el secreto (diferido consciente).

El backend es un **monolito modular**: contextos acotados en `server/src/main/java/com/nestorria/server/modules/` — `agency, booking, contract, favorite, notification, payment, properties, report, review, user` — más transversales en `common/` — `ai, algorithm, cache, config, datasource, event, exception, i18n, mail, outbox, persistence, util, websocket`. Routers del plano IA (`ai-service/app/routers/`): `admin, cancellation, chat, health, metrics, price, rag, recommendation, translate, visual`.

## 2. Principios SOLID aplicados (con evidencia)

- **SRP — Responsabilidad única.** Cada módulo posee un agregado (`booking/BookingService`, `payment/PaymentService`, `contract/ContractService`); capas estrictas Controller → Service → Repository + DTOs (`modules/booking/dto/`, `modules/payment/dto/`). Lo transversal vive en `common/` (p. ej. `common/outbox/`, `common/datasource/`), nunca dentro de servicios de negocio. En ai-service cada router posee una capacidad (`routers/chat.py`, `routers/rag.py`, `routers/price.py`).
- **OCP — Abierto/Cerrado.** La interfaz `common/outbox/EventHandler<T>` (`getEventType`/`getPayloadClass`/`handle`) permite añadir tipos de evento como nuevas clases en `handler/` sin tocar `OutboxEventProcessor` (resuelve `List<EventHandler<?>>` en un mapa). La lectura se extiende con `@ReadFromReplica` + `ReadReplicaAspect` sin editar servicios. `AiFallbackHandler` añade fallbacks por operación (health/recomendaciones/predicciones/chat) sin editar `AiServiceClient`. La búsqueda visual se activa por flag `visual_search_enabled` (`app/config.py:57`, `app/main.py:168-178`).
- **LSP — Sustitución de Liskov.** `DynamicDataSource extends AbstractRoutingDataSource` (`common/datasource/DynamicDataSource.java:5`) es sustituible donde se espere un `DataSource`; la clave viene de `DataSourceContextHolder`. Cualquier implementación de `EventHandler<?>` encaja en el `handlerMap` de `OutboxEventProcessor` sin romper comportamiento.
- **ISP — Segregación de interfaces.** `EventHandler<T>` expone solo 3 métodos; repositorios por agregado (`BookingRepository`, `InvoiceRepository`, `FavoriteRepository`); routers con superficies estrechas por capacidad en vez de una API gorda. Sin god-interface observada.
- **DIP — Inversión de dependencias.** Inyección por constructor en todo: `AiServiceClient(properties, fallbackHandler)`, `AiFallbackHandler(recommendationService)`, `OutboxEventProcessor(outboxRepository, deadLetterRepository, handlers, …)`. El código depende de abstracciones (`RestClient`, `EventHandler`, `Executor`, `TransactionTemplate`) con config inyectada vía `@Value` / `pydantic-settings` (`get_settings()` con `lru_cache` en `app/config.py:132`).

## 3. Patrones de diseño encontrados (con evidencia)

| Patrón | Dónde | Notas |
|---|---|---|
| API Gateway | `common/ai/AiServiceClient.java` (237 líneas, `@Service`) | Único punto server→ai-service; RestClient síncrono (3s/5s) + RestClient streaming (30s) |
| Circuit Breaker + Retry + Fallback | Anotaciones en `AiServiceClient` + `application.properties:126-144`, `AiFallbackHandler.java` | CB 50% / 30s abierto / 3 half-open; retry 3 intentos con backoff; fallback por operación (health→degraded, recomendaciones→algoritmo local, chat→mensaje de indisponibilidad) |
| Outbox transaccional + DLQ + worker planificado | `common/outbox/` (`OutboxEvent`, `OutboxEventProcessor`, `DeadLetterEvent`, `OutboxMetrics`) + `modules/payment/InvoiceTransactionWorker.java` | Poll por lotes `@Scheduled` (`batch-size:100`), backoff ≤60s, veneno → DLQ |
| Routing lectura/escritura (AOP) | `common/datasource/` (`DynamicDataSource`, `ReadReplicaAspect`, `ReadFromReplica`, `DataSourceContextHolder`) | La anotación cambia la clave; apagado en Docker con `DB_REPLICA_URL=""` |
| Cache-aside | `spring.cache.type=caffeine` (`application.properties:93-94`) + `common/cache/CategoryMemoizationCache.java` | Solo una instancia; exige Redis/bus antes de escalar horizontal |
| Observer / Pub-Sub | Campana STOMP (`NotificationBell.tsx`, `spring-boot-starter-websocket`) + despacho `EventHandler<?>` | Eventos de reserva/pago/contrato hacia handlers y UI |
| Strategy | Métodos por operación en `AiFallbackHandler`; pesos de recomendación (`config.py:47-49`) | Algoritmo intercambiable sin cambiar llamadores |
| Singleton (contenedor + caché) | Singletons `@Service/@Component` de Spring; `get_settings()` `@lru_cache` (`app/config.py:132`) | Una instancia de settings; un cliente por contenedor |
| Facade | `AiServiceClient` sobre endpoints `/ml|/rag|/dl|/ai` | El llamador ve chat/predecir/health, no detalles HTTP |
| Repository + DTO | `*Repository.java` + `dto/` por módulo | Persistencia desacoplada del contrato API |
| Cadena de middlewares | `app/main.py:75-136` (rate-limit → API-key → CORS → audit → request-id → metrics) | Ordenada; request-id lo más interno para que audit lo lea |
| ⚠️ Adapter (parcial) | `modules/payment/StripeClient.java` (solo nombre, cuerpo no leído) | Verificar antes de citarlo como patrón |

## 4. Decisiones transversales

- **Resiliencia:** CB + retry + fallback + `max-concurrent-streams=10` + timeout stream 60s. 4xx no reintenta, 5xx sí.
- **Consistencia:** JPA `ddl-auto=update`, pool Hikari 10/3, log de queries lentas >1000ms. Outbox da al-menos-una-vez en efectos laterales.
- **Seguridad:** JWT Clerk (resource server) + `ApiKeyAuthMiddleware` interno (health excluido); `⚠️ NO VERIFICADO`: autorización fina por endpoint.
- **Observabilidad:** Actuator `health,info` + Micrometer/Prometheus; logs JSON (`LogstashEncoder`, `service=nestorria-server`, MDC `instanceId`/`requestId`); en Loki `{container="nestorria-server"}`.
- **Restricciones respetadas:** nunca `8080`; nunca `postgres` plano; `@clerk/localizations ^4.16.0`; `allowBuilds: @clerk/shared, msw`; i18n obligatoria; ML experimental (~85 registros).

## 5. Diagramas (pendientes — se generan después)

Fuentes enlazadas (a crear bajo `docs/diagrams/`):

- `diagrams/architecture-stack.mmd` — contenedores + puertos + flujo de datos
- `diagrams/erd-business.mmd` — agregados JPA y relaciones
- `diagrams/bpmn-processes.mmd` — reserva → pago → contrato → notificación (BPMN-lite)
- `diagrams/uml-use-cases.mmd` — actores ↔ casos de uso
- `diagrams/flow-main.mmd` — buscar → reservar → pagar → notificar
- `diagrams/sequence-booking.mmd` — React → Spring → DB → STOMP
- `diagrams/sequence-ai-chat.mmd` — SSE vía `AiChatStreamingService` → FastAPI → Groq → tools
- `diagrams/sequence-payment.mmd` — Stripe + webhook + facturación
- `diagrams/sequence-contract.mmd` — firma multi-rol
- `diagrams/sequence-rag-ingest.mmd` — chunk → embed → pgvector

## 6. Fortalezas / riesgos

Fuerte: límites por capas, efectos auditables, operación barata en una instancia, degradación controlada de IA/Stripe. Riesgos: Caffeine no compartida entre instancias; réplica apagada en Docker; RAM de torch CPU; retención 7d; ML no productivo. Validar con pruebas de carga antes de escalar horizontalmente.
