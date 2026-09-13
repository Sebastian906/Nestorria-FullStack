# Nestorria — User Manual (EN)

> Scope: `frontend/` (React, `:5173`) + `admin/` (Vue, `:5174`) against `server/` (`:4000`) and `ai-service/` (`:8000`). Verified against repo state 2026-09-12. Versions/ports cite real files; anything not found is marked `NOT VERIFIED IN REPO`.

## 1. Login / roles (Clerk)

1. Open the app → Sign in with Clerk.
2. Roles: `USER` and `AGENCY_OWNER` — see `server/src/main/java/com/nestorria/server/modules/user/UserRole.java`.
3. Auth is stateless JWT via `spring-boot-starter-oauth2-resource-server` (`server/pom.xml`) with `clerk.issuer-uri` (`server/src/main/resources/application.properties:38`).

![Login screen](assets/01-login.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 2. Explore properties

Pages (verified `frontend/src/pages/`): `Listing`, `MapExplorer`, `Compare`, `Home`. Components: `NearbySearchPanel`, `PropertyMap` (Leaflet `^1.9.4`), `PropertyImages`.

![Listing with filters](assets/02-listing.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 3. Property details + map

`PropertyDetails.tsx` + `PropertyMap.tsx` + gallery. No hardcoded strings — UI text via `frontend/src/i18n/` (mandatory i18n).

![Property details + Leaflet map](assets/03-property-details.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 4. Booking — start/end dates + guests (core flow)

1. In `PropertyDetails` pick check-in / check-out + guests.
2. Availability check: `CheckAvailabilityRequest` → `BookingController` (`server/.../modules/booking/`).
3. Confirm → entry in `MyBookings` (`frontend/src/pages/MyBookings.tsx`).
4. Overlaps are rejected server-side; scheduler handles expiry (`BookingScheduler.java`).

![Booking date and guest selector](assets/04-booking.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 5. Favorites

Heart toggle → `FavoriteController` (`server/.../modules/favorite/`). List visible in profile.

![Favorites toggle](assets/05-favorites.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 6. Reviews

`MyReviews.tsx` + `ReviewController`. Server rate limit `review-per-minute=5` (`application.properties:81`).

![Reviews](assets/06-reviews.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 7. Contracts (multi-role signature)

`ContractDetails.tsx` → `ContractController` (`server/.../modules/contract/`). Entities: `Contract`, `DigitalSignature`, `SignatureRole`, `ContractClause`. Signature expiry 30 days (`app.contract.signature-expiry-days=30`).

![Contract signing](assets/07-contract.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 8. Payments & invoices (Stripe)

Checkout → `PaymentController` (stripe-java `28.4.0`, `server/pom.xml:101`) → webhook → `Invoice`/`PaymentTransaction` (tax 18%, due 15d — `application.properties:73-75`). Webhook returns **503 until `STRIPE_WEBHOOK_SECRET` is set** — deferred by design, not a bug.

![Payments and invoice](assets/08-payments.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 9. Push notifications (STOMP bell)

`NotificationBell.tsx` + `@stomp/stompjs ^7.3.0` over Spring WebSocket (`spring-boot-starter-websocket`, `server/pom.xml:137`). Events: booking / payment / contract.

![Notifications bell](assets/09-notifications.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 10. AI chat (SSE, quota 20 msg/h)

1. Open the widget (`frontend/src/components/chat/`).
2. Streaming via `AiChatStreamingService.java` → FastAPI `app/routers/chat.py` + `rag.py` → Groq `openai/gpt-oss-20b` (`ai-service/app/config.py:77`).
3. Quota: 20 msgs/user/hour (`config.py:81`), max 10 concurrent streams (`application.properties:124`).
4. Cited RAG sources shown in UI. If ai-service is down, `AiFallbackHandler.java` returns a degraded message (no blank stream).
5. ML models are **experimental (~85 records)** — do not present predictions as production-grade.

![AI chat widget streaming](assets/10-ai-chat.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 11. Admin panel (Vue)

Pages (verified `admin/src/pages/`): `Dashboard`, `ListProperty` / `AddProperty`, `Categories`, `Reports`, `AiDashboard`, `MlopsDashboard`. Port `:5174` (`admin/vite.config.js:12`).

![Admin dashboard](assets/11-admin.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 12. Troubleshooting

| Symptom | Fix |
|---|---|
| `nestorria-primary` already in use | `docker rm -f nestorria-primary`, then `docker compose up -d postgres-primary` |
| `permission denied: mvnw` | `chmod +x mvnw` (also in `server/Dockerfile:10`, CI does it) |
| `ERR_PNPM_NO_MATCHING_VERSION` | Keep `@clerk/localizations ^4.16.0` (v6 does not exist); keep `allowBuilds: @clerk/shared, msw` in `frontend/pnpm-workspace.yaml` |
| Vite `nav.json EOF` 500 in admin | Delete `admin/dist`, restart `pnpm dev` |
| Stripe webhook 503 | Set `STRIPE_WEBHOOK_SECRET`; 503 before that is expected |

## Quick start (operator)

```bash
docker compose up -d postgres-primary
docker compose up -d --build
curl http://localhost:4000/actuator/health
curl http://localhost:8000/health
```

Ports: server `:4000` (`server.port=${PORT:4000}`), ai-service `:8000`, DB `pgvector/pgvector:pg16`. Never `8080` / plain `postgres`.

## Screenshot checklist (`docs/assets/`)

1. `01-login.png` — Clerk login
2. `02-listing.png` — Listing + filters
3. `03-property-details.png` — Details + Leaflet map
4. `04-booking.png` — Date/guest selector + confirmation
5. `05-favorites.png` — Favorites toggle
6. `06-reviews.png` — Reviews
7. `07-contract.png` — Contract signature
8. `08-payments.png` — Stripe + invoice
9. `09-notifications.png` — STOMP bell
10. `10-ai-chat.png` — AI widget streaming
11. `11-admin.png` — Admin dashboard
