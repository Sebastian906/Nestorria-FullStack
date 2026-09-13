# Nestorria — Frequently Asked Questions (FAQ)

> Audience: end client. Language: English. This file is RAG corpus (`audience:client`, `lang:en`).
> Short literal answers on purpose: the retriever uses similarity threshold 0.5 and `top_k=5`
> (`ai-service/app/config.py`), so each question gets its own `###` with a 2–4 line answer.

### How do I create an account or sign in?

With Clerk from the app. Two everyday roles: `USER` (guest) and `AGENCY_OWNER` (owner).
See `server/src/main/java/com/nestorria/server/modules/user/UserRole.java`.

### Which roles exist on the platform?

`user`, `agency_owner`, `manager` and `administrator` (see `UserRole.java:5-9`).
As a client you will only use `user` or `agency_owner`.

### How do I search for properties?

In `Listing` with filters (price, type, category), in `MapExplorer` with the Leaflet map
and in `Compare` to compare side by side. See `frontend/src/pages/`.

### How do I book a property?

On the details page pick start date, end date and guests, check availability
and confirm. The booking shows up in `MyBookings`. Overlapping dates are rejected server-side.

### Which states can my booking have?

`PENDING`, `CONFIRMED` or `CANCELLED` (see `modules/booking/BookingStatus.java`).

### What do I do if my dates are taken?

Pick different dates: the server rejects bookings overlapping a confirmed one.
Go back to `Listing` or adjust the range in the selector.

### How do I save a property to favorites?

With the heart toggle on the details page. Managed in `modules/favorite/`, listed in your profile.

### Can I leave a review?

Yes, in `MyReviews`. Server limit: 5 reviews per minute (`application.properties:81`).

### How do I sign a contract?

In `ContractDetails`, with multi-role digital signature (guest and owner sign the same contract).
Signatures expire after 30 days (`app.contract.signature-expiry-days=30`).

### How do I pay for my booking?

With Stripe from the checkout. Payment creates a `PaymentTransaction` and an `Invoice`
(18% tax, due in 15 days — `application.properties:73-75`).

### I paid but I don't see the invoice, what happens?

The Stripe webhook confirms payment with a delay. If the webhook secret
is not configured, the server answers 503 until the operator sets it:
that is expected, not a bug. Wait a few minutes and check `MyBookings`.

### How do I get notified about bookings, payments and contracts?

Through the notification bell (STOMP/WebSocket) in the header.
See `frontend/src/components/NotificationBell.tsx`.

### What can the AI chat do?

Answer questions about how the app works in streaming mode and cite
the RAG sources used. Quota: 20 messages per user per hour.

### What do I do if the AI chat doesn't answer or says it's unavailable?

That is graceful degradation: when `ai-service` is down, the server returns
an unavailability message instead of an empty stream (`AiFallbackHandler.java`).
Try again later; the rest of the app keeps working.

### Which languages does the app support?

Spanish and English; i18n is mandatory and there are no hardcoded UI strings.

### Are the AI price predictions final?

No. ML models are experimental (~85 records) and indicative, not production-grade.

### Where is the full manual?

In `docs/USER_MANUAL.md` (English) and `docs/MANUAL_DE_USUARIO.md` (Spanish),
including section 12 with typical issues.
