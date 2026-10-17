# Product overview

From spec 001 (`docs/SPEC.md`) section 1, 13. Section numbers are kept, so references
such as "spec 9.1" still point here. Map of all sections: `docs/README.md`.

## 1. Overview

Build a standalone Feature Flag Service: a Java 21 backend that stores boolean flags organised in groups, plus a React/TypeScript admin UI behind a simple login. Other applications read flag values over a read-only HTTP API. This document is written to be executed end to end by an autonomous coding agent; every "MUST" is a hard requirement, every "SHOULD" is expected unless it blocks progress.

### 1.1 Goals

- An admin can log in with hardcoded credentials and manage flags without touching code or a database.
- Flags are grouped (example: group `orders` holds `new-checkout`, `split-payments`). A group can be created, renamed and deleted; deleting a group deletes all its flags.
- Each flag is a boolean (`true` / `false`) that can be created, toggled, edited and deleted individually.
- Client services can read a single flag or a whole group in under 50 ms (p95) from the service.
- The backend and the UI each ship as their own Docker image and can be deployed independently.

### 1.2 Non-goals (v1)

- Real user management, roles or SSO. Credentials are hardcoded in configuration; the design keeps auth swappable.
- Targeting rules, percentage rollouts, user segments or multivariate (string/number/JSON) flags. The data model leaves room for them (see section 13).
- Multiple environments (dev/stage/prod) inside one instance. One deployment = one environment.
- Client SDKs. Clients call the HTTP API directly.
- Real-time push (SSE/WebSocket) to clients. Clients poll with ETag caching.
- Distributed cache (Redis, Hazelcast or similar) and horizontal scaling. The cache lives inside the service process, so v1 runs as exactly one backend instance (see section 7.2).
- Rate limiting inside the service. It is done at the edge (CDN, API gateway or ingress), which sees the real client IP; see section 5.3 and decision `docs/architecture/decisions/0002-rate-limiting-at-edge.md`.

### 1.3 Glossary

| Term | Meaning |
| --- | --- |
| Group | Named container for flags, e.g. `orders`. Has a unique `key`. |
| Flag | Boolean switch inside exactly one group. Key unique within its group. |
| Full key | `<groupKey>.<flagKey>`, e.g. `orders.new-checkout`. Globally unique. |
| Admin API | Authenticated read/write API used by the UI. |
| Evaluation API | Read-only API used by client services, authenticated by a JWT obtained via the client credentials flow. |

## 13. Future enhancements and open questions

These are out of scope for v1 but shape today's design so they can be added without a rewrite.

| Future item | v1 design hook |
| --- | --- |
| Real users, roles (viewer/editor), SSO/OIDC | `AdminAuthenticator` interface; `actor` already recorded in audit |
| Environments (dev/stage/prod per flag) | Add `environment` table + `flag_value(flag_id, env_id, enabled)`; evaluation path gains `/{env}` |
| Percentage rollouts and targeting rules | Add `rules JSONB` column to `feature_flag`; evaluation becomes `POST` with a context body |
| Multi-instance backend | Broadcast cache updates between instances with Postgres `LISTEN/NOTIFY` |
| Push updates to clients | SSE endpoint `/api/v1/evaluate/stream` emitting revisions |
| Java/TS client SDKs | Wrap the polling + ETag logic from 7.3 |
| Flag lifecycle (stale-flag reports, owner, expiry date) | Add nullable `owner`, `expires_at` columns |

### Open questions

- [x] Flag and group keys are immutable after creation; renaming means delete and recreate. (Decided.)
- [x] PostgreSQL is the database; no embedded store. (Decided.)
- [x] Backend and UI stay two separate images; the backend does not serve the UI. (Decided.)
- [x] Export and import of flags, and a group-wide kill switch, are out of v1. (Decided.)
- [ ] Accessibility testing (axe scans, keyboard-only tests, ESLint `jsx-a11y`). (Parked.)
- [ ] Which hosting target (Kubernetes, VM, PaaS) should the Docker images, ingress and health checks be tuned for? (Parked with the cloud session setup.)
