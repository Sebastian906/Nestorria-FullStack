# Nestorria — Glossary

> RAG client corpus (`audience:client`, `lang:en`). Each term cites its source.
> Enum values copied literally from code; unread details are described without inventing values.

## Roles and actors

| Term | Meaning | Source |
|---|---|---|
| `USER` (`user`) | Guest: searches, books, pays, signs, reviews | `modules/user/UserRole.java:6` |
| `AGENCY_OWNER` (`agency_owner`) | Owner: publishes and manages properties, bookings and invoices | `UserRole.java:7` |
| `MANAGER` (`manager`) | Internal management role | `UserRole.java:8` |
| `ADMINISTRATOR` (`administrator`) | Internal administration role | `UserRole.java:9` |

## Bookings and payments

| Term | Meaning | Source |
|---|---|---|
| `PENDING` | Booking created, awaiting confirmation/payment | `modules/booking/BookingStatus.java:4` |
| `CONFIRMED` | Confirmed booking | `BookingStatus.java:5` |
| `CANCELLED` | Cancelled booking | `BookingStatus.java:6` |
| Contract | Agreement linked to a booking, with clauses and multi-role digital signatures; signatures expire after 30 days | `modules/contract/`, `application.properties:65` |
| Invoice (`Invoice`) | Document with 18% tax and 15-day due date | `application.properties:73-75` |
| Stripe webhook 503 | Expected answer until `STRIPE_WEBHOOK_SECRET` is set; deferred payment confirmation | `TECHNICAL_MANUAL.md`, `SECURITY.md` |

## Platform and AI chat

| Term | Meaning | Source |
|---|---|---|
| RAG | Similarity search over `rag_documents` (pgvector) + Groq generation; chunk 500/overlap 50, `top_k=5`, threshold 0.5, 384d `all-MiniLM-L6-v2` embeddings | `ai-service/app/config.py:63-72` |
| Chat quota | 20 messages per user per hour; max 10 concurrent streams | `config.py:81`, `application.properties:124` |
| Fallback | Degraded answer when `ai-service` is unavailable (no empty stream) | `common/ai/AiFallbackHandler.java` |
| STOMP bell | Push notifications for booking/payment/contract over WebSocket | `frontend/src/components/NotificationBell.tsx` |
| Replica disabled | `DB_REPLICA_URL=""` in Docker disables the read replica on purpose | `docker-compose.yml:36` |
| `instanceId` | Instance identifier in JSON logs to tell replicas apart | `application.properties:110`, `logback-spring.xml` |
