# Multi-Instance Deployment (Horizontal Scaling)

## Current state (verified against the code)

| Area | Multi-instance safe | Details |
|---|---|---|
| Authentication | Yes | Clerk JWT, OAuth2 Resource Server, no server-side state |
| Booking/invoice locks | Yes | `PESSIMISTIC_WRITE` at PostgreSQL level |
| File uploads | Yes | External Cloudinary storage |
| Read replica routing | Yes | `DynamicDataSource`, per-instance ThreadLocal |
| Cache | Per instance | Local Caffeine; eviction does not propagate |
| Rate limiting | Per instance | In-memory Bucket4j buckets |
| WebSocket | Per instance | In-memory STOMP broker (`/topic`) |
| Outbox processing | Safe | Atomic `claimEvent` prevents duplicate processing across instances |

## Implemented hardening

1. **Outbox atomic claim.** `OutboxEventRepository.claimEvent` transitions
   `PENDING -> PROCESSING` with a guarded `UPDATE ... WHERE status = 'PENDING'`.
   Only one instance wins per event; losers skip it. Portable across H2 and
   PostgreSQL. `FOR UPDATE SKIP LOCKED` is a future throughput optimization
   (PostgreSQL only, requires Testcontainers for tests).
2. **Instance identity.** `InstanceIdProvider` resolves `app.instance-id`
   once per process as `APP_INSTANCE_ID` > `HOSTNAME` (Docker/K8s) > random
   UUID (local), and the same value reaches `GET /api/health/`
   (`instanceId`), request-thread logs (`InstanceIdMdcFilter` puts it in the
   MDC), and async/scheduled logs (`MdcTaskDecorator` propagates it to every
   executor and to the `@Scheduled` scheduler). `logging.pattern.level`
   includes `%X{instanceId}`.
3. **Graceful shutdown.** `server.shutdown=graceful` plus a 30s
   timeout-per-shutdown-phase so rolling deploys drain in-flight requests.
   Executors already use `waitForTasksToCompleteOnShutdown=true`.

## Decisions per area

- **Cache.** Switch by configuration, not code: `spring.cache.type=redis`
  plus `spring.data.redis.*` once Redis exists. Keep `CacheConfig` free of
  custom `CacheManager` beans. Validate serialization per cache before
  enabling (a global `GenericJackson2JsonRedisSerializer` is fragile with
  complex types).
- **WebSocket.** Either accept per-instance delivery or adopt a shared broker
  (Redis pub/sub or an external STOMP broker). Sticky sessions do NOT fix
  push notifications: they originate server-side from the outbox handler,
  not from the client's request.
- **Load balancing.** Inbound only, in infrastructure (ALB/Nginx/Ingress).
  Do not use Spring Cloud LoadBalancer: the backend is a Spring MVC monolith
  with no service discovery and no downstream services to balance.
- **Rate limiting.** Accept per-instance limits (more instances = more
  aggregate quota), or move to Redis / edge LB if the business requires it.
  When an LB sits in front, configure `app.rate-limit.trusted-proxies` so
  clients (via X-Forwarded-For) are measured instead of the LB address.

## Observability

- **LB probes:** `/actuator/health` (public, already permitted in
  `SecurityConfig`).
- **Health details:** `GET /api/health/` (status, instanceId, timestamp),
  `/api/health/cache` (per-cache stats), `/api/health/queues` (outbox counts
  including DLQ).
- **Metrics:** `outbox.pending` and `outbox.dead_letter` gauges (Micrometer).
  These are alerting signals, NOT readiness indicators: outbox state is
  shared in PostgreSQL, so a failing probe there would take every instance
  down at once.

## Known limitations

- Outbox processing is at-least-once: `claimEvent`, the handler and the
  `COMPLETED` update run in one transaction, so a crash mid-processing rolls
  back to `PENDING` and the event is reprocessed. Event handlers must be
  idempotent; they already are (guard checks before side effects). A
  `PROCESSING` event is never durably committed.
- Caffeine evictions do not propagate across instances until Redis is
  enabled.

## Prerequisites before the 2nd instance goes live

1. **Hikari sizing:** `pool_size x instances < max_connections` of
   PostgreSQL (currently 20 x 1 = 20; adjust when adding instances or a
   replica).
2. **Deploy order:** the outbox claim makes overlapping polling safe by
   design, so a rolling deploy can add instances without a maintenance
   window.

## Infrastructure pending (not code)

- Dockerfile for `server/` (today `docker-compose.yml` only provides a dev
  PostgreSQL).
- Wiring Actuator liveness/readiness probes in the orchestrator.
- CI/CD to build and deploy per instance.