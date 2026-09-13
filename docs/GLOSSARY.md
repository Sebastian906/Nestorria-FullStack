# Nestorria — Glosario

> Corpus RAG de cliente (`audience:client`, `lang:es`). Cada término cita su fuente.
> Valores de enums copiados literalmente del código; lo no leído se describe sin inventar valores.

## Roles y actores

| Término | Significado | Fuente |
|---|---|---|
| `USER` (`user`) | Huésped: busca, reserva, paga, firma, opina | `modules/user/UserRole.java:6` |
| `AGENCY_OWNER` (`agency_owner`) | Propietario: publica y gestiona propiedades, reservas y facturas | `UserRole.java:7` |
| `MANAGER` (`manager`) | Rol interno de gestión | `UserRole.java:8` |
| `ADMINISTRATOR` (`administrator`) | Rol interno de administración | `UserRole.java:9` |

## Reservas y pagos

| Término | Significado | Fuente |
|---|---|---|
| `PENDING` | Reserva creada, pendiente de confirmación/pago | `modules/booking/BookingStatus.java:4` |
| `CONFIRMED` | Reserva confirmada | `BookingStatus.java:5` |
| `CANCELLED` | Reserva cancelada | `BookingStatus.java:6` |
| Contrato | Acuerdo ligado a una reserva, con cláusulas y firmas digitales multi-rol; la firma expira a 30 días | `modules/contract/`, `application.properties:65` |
| Factura (`Invoice`) | Documento con impuesto 18% y vencimiento a 15 días | `application.properties:73-75` |
| Webhook Stripe 503 | Respuesta esperada hasta configurar `STRIPE_WEBHOOK_SECRET`; confirmación de pago diferida | `TECHNICAL_MANUAL.md`, `SECURITY.md` |

## Plataforma y chat IA

| Término | Significado | Fuente |
|---|---|---|
| RAG | Búsqueda por similitud sobre `rag_documents` (pgvector) + generación con Groq; chunk 500/overlap 50, `top_k=5`, umbral 0.7, embeddings 384d `all-MiniLM-L6-v2` | `ai-service/app/config.py:63-72` |
| Cuota del chat | 20 mensajes por usuario y hora; máx. 10 streams concurrentes | `config.py:81`, `application.properties:124` |
| `API_KEY` | Clave de API para autenticación en el servicio de IA | `ai-service/.env` |
| Fallback | Respuesta degradada cuando `ai-service` no está disponible (sin stream vacío) | `common/ai/AiFallbackHandler.java` |
| Campana STOMP | Notificaciones push de reserva/pago/contrato por WebSocket | `frontend/src/components/NotificationBell.tsx` |
| Réplica desactivada | `DB_REPLICA_URL=""` en Docker desactiva la réplica de lectura a propósito | `docker-compose.yml:36` |
| `instanceId` | Identificador de instancia en logs JSON para distinguir réplicas | `application.properties:110`, `logback-spring.xml` |
