# Nestorria — Preguntas frecuentes (FAQ)

> Audiencia: cliente final. Idioma: español. Este archivo es corpus del RAG (`audience:client`, `lang:es`).
> Respuestas cortas y literales a propósito: el retriever usa umbral de similitud 0.7 y `top_k=5`
> (`ai-service/app/config.py`), así que cada pregunta va en su propio `###` con respuesta de 2–4 líneas.

### ¿Cómo creo una cuenta o inicio sesión?

Con Clerk desde la app. Hay dos roles de uso habitual: `USER` (huésped) y `AGENCY_OWNER` (propietario).
Ver `server/src/main/java/com/nestorria/server/modules/user/UserRole.java`.

### ¿Qué roles existen en la plataforma?

`user`, `agency_owner`, `manager` y `administrator` (ver `UserRole.java:5-9`).
Como cliente solo usarás `user` o `agency_owner`.

### ¿Cómo busco propiedades?

En `Listing` con filtros (precio, tipo, categoría), en `MapExplorer` con el mapa Leaflet
y en `Compare` para comparar lado a lado. Ver `frontend/src/pages/`.

### ¿Cómo reservo una propiedad?

En el detalle elige fecha de inicio, fecha de fin y huéspedes, verifica disponibilidad
y confirma. La reserva aparece en `MyBookings`. Los solapes de fechas se rechazan en el servidor.

### ¿Qué estados puede tener mi reserva?

`PENDING`, `CONFIRMED` o `CANCELLED` (ver `modules/booking/BookingStatus.java`).

### ¿Qué hago si mis fechas están ocupadas?

Elige otras fechas: el servidor rechaza reservas que se solapan con otra confirmada.
Vuelve a `Listing` o ajusta el rango en el selector.

### ¿Cómo guardo una propiedad en favoritos?

Con el corazón (toggle) del detalle. Se gestiona en `modules/favorite/` y queda en tu perfil.

### ¿Puedo dejar una reseña?

Sí, en `MyReviews`. Límite del servidor: 5 reseñas por minuto (`application.properties:81`).

### ¿Cómo firmo un contrato?

En `ContractDetails`, con firma digital multi-rol (huésped y propietario firman el mismo contrato).
La firma expira a los 30 días (`app.contract.signature-expiry-days=30`).

### ¿Cómo pago mi reserva?

Con Stripe desde el checkout. El pago genera `PaymentTransaction` e `Invoice`
(impuesto 18%, vencimiento 15 días — `application.properties:73-75`).

### Pagué y no veo la factura, ¿qué pasa?

El webhook de Stripe confirma el pago de forma diferida. Si el secreto del webhook
no está configurado, el servidor responde 503 hasta que el operador lo configure:
es esperado, no un bug. Espera unos minutos y revisa `MyBookings`.

### ¿Cómo recibo avisos de mis reservas, pagos y contratos?

Con la campana de notificaciones (STOMP/WebSocket) del encabezado.
Ver `frontend/src/components/NotificationBell.tsx`.

### ¿Qué puede hacer el chat de IA?

Responder preguntas sobre el funcionamiento de la app en streaming y citar
las fuentes RAG usadas. Cuota: 20 mensajes por usuario y hora.

### ¿Qué hago si el chat de IA no responde o dice que no está disponible?

Es la degradación controlada: si `ai-service` cae, el servidor responde un mensaje
de indisponibilidad en vez de un stream vacío (`AiFallbackHandler.java`).
Reintenta más tarde; el resto de la app sigue funcionando.

### ¿En qué idioma está la aplicación?

Español e inglés; la i18n es obligatoria y no hay textos hardcodeados en la UI.

### ¿Las predicciones de precio de la IA son definitivas?

No. Los modelos ML son experimentales (~85 registros) y orientativos, no productivos.

### ¿Dónde está el manual completo?

En `docs/MANUAL_DE_USUARIO.md` (español) y `docs/USER_MANUAL.md` (inglés),
incluida la sección 12 de problemas típicos.
