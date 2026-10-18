# Architecture overview

From spec 001 (`docs/SPEC.md`) section 3. Section numbers are kept, so references
such as "spec 9.1" still point here. Map of all sections: `docs/README.md`.

## 3. Architecture

The system is three deployable units: a backend image, a UI image and a PostgreSQL database. The UI and the backend are versioned, built and deployed independently; the only contract between them is the Admin API in section 6.

&#91;embedded content: deployment and request paths · 2 images + PostgreSQL\]

The UI talks only to the Admin API; client services talk only to the Evaluation API, which answers from the flag cache and queries the database only on a cache miss.

Request paths:

1. Admin edits: browser → nginx (UI image) → `/api/v1/admin/**` with an admin JWT → service layer → PostgreSQL, with an audit event, the `cache_state.revision` increment and a `flag_change` row in the same transaction (since spec 003).
2. After commit: the writing instance syncs its flag cache from the database before it answers; every other backend instance syncs within `FF_CACHE_SYNC_INTERVAL` (1 s) by polling the revision (`api.md` 7.2).
3. Consumer authentication: consumer → `POST /api/v1/auth/token` with client id and secret → short-lived JWT (scope `flags:read`), cached until near expiry.
4. Client reads: consumer → `/api/v1/evaluate/**` with `Authorization: Bearer <jwt>` → flag cache (database only on a miss) → JSON with `ETag`.

The backend is stateless apart from the flag cache, so restarting it is safe: the cache is warmed from the database on startup before the readiness probe reports UP. Since spec 003 it runs as any number of instances behind a load balancer; they share only the database.
