# Security Policy — Nestorria

## Supported versions

| Versión | Soporte |
|---|---|
| `main` (actual) | Sí |
| Ramas feature / snapshots | Solo a través de `main` |

## Reporting a vulnerability

**No abras un issue público.** Escribe por mensaje privado al mantenedor del repositorio
con: descripción, pasos de reproducción, impacto estimado y, si aplica, PoC mínimo.
Recibirás confirmación y el fix se publicará vía `CHANGELOG.md`.

## Secrets — reglas duras

- Los `.env` reales están ignorados por git (`.gitignore:1-3`: `.env`, `.env.local`, `.env.*.local`;
  el patrón basename aplica también a `server/.env` y `ai-service/.env`). **Nunca** los subas.
- Solo los **nombres** de variables viven en `.env.example` y `ai-service/.env.example`.
  Este repo y su `docs/` **no contienen valores reales**.
- Claves sensibles (verificadas en `application.properties` y `ai-service/.env.example`):
  `POSTGRES_PASSWORD`, `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET`, `CLDN_API_KEY/SECRET`,
  `SMTP_USER/SMTP_PASS`, `AI_SERVICE_API_KEY` / `API_KEY` (ai-service), `LLM_API_KEY` (Groq).
- La doc pública (`docs/*.md`) solo nombra variables, nunca valores.

## AuthN/Z (verificado en código)

- Usuarios: JWT de Clerk como OAuth2 resource server (`server/pom.xml:80-83`,
  `clerk.issuer-uri` en `application.properties:38`). Roles en `UserRole.java`.
- Interno server↔ai-service: `X-API-Key` (`ai-service/app/middleware/auth.py:48`).
  Fail-closed: sin `API_KEY` configurado, ai-service responde 500 en rutas protegidas
  (`auth.py:39-45`); `/health` y `/ready` están excluidos para probes.
- CORS restringido a `http://localhost:5173,http://localhost:5174` por defecto
  (`application.properties:46`, `ai-service/.env.example:31`).

## Notas operativas

- Stripe: el webhook devuelve 503 sin `STRIPE_WEBHOOK_SECRET` (diferido consciente).
- Logs JSON con `instanceId`/`requestId`; no registrar PII ni secretos en logs.
- Los modelos ML son experimentales: no exponer sus salidas como decisiones finales.
