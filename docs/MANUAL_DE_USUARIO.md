# Manual de usuario Nestorria (ES)

> Alcance: `frontend/` (React, `:5173`) + `admin/` (Vue, `:5174`) contra `server/` (`:4000`) y `ai-service/` (`:8000`). Verificado contra el repo el 2026-09-12. Lo no hallado se marca `⚠️ NO VERIFICADO EN REPO`.

## 1. Registro / login (Clerk)

1. Abre la app → inicia sesión con Clerk.
2. Roles: `USER` y `AGENCY_OWNER` — ver `server/src/main/java/com/nestorria/server/modules/user/UserRole.java`.
3. Auth JWT sin estado vía `spring-boot-starter-oauth2-resource-server` (`server/pom.xml`) con `clerk.issuer-uri` (`application.properties:38`).

![Pantalla de login](assets/01-login.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 2. Explorar propiedades

Páginas verificadas (`frontend/src/pages/`): `Listing`, `MapExplorer`, `Compare`, `Home`. Componentes: `NearbySearchPanel`, `PropertyMap` (Leaflet `^1.9.4`), `PropertyImages`.

![Listado con filtros](assets/02-listing.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 3. Detalle de propiedad + mapa

`PropertyDetails.tsx` + `PropertyMap.tsx` + galería. Sin strings hardcodeados — texto vía `frontend/src/i18n/` (i18n obligatoria).

![Detalle + mapa Leaflet](assets/03-property-details.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 4. Reserva — fechas inicio/fin + huéspedes (flujo central)

1. En `PropertyDetails` elige check-in / check-out + huéspedes.
2. Chequeo de disponibilidad: `CheckAvailabilityRequest` → `BookingController` (`server/.../modules/booking/`).
3. Confirma → entrada en `MyBookings` (`frontend/src/pages/MyBookings.tsx`).
4. Solapes rechazados en servidor; expiración vía `BookingScheduler.java`.

![Selector de fechas y huéspedes](assets/04-booking.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 5. Favoritos

Toggle corazón → `FavoriteController` (`server/.../modules/favorite/`).

![Toggle de favoritos](assets/05-favorites.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 6. Reviews

`MyReviews.tsx` + `ReviewController`. Límite servidor `review-per-minute=5` (`application.properties:81`).

![Reviews](assets/06-reviews.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 7. Contratos (firma multi-rol)

`ContractDetails.tsx` → `ContractController` (`server/.../modules/contract/`). Entidades: `Contract`, `DigitalSignature`, `SignatureRole`, `ContractClause`. Expiración firma 30 días (`app.contract.signature-expiry-days=30`).

![Firma de contrato](assets/07-contract.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 8. Pagos y facturas (Stripe)

Checkout → `PaymentController` (stripe-java `28.4.0`) → webhook → `Invoice`/`PaymentTransaction` (impuesto 18%, vencimiento 15d — `application.properties:73-75`). Webhook devuelve **503 hasta configurar `STRIPE_WEBHOOK_SECRET`** — diferido consciente, no bug.

![Pagos y factura](assets/08-payments.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 9. Notificaciones push (campana STOMP)

`NotificationBell.tsx` + `@stomp/stompjs ^7.3.0` sobre WebSocket Spring (`server/pom.xml:137`). Eventos: reserva / pago / contrato.

![Campana de notificaciones](assets/09-notifications.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 10. Chat IA (SSE, cuota 20 msg/h)

1. Abre el widget (`frontend/src/components/chat/`).
2. Streaming vía `AiChatStreamingService.java` → FastAPI `app/routers/chat.py` + `rag.py` → Groq `openai/gpt-oss-20b` (`ai-service/app/config.py:77`).
3. Cuota: 20 msgs/usuario/hora (`config.py:81`), máx. 10 streams concurrentes (`application.properties:124`).
4. Fuentes RAG citadas en UI. Si ai-service cae, `AiFallbackHandler.java` responde degradado (sin stream vacío).
5. Modelos ML **experimentales (~85 registros)** — no presentarlos como productivos.

![Widget de chat IA](assets/10-ai-chat.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 11. Panel admin (Vue)

Páginas verificadas (`admin/src/pages/`): `Dashboard`, `ListProperty` / `AddProperty`, `Categories`, `Reports`, `AiDashboard`, `MlopsDashboard`. Puerto `:5174` (`admin/vite.config.js:12`).

![Panel admin](assets/11-admin.png)
> *Captura pendiente — el usuario debe tomarla tras levantar el sistema con `pnpm dev`.*

## 12. Problemas típicos

| Síntoma | Solución |
|---|---|
| `nestorria-primary` ya en uso | `docker rm -f nestorria-primary`, luego `docker compose up -d postgres-primary` |
| `permission denied: mvnw` | `chmod +x mvnw` (también en `server/Dockerfile:10`, el CI lo hace) |
| `ERR_PNPM_NO_MATCHING_VERSION` | Mantener `@clerk/localizations ^4.16.0` (v6 no existe); mantener `allowBuilds: @clerk/shared, msw` en `frontend/pnpm-workspace.yaml` |
| Vite `nav.json EOF` 500 en admin | Borrar `admin/dist`, reiniciar `pnpm dev` |
| Webhook Stripe 503 | Configurar `STRIPE_WEBHOOK_SECRET`; el 503 previo es esperado |

## Arranque rápido (operador)

```bash
docker compose up -d postgres-primary
docker compose up -d --build
curl http://localhost:4000/actuator/health
curl http://localhost:8000/health
```

Puertos: server `:4000` (`server.port=${PORT:4000}`), ai-service `:8000`, DB `pgvector/pgvector:pg16`. Nunca `8080` ni `postgres` plano.

## Checklist de capturas (`docs/assets/`)

1. `01-login.png` — Login Clerk
2. `02-listing.png` — Listado + filtros
3. `03-property-details.png` — Detalle + mapa
4. `04-booking.png` — Selector fechas/huéspedes + confirmación
5. `05-favorites.png` — Favoritos
6. `06-reviews.png` — Reviews
7. `07-contract.png` — Firma
8. `08-payments.png` — Stripe + factura
9. `09-notifications.png` — Campana STOMP
10. `10-ai-chat.png` — Chat IA en streaming
11. `11-admin.png` — Panel admin
