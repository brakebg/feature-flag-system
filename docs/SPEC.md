# Feature Flag Service — Technical Specification

Oct 1, 2026 · @Yordan

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
- Rate limiting inside the service. It is done at the edge (CDN, API gateway or ingress), which sees the real client IP; see section 5.3 and decision `decisions/0002-rate-limiting-at-edge.md`.

### 1.3 Glossary

| Term | Meaning |
| --- | --- |
| Group | Named container for flags, e.g. `orders`. Has a unique `key`. |
| Flag | Boolean switch inside exactly one group. Key unique within its group. |
| Full key | `<groupKey>.<flagKey>`, e.g. `orders.new-checkout`. Globally unique. |
| Admin API | Authenticated read/write API used by the UI. |
| Evaluation API | Read-only API used by client services, authenticated by a JWT obtained via the client credentials flow. |

## 2. Tech stack

The agent MUST use these choices; it SHOULD use the latest patch release of each line at build time and pin exact versions in the build files.

| Layer | Choice | Notes |
| --- | --- | --- |
| Language (backend) | Java 21 (LTS) | Use records, sealed types and pattern matching where natural. Virtual threads enabled (`spring.threads.virtual.enabled=true`). |
| Framework | Spring Boot 3.x | Starters: web, validation, data-jpa, security, oauth2-resource-server, actuator. |
| Build (backend) | Maven with wrapper (`mvnw`) | Single module. |
| Database | PostgreSQL 16 | Schema managed by Flyway migrations. No `ddl-auto` in any profile except tests. |
| Auth tokens | JWT (HS256) via `Spring Security NimbusJwtEncoder / NimbusJwtDecoder` | Stateless; validated by OAuth2 Resource Server. Admin login + OAuth 2.0 client credentials; see section 5. |
| API docs | springdoc-openapi | Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs`. |
| Cache | Caffeine (in-process) | For the evaluation API. |
| Backend tests | JUnit 5, AssertJ, Spring Boot Test, Testcontainers (PostgreSQL), MockMvc |  |
| Language (UI) | TypeScript 5 (strict mode) | `"strict": true`, no `any` without a comment. |
| UI framework | React 18 + Vite | SPA. |
| Routing / data | React Router 6, TanStack Query 5 | Query handles caching, refetch and mutations. |
| Forms | React Hook Form + Zod | Zod schemas mirror backend validation. |
| Styling | CSS Modules + a small set of CSS variables | No component library; keep UI simple. |
| UI tests | Vitest + React Testing Library; Playwright for end-to-end | MSW for API mocking in unit tests. |
| Lint / format | ESLint + Prettier (UI); Spotless with Google Java Format (backend) | Enforced in CI. |
| Packaging | Docker (multi-stage), docker-compose for local | Backend image on Eclipse Temurin 21 JRE; UI image on nginx (alpine). |

## 3. Architecture

The system is three deployable units: a backend image, a UI image and a PostgreSQL database. The UI and the backend are versioned, built and deployed independently; the only contract between them is the Admin API in section 6.

&#91;embedded content: deployment and request paths · 2 images + PostgreSQL\]

The UI talks only to the Admin API; client services talk only to the Evaluation API, which answers from the flag cache and queries the database only on a cache miss.

Request paths:

1. Admin edits: browser → nginx (UI image) → `/api/v1/admin/**` with an admin JWT → service layer → PostgreSQL, with an audit event in the same transaction.
2. After commit: the service publishes a `FlagsChangedEvent`; the flag cache updates the affected entries and the revision increments.
3. Consumer authentication: consumer → `POST /api/v1/auth/token` with client id and secret → short-lived JWT (scope `flags:read`), cached until near expiry.
4. Client reads: consumer → `/api/v1/evaluate/**` with `Authorization: Bearer <jwt>` → flag cache (database only on a miss) → JSON with `ETag`.

The backend is stateless apart from the flag cache, so restarting it is safe: the cache is warmed from the database on startup before the readiness probe reports UP.

## 4. Data model

Three tables: `flag_group`, `feature_flag` (many per group, deleted with the group via `ON DELETE CASCADE`) and `audit_event` (append-only history of every change).

### 4.1 Tables

`flag_group`

| Column | Type | Constraints |
| --- | --- | --- |
| id | UUID | PK, generated by the application (UUIDv7 preferred) |
| key | VARCHAR(50) | NOT NULL, UNIQUE, immutable after creation |
| name | VARCHAR(100) | NOT NULL |
| description | VARCHAR(500) | NULL |
| created\_at | TIMESTAMPTZ | NOT NULL, default now() |
| created\_by | VARCHAR(100) | NOT NULL, username of the creator, immutable |
| updated\_at | TIMESTAMPTZ | NOT NULL |
| updated\_by | VARCHAR(100) | NOT NULL, username of the last editor (equals created\_by on insert) |
| version | BIGINT | NOT NULL, JPA `@Version` for optimistic locking |

`feature_flag`

| Column | Type | Constraints |
| --- | --- | --- |
| id | UUID | PK |
| group\_id | UUID | NOT NULL, FK → flag\_group(id) ON DELETE CASCADE, indexed |
| key | VARCHAR(50) | NOT NULL, immutable after creation |
| description | VARCHAR(500) | NULL |
| enabled | BOOLEAN | NOT NULL, default false |
| created\_at | TIMESTAMPTZ | NOT NULL |
| created\_by | VARCHAR(100) | NOT NULL, username of the creator, immutable |
| updated\_at | TIMESTAMPTZ | NOT NULL |
| updated\_by | VARCHAR(100) | NOT NULL, username of the last editor, including toggles (equals created\_by on insert) |
| version | BIGINT | NOT NULL, `@Version` |

Unique constraint: `(group_id, key)`.

Ownership columns: `created_by` and `updated_by` are filled by the backend from the authenticated username (the JWT `sub`), never from the request body. Implement this once with Spring Data JPA auditing (`@EnableJpaAuditing`, an `AuditorAware<String>` reading the security context, and `@CreatedBy` / `@LastModifiedBy` on a shared `@MappedSuperclass` together with `@CreatedDate` / `@LastModifiedDate`). The `created_by` column is mapped `updatable = false`. Seed data in `V2` uses `system` as the creator.

`audit_event`

| Column | Type | Constraints |
| --- | --- | --- |
| id | BIGSERIAL | PK |
| occurred\_at | TIMESTAMPTZ | NOT NULL, indexed DESC |
| actor | VARCHAR(100) | NOT NULL (username from the JWT) |
| action | VARCHAR(30) | NOT NULL: GROUP\_CREATED, GROUP\_UPDATED, GROUP\_DELETED, FLAG\_CREATED, FLAG\_UPDATED, FLAG\_TOGGLED, FLAG\_DELETED |
| target\_key | VARCHAR(101) | NOT NULL: group key or full flag key |
| details | JSONB | NULL: before/after values, e.g. `{"enabled":{"from":false,"to":true}}` |

Audit rows are NOT linked by FK, so history survives deletions. Deleting a group writes one GROUP\_DELETED event whose `details` lists the deleted flag keys.

Retention: audit events are kept for 1 year. A nightly `@Scheduled` job (cron `FF_AUDIT_PURGE_CRON`, default `0 30 3 * * *`) deletes rows whose `occurred_at` is older than `FF_AUDIT_RETENTION` (default `P365D`), in batches of 5,000 so the table is never locked for long, and logs how many rows it removed.

### 4.2 Validation rules

Backend (Bean Validation) and UI (Zod) MUST enforce the same rules.

| Field | Rule |
| --- | --- |
| Group key, flag key | Regex `^[a-z][a-z0-9-]{1,49}$` (lowercase, starts with a letter, 2–50 chars, letters/digits/hyphens). |
| Group name | Required, trimmed, 1–100 chars. |
| Description | Optional, max 500 chars. |
| Uniqueness | Group key unique globally; flag key unique within its group. Violation → HTTP 409. |
| Immutability | `key` cannot be changed after creation (clients depend on it). Rename = delete + create. |

### 4.3 Migrations

Flyway scripts live in `backend/src/main/resources/db/migration`: `V1__init_schema.sql` (all three tables, indexes) and `V2__seed_demo_data.sql` that runs only in the `dev` profile (via `spring.flyway.locations`) and inserts group `orders` with flags `new-checkout` (true) and `split-payments` (false).

## 5. Authentication

Both APIs use JWT bearer tokens issued by the service itself: the admin gets one by logging in with hardcoded credentials, and each consumer system gets one through the OAuth 2.0 client credentials flow with a hardcoded client id and secret. Scopes keep the two apart: admin tokens carry `admin`, consumer tokens carry `flags:read`. All credentials live in configuration, overridable by environment variables.

### 5.1 Configuration

```yaml
featureflags:
  auth:
    admin-username: ${FF_ADMIN_USERNAME:admin}
    admin-password: ${FF_ADMIN_PASSWORD:admin123}   # dev default only
    jwt-secret: ${FF_JWT_SECRET:change-me-to-a-32-byte-minimum-secret!!}
    issuer: feature-flag-service
    admin-token-ttl: ${FF_ADMIN_TOKEN_TTL:PT8H}
    client-token-ttl: ${FF_CLIENT_TOKEN_TTL:PT15M}
    clients:                                          # consumer systems
      - client-id: order-service
        client-secret: ${FF_CLIENT_ORDER_SERVICE_SECRET:order-service-dev-secret}
        scopes: [flags:read]
```

Clients bind to a `List<ClientRegistration>` record via `@ConfigurationProperties`. More clients are added as list entries (or through `FF_AUTH_CLIENTS_0_CLIENT_ID`-style environment variables).

- On startup the backend MUST fail fast if `jwt-secret` is shorter than 32 bytes, or if two clients share a `client-id`.
- In the `prod` profile it MUST log a WARN if any default password or secret above is still in use.
- Admin password and client secrets are compared in constant time (`MessageDigest.isEqual`). They are not stored hashed in v1 because they are config, but checks sit behind `AdminAuthenticator` and `ClientAuthenticator` interfaces so a real user or client store can replace them.
- Tokens are signed HS256 and issued with Spring Security's `NimbusJwtEncoder`; they are validated by Spring Security OAuth2 Resource Server (`spring-boot-starter-oauth2-resource-server`) with a `NimbusJwtDecoder` that checks signature, `exp`, `iss` and `aud`. No custom JWT filter.

### 5.2 Login flow

1. UI posts `{ "username", "password" }` to `POST /api/v1/auth/login`.
2. On success the backend returns `200 { "accessToken": "<jwt>", "expiresAt": "<ISO-8601>", "username": "admin" }`. JWT claims: `sub` = username, `scope` = `admin`, `aud` = `feature-flag-admin`, `iss` = `feature-flag-service`, `iat`, `exp`.
3. On failure: `401` with a generic problem detail ("Invalid username or password"). Never reveal which field was wrong.
4. UI keeps the token in `sessionStorage` and sends `Authorization: Bearer <jwt>` on every Admin API call.
5. Any `401` from the Admin API makes the UI clear the token and redirect to `/login?expired=1`.
6. Logout is client-side only: the UI discards the token (no server-side revocation in v1).

### 5.3 Rate limiting

Not in the service (decision `decisions/0002-rate-limiting-at-edge.md`). Rate limiting of `POST /api/v1/auth/login` and `POST /api/v1/auth/token` is done at the edge (CDN, API gateway or ingress, section 10.2). The service MUST NOT implement its own rate limiter. The UI still handles a `429` from the edge (section 8.2).

### 5.4 Security rules (Spring Security)

| Path | Access |
| --- | --- |
| `POST /api/v1/auth/login` | Public |
| `POST /api/v1/auth/token` | Public (client authenticates with id and secret) |
| `/api/v1/admin/**` | JWT with scope `admin` and audience `feature-flag-admin` |
| `/api/v1/evaluate/**` | JWT with scope `flags:read` and audience `feature-flag-service` |
| `/actuator/health`, `/actuator/info` | Public |
| `/swagger-ui/**`, `/v3/api-docs/**` | Public in `dev`, disabled in `prod` |
| Everything else | Denied |

A token with the wrong scope or audience gets `403`; a missing, expired or badly signed token gets `401` with a `WWW-Authenticate: Bearer` header. Authorities come from the `scope` claim via `JwtGrantedAuthoritiesConverter` (`SCOPE_admin`, `SCOPE_flags:read`).

CSRF is disabled (stateless, token in header). CORS allows the origin in `FF_CORS_ALLOWED_ORIGINS` (default `http://localhost:5173`).

### 5.5 Consumer tokens (client credentials flow)

A consumer system exchanges its client id and secret for a short-lived JWT, caches it, and refreshes it before expiry.

1. Consumer calls the token endpoint (RFC 6749 section 4.4):

```http
POST /api/v1/auth/token
Authorization: Basic base64(order-service:order-service-dev-secret)
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&scope=flags:read
```

2. On success: `200 { "access_token": "<jwt>", "token_type": "Bearer", "expires_in": 900, "scope": "flags:read" }`. JWT claims: `sub` = client id, `scope` = `flags:read`, `aud` = `feature-flag-service`, `iss`, `iat`, `exp`.
3. Errors use the OAuth 2.0 error format, not problem details: `400 { "error": "unsupported_grant_type" }`, `400 { "error": "invalid_scope" }` when asking for a scope the client does not have, `401 { "error": "invalid_client" }` for a wrong id or secret.
4. `scope` in the request is optional; omitted means all scopes registered for the client.
5. Consumer sends `Authorization: Bearer <jwt>` on every Evaluation API call and requests a new token when less than 60 s remain, or after any `401`.
6. No refresh tokens are issued; the client simply repeats step 1.

Migration path: because validation is standard OAuth2 Resource Server, the built-in token endpoint can later be removed and `spring.security.oauth2.resourceserver.jwt.issuer-uri` pointed at an external identity provider (Keycloak, Okta, Azure AD). Consumers then fetch tokens from that provider; the Evaluation API does not change.

## 6. Admin API

All endpoints are JSON, prefixed `/api/v1/admin`, require a JWT, and return errors as RFC 9457 problem details (section 9.1). Writes that modify an existing resource require the current `version` to prevent lost updates.

### 6.1 Endpoints

| Method | Path | Purpose | Success | Errors |
| --- | --- | --- | --- | --- |
| GET | `/groups` | List groups with flag counts. Query: `q` (search key/name), `sort` (`key`, `name`, `updatedAt`; default `key`) | 200 `GroupSummary[]` | 401 |
| POST | `/groups` | Create group | 201 `Group` + `Location` | 400, 409 |
| GET | `/groups/{groupId}` | Group with all its flags | 200 `GroupDetail` | 404 |
| PATCH | `/groups/{groupId}` | Update `name`, `description` (needs `version`) | 200 `Group` | 400, 404, 409 |
| DELETE | `/groups/{groupId}` | Delete group AND all flags in one transaction | 204 | 404 |
| POST | `/groups/{groupId}/flags` | Create flag in group | 201 `Flag` | 400, 404, 409 |
| PATCH | `/flags/{flagId}` | Update `description` and/or `enabled` (needs `version`) | 200 `Flag` | 400, 404, 409 |
| POST | `/flags/{flagId}/toggle` | Flip `enabled`; body `{ "enabled": true }` sets an explicit value (idempotent) | 200 `Flag` | 404 |
| DELETE | `/flags/{flagId}` | Delete one flag | 204 | 404 |
| GET | `/audit` | Audit events, newest first. Query: `page` (0-based), `size` (default 50, max 200), `targetKey` (prefix match) | 200 `Page<AuditEvent>` | 400 |

Design notes: the toggle endpoint takes the target value instead of blindly flipping, so double-clicks and retries are safe. Group deletion needs no request body; the UI is responsible for confirmation.

### 6.2 Payloads (TypeScript notation)

```ts
type GroupSummary = { id: string; key: string; name: string; description?: string;
  flagCount: number; enabledCount: number; createdBy: string;
  updatedAt: string; updatedBy: string; version: number };

type Group = Omit<GroupSummary, "flagCount" | "enabledCount"> & { createdAt: string };

type GroupDetail = Group & { flags: Flag[] };        // flags sorted by key

type Flag = { id: string; groupId: string; key: string; fullKey: string;
  description?: string; enabled: boolean;
  createdAt: string; createdBy: string; updatedAt: string; updatedBy: string; version: number };

// Request bodies never carry createdBy / updatedBy; the server sets them from the JWT.
type CreateGroupRequest = { key: string; name: string; description?: string };
type UpdateGroupRequest = { name?: string; description?: string; version: number };
type CreateFlagRequest  = { key: string; description?: string; enabled?: boolean }; // enabled default false
type UpdateFlagRequest  = { description?: string; enabled?: boolean; version: number };
type ToggleFlagRequest  = { enabled: boolean };

type AuditEvent = { id: number; occurredAt: string; actor: string; action: string;
  targetKey: string; details?: Record<string, unknown> };
```

Timestamps are ISO-8601 UTC strings. IDs are UUID strings.

### 6.3 Example

```http
POST /api/v1/admin/groups/0191f0c2-.../flags
Authorization: Bearer eyJ...
Content-Type: application/json

{ "key": "new-checkout", "description": "New one-page checkout", "enabled": false }

HTTP/1.1 201 Created
Location: /api/v1/admin/flags/0191f0c3-...

{ "id": "0191f0c3-...", "groupId": "0191f0c2-...", "key": "new-checkout",
  "fullKey": "orders.new-checkout", "enabled": false, "version": 0, ... }
```

## 7. Evaluation API (for client services)

Client services read flags with a bearer JWT from the client credentials flow (section 5.5) and cache responses using ETags; every read is served from an in-memory cache, the database is used only on a miss, and every admin change updates the cache.

### 7.1 Endpoints

All require `Authorization: Bearer <jwt>` with scope `flags:read` and audience `feature-flag-service`. Missing, expired or invalid token → `401`; an admin token or any token without `flags:read` → `403`. The consumer's client id (`sub`) is attached to the request log and to the `client` tag of `ff_evaluations_total`.

| Method | Path | Response |
| --- | --- | --- |
| GET | `/api/v1/evaluate/flags` | All flags: `{ "flags": { "orders.new-checkout": true, ... }, "revision": 42 }` |
| GET | `/api/v1/evaluate/groups/{groupKey}` | One group: `{ "group": "orders", "flags": { "new-checkout": true, "split-payments": false }, "revision": 42 }`; unknown group → 404 |
| GET | `/api/v1/evaluate/flags/{groupKey}/{flagKey}` | One flag: `{ "key": "orders.new-checkout", "enabled": true }`; unknown → 404 |

Unknown flags return 404, never a silent `false`. Clients decide their own default; the spec's README MUST tell them to default to `false` on 404 or network error.

### 7.2 Caching

Every Evaluation API request is answered from an in-process Caffeine cache. The database is queried only on a cache miss, and every change made in the UI is written into the cache right after it commits, so the cache never serves a stale value. The Admin API reads straight from the database (low volume, and it needs the latest `version` numbers).

**Caches** (all inside `FlagCacheService`; `Optional.empty()` = a negative entry for a key that does not exist):

| Cache | Key → value | Serves |
| --- | --- | --- |
| `flagCache` | full key (`orders.new-checkout`) → `Optional<Boolean>` | `GET /evaluate/flags/{group}/{flag}` |
| `groupCache` | group key → `Optional<Map<String, Boolean>>` (flag key → enabled) | `GET /evaluate/groups/{group}` |
| `allFlagsCache` | single entry → `Map<String, Boolean>` (full key → enabled) | `GET /evaluate/flags` |

All maps stored in the cache are immutable; updates build a new map and `put` it (copy-on-write), so readers never see a half-applied change.

**Read path (cache-aside)**

1. Look up the key. Hit → return the value without touching the database.
2. Miss → the Caffeine `LoadingCache` loader queries the database, stores the result and returns it. Concurrent misses on the same key run one query, not many.
3. Key not in the database → store a negative entry (expires after 30 s) and return 404, so repeated lookups of unknown keys do not hit the database.

**Write path (write-through after commit)**

Every admin write publishes a `FlagsChangedEvent` describing what changed (keys and new values). A `@TransactionalEventListener(phase = AFTER_COMMIT)` applies it to the cache synchronously, before the HTTP response returns to the UI:

| Change in the UI | Cache update |
| --- | --- |
| Flag created, updated or toggled | `put` the flag entry; put the new value into its group entry and into all-flags |
| Flag deleted | `put` a negative flag entry; remove it from its group and from all-flags |
| Group created | `put` an empty group entry |
| Group deleted | `put` negative entries for the group and each of its flags; remove those flags from all-flags |
| Group name or description edited | Nothing (the Evaluation API uses keys only) |

- Applying only after commit means a rolled-back write never reaches the cache.
- If applying an update fails, the listener invalidates the affected entries plus all-flags instead (logged at WARN), so the next read reloads them from the database.
- Each applied change increments `revision` (a long held in memory, initialised from `max(audit_event.id)` at startup). It is returned as `ETag: "<revision>"` with `Cache-Control: no-cache`; a request whose `If-None-Match` matches gets `304 Not Modified`.

**Freshness safety net**

- Warm-up: at startup all groups and flags are loaded into the three caches; the readiness probe reports UP only after warm-up finishes, so live traffic starts at a near-100 % hit rate.
- No time-based expiry on positive entries: all changes are expected to go through the UI/Admin API, which keeps the cache current. Only negative entries expire (30 s).
- Daily reconciliation: a `@Scheduled` job (cron from `FF_CACHE_RECONCILE_CRON`, default `0 0 3 * * *`, server time zone) loads all groups and flags from the database, compares them with the cache and fixes every difference using the same copy-on-write updates as the write path. Each difference is logged at WARN with the key, cached value and database value; if any were found, `revision` increments. It also reports `ff_cache_reconcile_drift_total` and `ff_cache_reconcile_last_success_seconds`. A run that fails is logged at ERROR and leaves the cache as it was. There is no manual reconcile trigger in v1: an urgent direct database fix is picked up by the next nightly run, or immediately by restarting the service, which re-warms the cache.
- `maximumSize` 100,000 entries per cache; `recordStats()` on, exported to Micrometer as `cache_gets_total{cache,result}`.

**Scope: service-level cache only.** The cache lives in the memory of the backend process. A distributed cache (Redis, Hazelcast or similar) is explicitly out of scope for v1, and the agent MUST NOT add one, nor any messaging between instances.

Known limitation, accepted for v1: with more than one backend instance, a change made through one instance updates only that instance's cache, so other instances would serve old values until their daily reconciliation. Therefore v1 is deployed as exactly one backend instance (`replicas: 1` in docker-compose and any orchestrator), and the README MUST state this. The path to multiple instances is listed in section 13.

### 7.3 Client usage (documented in README)

Fetch a token from `POST /api/v1/auth/token` and cache it; refresh it when less than 60 s remain or after a `401`. Poll `GET /api/v1/evaluate/flags` every 30 s with `If-None-Match`; keep the last good map; on any error (including token errors) keep using it. The README MUST include a curl example of both calls.

## 8. Frontend UI

The UI is a three-route SPA: a login page, a two-pane flags workspace (groups on the left, the selected group's flags on the right), and an audit log. Simple, clean, keyboard accessible, desktop-first but usable down to 768 px wide.

**Visual reference.** The approved screen designs are committed in `docs/design/`, one HTML file and one PNG per screen: sign in, flags workspace, new flag dialog, delete group confirmation, audit log. The UI MUST match them in layout, colours, typography (IBM Plex Sans and IBM Plex Mono), spacing and component states. Where a design and this section disagree, this section decides behaviour and the design decides appearance. Names and values shown in the designs are sample data only.

### 8.1 Routes

| Route | Screen | Auth |
| --- | --- | --- |
| `/login` | Login form | Public; logged-in users are redirected to `/` |
| `/` | Redirects to `/groups` | Required |
| `/groups` and `/groups/:groupId` | Flags workspace; the URL holds the selected group so it survives refresh | Required |
| `/audit` | Audit log | Required |
| `*` | 404 page with a link back | — |

A `RequireAuth` wrapper checks for a non-expired token (decode `exp` client-side) and otherwise redirects to `/login`.

### 8.2 Login page

- Centered card: app title "Feature Flags", Username, Password, "Sign in" button.
- Button disabled and shows a spinner while the request runs; Enter submits.
- Errors: 401 → "Invalid username or password"; 429 (returned by the edge, section 5.3; N from its `Retry-After` header) → "Too many attempts, try again in N seconds"; network → "Cannot reach server".
- `?expired=1` shows an info banner "Your session expired, please sign in again".

### 8.3 App shell

Top bar: app name, nav links (Flags, Audit log), signed-in username, "Sign out" button. Content area below.

### 8.4 Flags workspace

Left pane (groups, \~280 px):

- Search box filtering groups by key or name (client-side).
- List items show group name, key in monospace, and a badge `enabled/total` (e.g. `1/2`).
- "+ New group" button opens a modal: Key, Name, Description. Key field auto-suggests a slug from Name until the user edits Key manually.
- Empty state: "No groups yet" + "Create your first group" button.

Right pane (selected group):

- Header: group name, key (with copy button), description, a meta line "Created by admin · Updated by admin, 2 hours ago" (from createdBy, updatedBy, updatedAt), "Edit" (modal for name/description) and "Delete group" (danger) buttons.
- Flags table, columns: Key (full key shown as tooltip, copy button), Description, Status toggle switch, Created by (username), Updated (relative time with the editor's username underneath), Actions (Edit, Delete). Created by and Updated hide below 860 px wide.
- Search box filters flags in the group; a filter chip set: All / On / Off.
- "+ New flag" button opens a modal: Key, Description, Initial state (switch, default off).
- Empty group state: "No flags in this group" + "Add flag" button.
- No group selected: placeholder "Select a group or create one".

### 8.5 Interaction rules

| Action | Behavior |
| --- | --- |
| Toggle a flag | Optimistic update via TanStack Query; calls `POST /flags/{id}/toggle` with the new value; on error revert and show a toast. Switch is disabled while its request is in flight. |
| Delete a flag | Confirm dialog: "Delete flag `orders.new-checkout`? Services reading it will get 404." Buttons Cancel / Delete. |
| Delete a group | Confirm dialog lists the flag count and requires typing the group key to enable the Delete button. After success, navigate to `/groups` and toast "Group `orders` and 2 flags deleted". |
| Create / edit | Inline field errors from Zod; server 409 shown on the Key field ("Key already exists"); server 400 field errors mapped onto fields. |
| Concurrent edit (409 version conflict) | Toast "This item was changed by someone else" and refetch the group. |
| Any success | Toast for 3 s (create, update, delete). Toggles do not toast. |

### 8.6 Audit log page

Table: Time (local, absolute + relative), Actor, Action (coloured label), Target, Details (e.g. "false → true"). Filter input on target key, "Load more" pagination (50 per page).

### 8.7 UI code structure

- `src/api/` — typed fetch client (`apiClient.ts`) that adds the bearer token, parses problem details into an `ApiError` class, and triggers logout on 401; one module per resource (`groups.ts`, `flags.ts`, `audit.ts`, `auth.ts`).
- `src/hooks/` — TanStack Query hooks (`useGroups`, `useGroup`, `useCreateFlag`, `useToggleFlag`, ...). Query keys: `['groups']`, `['group', id]`, `['audit', filters]`.
- `src/components/` — `Modal`, `ConfirmDialog`, `Toggle`, `Toast`, `Button`, `TextField`, `EmptyState`.
- `src/features/` — `auth/`, `groups/`, `flags/`, `audit/` screens.
- `src/schemas/` — Zod schemas shared by forms.
- API base URL from `import.meta.env.VITE_API_BASE_URL` (default `/api`); in dev Vite proxies `/api` to `http://localhost:8080`.

Accessibility: every control has a label, the toggle is a `button role="switch"` with `aria-checked`, modals trap focus and close on Escape.

## 9. Non-functional requirements

Errors use one problem-details shape everywhere; the service logs JSON, exposes health and metrics, and is configured only through environment variables.

### 9.1 Error format (RFC 9457)

```json
{
  "type": "https://featureflags.local/problems/validation",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request has 1 invalid field",
  "instance": "/api/v1/admin/groups",
  "errors": [ { "field": "key", "message": "must match ^[a-z][a-z0-9-]{1,49}$" } ]
}
```

| Situation | Status | `type` suffix |
| --- | --- | --- |
| Bean Validation / malformed JSON | 400 | `validation`, `malformed-request` |
| Missing, expired or invalid JWT; wrong admin credentials | 401 | `unauthorized` |
| Valid JWT without the required scope or audience | 403 | `forbidden` |
| Resource not found | 404 | `not-found` |
| Duplicate key | 409 | `duplicate-key` |
| Stale `version` (`OptimisticLockException`) | 409 | `version-conflict` |
| Unexpected | 500 | `internal` (no stack trace in body; logged with a correlation id) |

Exception: `POST /api/v1/auth/token` returns OAuth 2.0 error bodies (`invalid_client`, `invalid_scope`, `unsupported_grant_type`) as section 5.5 defines.

Implemented once in a `@RestControllerAdvice` (`GlobalExceptionHandler`). DB unique violations MUST be mapped to 409, not 500.

### 9.2 Performance and limits

- Evaluation API: p95 < 50 ms at 200 req/s on 1 vCPU / 512 MB (served from cache; hit rate ≥ 99 % after warm-up).
- Admin API: p95 < 300 ms for any call with 1,000 groups × 100 flags.
- Limits: max 1,000 groups, 500 flags per group (enforced, 409 `limit-reached`). Request body max 64 KB.

### 9.3 Observability

- Logs: JSON to stdout (Spring Boot structured logging, `ecs` format). Every request logs method, path, status, duration and a correlation id (`X-Request-Id` header, generated if missing, echoed back).
- Never log passwords, client secrets or JWTs. Evaluation requests log the consumer's client id (JWT sub).
- Actuator: `/actuator/health` (with DB check, liveness and readiness groups), `/actuator/info`, `/actuator/prometheus` (Micrometer; behind JWT).
- Custom metrics: `ff_evaluations_total{endpoint,result}`, `ff_admin_writes_total{action}`.

Alert rules: the agent writes them as Prometheus rules in `ops/prometheus/alerts.yml`, validated with `promtool check rules` in gate 1. Delivering alerts (Alertmanager, e-mail, chat) is not wired in v1.

| Alert | Condition | Severity |
| --- | --- | --- |
| `FFReconcileDrift` | Reconciliation found at least one difference in the last 24 h | warning |
| `FFReconcileStale` | No successful reconciliation for more than 26 h | critical |
| `FFNotReady` | Readiness probe DOWN for more than 5 min | critical |
| `FFHighErrorRate` | 5xx responses above 1 % of requests over 10 min | critical |
| `FFSlowEvaluation` | Evaluation API p95 latency above 50 ms over 15 min | warning |

### 9.4 Configuration (environment variables)

| Variable | Default (dev) | Purpose |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` or `prod` |
| `FF_DB_URL` | `jdbc:postgresql://localhost:5432/featureflags` | JDBC URL |
| `FF_DB_USER` / `FF_DB_PASSWORD` | `featureflags` / `featureflags` | DB credentials |
| `FF_ADMIN_USERNAME` / `FF_ADMIN_PASSWORD` | `admin` / `admin123` | Hardcoded login |
| `FF_JWT_SECRET` | dev string (≥ 32 bytes) | JWT signing key |
| `FF_ADMIN_TOKEN_TTL` | `PT8H` | Admin token lifetime |
| `FF_CLIENT_TOKEN_TTL` | `PT15M` | Consumer token lifetime |
| `FF_CLIENT_ORDER_SERVICE_SECRET` | `order-service-dev-secret` | Secret of the sample consumer client |
| `FF_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | UI origin(s) |
| `SERVER_PORT` | `8080` | HTTP port |
| `VITE_API_BASE_URL` (UI build) | `/api` | API base URL |

### 9.5 Backend code structure

Package `com.example.featureflags`, layered by feature:

- `auth/` — `AuthController` (login), `TokenController` (client credentials), `TokenIssuer` (wraps `JwtEncoder`), `AdminAuthenticator`, `ClientAuthenticator`, `ClientRegistrationProperties`.
- `group/` — `FlagGroup` entity, `FlagGroupRepository`, `GroupService`, `GroupController`, DTO records.
- `flag/` — `FeatureFlag` entity, repository, `FlagService`, `FlagController`, DTOs.
- `evaluation/` — `FlagCacheService`, `EvaluationController`.
- `audit/` — `AuditEvent` entity, `AuditService`, `AuditController`.
- `common/` — `GlobalExceptionHandler`, `SecurityConfig` (resource server, scope rules, `JwtDecoder` with issuer and audience validators), `CorsConfig`, `RequestIdFilter`, `ClockConfig` (inject `Clock` for testable timestamps), `AuditableEntity` (`@MappedSuperclass` with created/updated at/by).

Rules: controllers never touch repositories; services are `@Transactional`; entities never leave the service layer (map to DTO records); all audit writes happen in the same transaction as the change.

### 9.6 Versioning and releases

- **Evaluation API compatibility:** `/api/v1` is a stable contract. Within v1 only additive changes are allowed: new endpoints and new optional response fields. Removing or renaming a field, changing a status code or changing meaning requires `/api/v2`, served alongside v1 for a deprecation period announced in the CHANGELOG. Consumers MUST ignore unknown response fields.
- **Admin API:** used only by the bundled UI and released together with it; it still lives under `/api/v1/admin`.
- **Version numbers:** one Semantic Versioning number (`MAJOR.MINOR.PATCH`) for the repository, kept in the file `VERSION` and tagged in git as `v<version>`. The first release is `1.0.0`.
- **Docker images:** both images are tagged `<version>` and `sha-<short commit>`, for example `feature-flag-backend:1.0.0` and `feature-flag-ui:1.0.0`. Deployment manifests never use `latest`. Each image can still be deployed on its own.
- **Version visibility:** the backend reports its version and commit in `/actuator/info` (Spring Boot build info); the UI shows its version in the footer of the app shell.
- **CHANGELOG.md** in Keep a Changelog format: every chunk that changes behaviour adds a line under `Unreleased`; the final milestone moves them under `1.0.0`.

## 10. Repository, build and run

One Git repository with two independently built and deployed apps (`backend/`, `frontend/`) plus a root `docker-compose.yml` that runs everything locally with one command.

### 10.1 Layout

```text
feature-flag-service/
├── backend/
│   ├── mvnw, pom.xml, Dockerfile
│   └── src/{main,test}/java/com/example/featureflags/...
│       src/main/resources/{application.yml, application-dev.yml, application-prod.yml, db/migration/}
├── frontend/
│   ├── package.json, vite.config.ts, tsconfig.json, Dockerfile, nginx.conf
│   ├── src/...
│   └── e2e/            # Playwright tests
├── docker-compose.yml  # postgres + backend + frontend
├── .github/workflows/ci.yml
├── Makefile            # make up | down | test | lint
└── README.md
```

### 10.2 Docker images

- Backend: multi-stage, `eclipse-temurin:21-jdk` build → `eclipse-temurin:21-jre` runtime, non-root user, `EXPOSE 8080`, `HEALTHCHECK` on `/actuator/health/readiness`, JVM flags `-XX:MaxRAMPercentage=75`.
- Frontend: multi-stage, `node:20-alpine` build → `nginx:alpine` serving `dist/`. `nginx.conf` does SPA fallback (`try_files $uri /index.html`) and reverse-proxies `/api/` to `${BACKEND_URL}` (envsubst template), so the UI can be deployed on its own host and pointed at any backend.

**HTTPS in production.** TLS is terminated by an ingress in front of both services (a Kubernetes Ingress controller, a cloud load balancer, or Traefik or Caddy on a VM; the concrete choice comes with the hosting target). The ingress holds the certificates and routes the UI host and the API host to the two containers, which speak plain HTTP on a private network, so the images stay independent.

- The backend sets `server.forward-headers-strategy=framework` and trusts `X-Forwarded-Proto` from the ingress.
- The ingress (or the CDN / API gateway in front of it) also rate-limits `POST /api/v1/auth/login` and `POST /api/v1/auth/token` per client IP (section 5.3). Its concrete limits come with the hosting target.
- With `FF_REQUIRE_HTTPS=true` (default in `prod`, `false` in `dev`), `POST /api/v1/auth/login` and `POST /api/v1/auth/token` reject requests that did not arrive over HTTPS with `403` and problem type `https-required`, so credentials are never accepted in clear text.
- Local docker-compose stays plain HTTP in the `dev` profile.

**Security headers.** IBM Plex fonts are bundled with the UI via `@fontsource` packages, so the UI loads nothing from other origins. nginx adds to every UI response:

| Header | Value |
| --- | --- |
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'` |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` (production only, when served over HTTPS) |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `no-referrer` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=()` |

The backend keeps Spring Security's default security headers and adds `Cache-Control: no-store` to every Admin API response. The UI build must contain no inline scripts, so the policy needs no `'unsafe-inline'`.

### 10.3 docker-compose

Services: `postgres` (16-alpine, volume `pgdata`, healthcheck), `backend` (depends on healthy postgres, port 8080), `frontend` (port 3000 → nginx 80, `BACKEND_URL=http://backend:8080`). `make up` builds and starts; UI at `http://localhost:3000`, login `admin` / `admin123`.

### 10.4 Local development

- Backend: `./mvnw spring-boot:run` with `dev` profile (needs `docker compose up postgres`).
- Frontend: `npm ci && npm run dev` (Vite on 5173, proxy to 8080).
- Scripts in `package.json`: `dev`, `build`, `preview`, `test`, `test:e2e`, `lint`, `typecheck`, `format`.

### 10.5 CI (GitHub Actions)

On every push and PR CI runs `make verify-all` (section 11.3) in one workflow with Docker available, uploads `build/verify-report.md`, JaCoCo, PIT and Playwright reports as artifacts, and fails the build if any gate fails. CI and the agent run exactly the same command, so a green local run means a green CI run. Docker images are built but not pushed in v1.

## 11. Testing and acceptance criteria

The build is accepted when every checkbox below passes in CI; backend line coverage ≥ 80 % on `service` and `controller` packages, frontend ≥ 70 % on `src/features` and `src/api`.

### 11.1 Test layers

| Layer | Tooling | Must cover |
| --- | --- | --- |
| Backend unit | JUnit 5, AssertJ, Mockito | Services: validation, uniqueness, cascade delete, audit details, cache hit, miss and load, write-through for every change type, rollback leaves cache unchanged, JWT create/parse/expiry |
| Backend integration | `@SpringBootTest` + MockMvc + Testcontainers PostgreSQL | Every endpoint in sections 6 and 7: happy path, 400, 401, 404, 409; ETag / 304; Flyway migrations apply on empty DB |
| Frontend unit | Vitest, RTL, MSW | Login form errors, group/flag modals with validation, optimistic toggle + revert, delete-group typed confirmation, 401 → redirect |
| End-to-end | Playwright against docker-compose | Scenarios in 11.2 |

### 11.2 Acceptance criteria

Authentication

- [ ] Given wrong credentials, when I sign in, then I see "Invalid username or password" and stay on `/login`.
- [ ] Given `admin` / `admin123`, when I sign in, then I land on `/groups`.
- [ ] Given no token, when I open `/groups` directly, then I am redirected to `/login`.
- [ ] Given an expired token, when any admin call returns 401, then I am sent to `/login?expired=1`.
- [ ] [Removed — decision 0002] ~~11 failed logins within 5 minutes from one IP return 429.~~ Rate limiting is done at the edge. ID kept so the other IDs do not shift; no test required.
- [ ] Sign out clears the token; Back button does not show protected data.

Groups

- [ ] I can create group `orders` (name "Orders"); it appears in the list with badge `0/0`.
- [ ] Creating a second group with key `orders` shows "Key already exists" on the Key field.
- [ ] Keys failing the regex (`Orders`, `1abc`, `a`, `has space`) are rejected in the UI and by the API.
- [ ] I can edit name and description; key is read-only in the edit modal.
- [ ] Deleting group `orders` with 2 flags requires typing `orders`; afterwards the group and both flags are gone from UI, DB and Evaluation API (404), and one GROUP\_DELETED audit event lists both flag keys.

Flags

- [ ] I can add flag `new-checkout` to `orders`, initial state off; full key `orders.new-checkout` is shown.
- [ ] The same flag key may exist in two different groups.
- [ ] Toggling updates the switch immediately, persists after page reload, and the badge updates.
- [ ] If the toggle request fails, the switch reverts and an error toast appears.
- [ ] Deleting one flag removes only that flag.
- [ ] Editing with a stale `version` returns 409 and the UI refetches.

Evaluation API

- [ ] `POST /api/v1/auth/token` with valid client id and secret returns a bearer JWT with scope `flags:read` and `expires_in` 900.
- [ ] Wrong secret → 401 `invalid_client`; unknown grant type → 400 `unsupported_grant_type`; requesting `admin` scope → 400 `invalid_scope`.
- [ ] Evaluation call without a token or with an expired token → 401; with an admin token → 403; with a client token → 200.
- [ ] A client token sent to any `/api/v1/admin/**` endpoint → 403.
- [ ] After toggling a flag in the UI, the next evaluation call returns the new value (no restart).
- [ ] Repeating a call with `If-None-Match` set to the returned ETag → 304 until the next admin write.
- [ ] Unknown group or flag → 404.

Caching (verified with a spy on the repositories counting database queries)

- [ ] After warm-up, 1,000 evaluation calls for existing flags run zero database queries.
- [ ] Evicting one entry and reading it twice concurrently runs exactly one database query; the second read is a hit.
- [ ] Reading an unknown key twice within 30 s runs one database query.
- [ ] Toggle, create and delete of a flag, and delete of a group, are each visible on the next evaluation call with zero database queries for that call.
- [ ] A write whose transaction rolls back leaves the cached value unchanged.
- [ ] The readiness probe reports DOWN until warm-up has finished.
- [ ] A flag changed directly in the database keeps its old cached value until reconciliation runs; after a reconciliation run (triggered in the test by calling the job), the next evaluation call returns the database value, a WARN is logged and the ETag changes.
- [ ] A reconciliation run with no differences logs no WARN and leaves the ETag unchanged.
- [ ] Under the 9.2 load test the cache hit rate metric is ≥ 99 %.

Audit

- [ ] A new group or flag stores created\_by = the signed-in user; every later update or toggle sets updated\_by to the signed-in user while created\_by stays unchanged; extra createdBy / updatedBy fields in a request body are ignored. Every create, update, toggle and delete produces exactly one audit event with actor `admin`.
- [ ] Audit page lists events newest first and filters by target key prefix.

* [ ] Audit events older than the retention period are deleted by the purge job (verified with an injected `Clock`); newer events are kept.

Operations

- [ ] `make up` on a clean machine with Docker starts all three containers; UI reachable on port 3000 within 90 s.
- [ ] Backend image runs standalone with only env vars set (no UI needed); UI image runs standalone pointed at any `BACKEND_URL`.
- [ ] `/actuator/health` reports UP with DB status.

* [ ] With `FF_REQUIRE_HTTPS=true`, login and token requests carrying `X-Forwarded-Proto: http` are rejected with 403 `https-required`; the same requests with `https` succeed. Every UI response carries the security headers listed in 10.2.

### 11.3 Automated quality gates

One command, `make verify`, runs every gate below in order and is the agent's single source of truth for "done": it exits non-zero on the first failing gate and writes `build/verify-report.json` and `build/verify-report.md` (per gate: status, duration, failing test names, first 30 lines of each error, affected acceptance-criterion IDs). `make verify-all` runs every gate even after a failure; `make verify-fast` runs only the gates that need no Docker (1–6, 9, 10, 14, 15) for the inner edit loop.

| # | Gate | Tool / command | Fails when | Active from |
| --- | --- | --- | --- | --- |
| 1 | Format and lint | `./mvnw spotless:check`, `npm run lint`, `npm run format:check` | Any violation | M1 |
| 2 | Compile and types | `./mvnw -q compile`, `npm run typecheck` | Any error | M1 |
| 3 | Architecture rules | ArchUnit `ArchitectureTest` | A controller uses a repository; an entity appears in a controller signature; Evaluation API code reads the database outside the `FlagCacheService` loaders or reconciliation job; package cycles; field injection | M2 |
| 4 | Banned dependencies | Maven Enforcer `bannedDependencies`; `scripts/check-npm-deps.mjs` | Redis, Jedis, Lettuce, Hazelcast, Kafka, RabbitMQ, jjwt on the backend; any UI component library (MUI, Ant, Chakra, Bootstrap) on the frontend | M1 |
| 5 | Backend tests | `./mvnw verify` (Surefire unit + Failsafe integration with Testcontainers) | Any failing test | M2 |
| 6 | Coverage | JaCoCo `check` goal; Vitest coverage thresholds | Below the thresholds in section 11 | M2 |
| 7 | Mutation testing | PIT on `auth`, `group`, `flag`, `evaluation` packages | Mutation score < 60 %; catches tests that run code without asserting on it | M4 |
| 8 | API contract | springdoc writes `backend/openapi.json`; `openapi-typescript` generates `frontend/src/api/schema.d.ts`; `git diff --exit-code` | Committed OpenAPI file or generated types are out of date, or UI code does not compile against them | M4 |
| 9 | Frontend tests | `npm test -- --coverage` (Vitest, RTL, MSW) | Any failing test | M6 |
| 10 | Secrets | gitleaks with an allowlist for the documented dev defaults; Trivy filesystem scan of Maven and npm dependencies; Trivy scan of both Docker images once they are built (full verify only) | Any other secret-looking string, or any HIGH or CRITICAL vulnerability. The only exception is a vulnerability with no fixed version, listed in .trivyignore with a reason and an expiry date and recorded in docs/DECISIONS.md | M1 |
| 11 | Docker smoke test | `scripts/smoke.sh` against `docker compose up` | Any step fails: health UP within 90 s → admin login → client token → evaluate seeded `orders.new-checkout` = true → toggle via Admin API → evaluate returns false → readiness was DOWN before warm-up → every UI security header from 10.2 present | M5 |
| 12 | End-to-end | Playwright, `--repeat-each=2`, retries 0 | Any failure, including a test that passes once and fails once (flaky), or any Content-Security-Policy violation reported in the browser console | M8 |
| 13 | Performance | k6 script `perf/evaluate.js`, 200 req/s for 60 s against compose | p95 ≥ 50 ms, any error, or cache hit rate < 99 % | M8 |
| 14 | Traceability | `scripts/check-traceability.mjs` (section 11.4) | Any acceptance criterion without a passing test, or a test tagged with an unknown ID | M1 |
| 15 | Test integrity | `scripts/check-integrity.mjs` | Any `@Disabled`, `assumeTrue(false)`, `.skip(`, `.only(`, `xit(`, `test.fixme`; any `eslint-disable` or `@SuppressWarnings` without a `// reason:` comment; thresholds in config files differing from this spec; coverage, mutation, lint or ArchUnit exclusions not listed in `docs/DECISIONS.md` | M1 |

A gate not yet active passes trivially but is already wired into `make verify` from M1, so later milestones only add tests, never plumbing.

### 11.4 Acceptance-criteria traceability

Every checkbox in 11.2 gets a stable ID by group and position: `AC-AUTH-1…6`, `AC-GRP-1…5`, `AC-FLAG-1…6`, `AC-EVAL-1…7`, `AC-CACHE-1…9`, `AC-AUD-1…3`, `AC-OPS-1…4` (for example, the first Groups checkbox is `AC-GRP-1`).

A checkbox marked `[Removed — decision NNNN]` keeps its ID so later IDs do not shift. It needs no test, and `check-traceability.mjs` skips it. Currently removed: `AC-AUTH-5` (decision 0002).

- In M1 the agent copies the criteria verbatim with their IDs into `docs/acceptance-criteria.md`. That file is read-only for the agent afterwards.
- Each test names the IDs it proves: JUnit `@Tag("AC-FLAG-3")`; Vitest and Playwright titles contain `[AC-FLAG-3]`. One test may cover several IDs; one ID may need several tests.
- `check-traceability.mjs` reads the registry plus the JUnit XML, Vitest JUnit and Playwright JSON reports, and writes an ID → tests → pass/fail matrix into the verify report. Every ID needs at least one test, and all its tests must pass.
- In addition, every status code listed in the Errors column of 6.1, and every row of the 9.1 error table, must have an integration test asserting status and problem-detail `type`; the script checks this from tags of the form `ERR-<METHOD>-<path>-<status>`.

### 11.5 Test environments and data isolation

Every test run uses throwaway containers, and the database and the cache are always reset together, never the database alone (a direct database change would leave the cache stale, by design).

- **Backend integration tests:** Testcontainers starts a fresh PostgreSQL container per run (container reuse off in CI). Before each test the harness truncates the tables and calls `FlagCacheService.reloadAll()`, the same public method used by warm-up and reconciliation, so no test-only code exists in the service.
- **End-to-end, smoke and performance tests:** each run starts a new docker-compose stack under a unique project name (`ff-e2e-<run-id>`) with no persistent volumes, and removes it with `docker compose down -v` in a shell `trap`, so it is removed even when tests fail.
- **Inside one end-to-end run:** each test creates its own data through the Admin API with unique keys (`e2e-<test-id>-...`), asserts only on that data, and deletes it through the Admin API in `afterEach`. Tests are independent and run with 4 parallel workers.
- **Screenshot tests** run in their own fresh stack, seeded through the Admin API with the sample data shown in the designs; relative-time and username cells are masked so screenshots do not depend on the clock.

### 11.6 UI testing

**Browsers (Playwright projects)**

| Project | Viewport | Runs |
| --- | --- | --- |
| `chromium-desktop` | 1440 × 900 | Full suite |
| `chromium-narrow` | 800 × 900 | Full suite |
| `firefox-desktop` | 1440 × 900 | Tests tagged `@cross-browser` only |
| `webkit-desktop` | 1440 × 900 | Tests tagged `@cross-browser` only |

The `@cross-browser` subset has 6–8 tests: login, session-expired redirect, toggle a flag, create a flag, delete-group confirmation, audit log. Browsers never run in `make verify-fast`.

**Matching the design**

1. Style checks: Playwright reads computed styles and asserts the key design values, for example the primary button background `#2350C8`, top bar background `#15181D`, body font IBM Plex Sans, keys in IBM Plex Mono, 44 px login inputs, and the switch colour when on. Design values live once in `frontend/src/styles/tokens.css`; tests compare against the values taken from `docs/design/`, not against `tokens.css`.
2. Screenshot regression: from M7, `toHaveScreenshot` baselines for all five screens on `chromium-desktop` are committed (`maxDiffPixelRatio` 0.01). Changing a baseline requires a `docs/DECISIONS.md` entry naming the screen and the reason; gate 15 checks that every changed baseline file is listed there.
3. Review gallery: the final run writes `docs/design-compare/index.html`, showing each screen's screenshot next to its design PNG at the same size, for the owner's final review. There is no automated pixel comparison against the design PNGs, because font rendering and sample data differences would cause constant false failures.

## 12. Implementation plan for the AI agent

The agent builds in eight milestones, in order; each ends with green tests and a commit, and no milestone starts while the previous one is red. The agent runs straight through all eight milestones without waiting for review; only a Level 3 escalation (12.5) pauses work. The owner reviews once, at the end.

### 12.1 Working rules

- This spec is committed as `docs/SPEC.md`. Read it fully, then `docs/STATE.md`, before writing code. If code and spec disagree, the spec wins.
- Where the spec is silent, make a Level 1 decision (12.5): choose the simplest option, record it in `docs/DECISIONS.md`, and continue.
- Do not add dependencies, services or features beyond this spec (no Redis, Kafka, component libraries, i18n, dark mode).
- Work in small green chunks and push every one (12.6). A milestone ends with a commit `M<n>: complete` and a summary comment on the PR (for information only; the agent does not wait for a reply).
- Never commit real secrets; only the documented dev defaults.

### 12.2 Milestones

1. **M1 Scaffolding** — repo layout (10.1), Spring Boot app with health endpoint, Vite React TS app, docker-compose with Postgres, CI workflow, lint/format configured, make verify with all 15 gates wired (inactive gates pass trivially), docs/acceptance-criteria.md registry with IDs, CLAUDE.md with the startup ritual, docs/STATE.md and docs/PROGRESS.md, the draft PR, and the escalation-notify workflow. Done when: `make up` shows a placeholder UI and `/actuator/health` is UP.
2. **M2 Schema and domain** — Flyway V1/V2, entities, repositories, DTO records, `GlobalExceptionHandler`. Done when: migration test on Testcontainers passes.
3. **M3 Auth** — config properties, `/auth/login`, client credentials token endpoint, resource-server JWT validation with scopes and audiences, security rules (5.4). Done when: auth integration tests pass.
4. **M4 Admin API** — group and flag services/controllers, optimistic locking, cascade delete, audit events, `/audit`. Done when: all section 6 integration tests pass and OpenAPI renders.
5. **M5 Evaluation API** — flag cache (Caffeine loading caches, warm-up, after-commit write-through, daily reconciliation job), ETag/304, metrics. Done when: section 7 tests pass, including "toggle then read".
6. **M6 UI foundation** — API client, auth flow, routing, app shell, shared components, toasts. Done when: login/logout/redirect unit tests pass.
7. **M7 UI features** — groups pane, flags table, modals, toggle, delete confirmations, audit page. Done when: frontend unit tests pass and coverage targets are met.
8. **M8 Hardening** — Playwright e2e for every 11.2 scenario, Dockerfiles finalised (non-root, healthchecks), README (setup, env vars, API usage with curl examples, client polling guidance). Done when: the full CI pipeline is green and every 11.2 box is ticked.

### 12.3 Definition of done

- [ ] `make verify-all` passes from a fresh clone (`git clean -xfd`), all 15 gates green.
- [ ] The traceability matrix shows every acceptance criterion and every error case covered by at least one passing test.
- [ ] Gate 15 reports no integrity violations; thresholds equal the values in this spec.
- [ ] README documents setup, configuration, the single-instance rule, Admin and Evaluation API with curl examples.
- [ ] `docs/DECISIONS.md` lists every assumption; `docs/BLOCKERS.md` is empty, or each entry explains what was tried.
- [ ] No escalation is open, no `wip/` branch remains, and `docs/STATE.md` shows all milestones complete.
- [ ] The final `docs/verify-report.md` is committed and the PR is marked ready for review.

### 12.4 Feedback loop: how the agent reacts to failures

Failed tests drive the work: the agent changes production code until the gates pass, and never changes the gates to pass.

1. Work in small steps. After each change run `make verify-fast`; before every milestone commit run `make verify`. A milestone is done only when every gate active for it is green.
2. On failure, open `build/verify-report.md`, start with the first failing gate, read the failing test and the acceptance criterion it is tagged with, then fix the production code.
3. Forbidden ways to turn a gate green: deleting, skipping or disabling tests; weakening or removing assertions; lowering thresholds; adding exclusions; editing `docs/acceptance-criteria.md` or the banned-dependency lists; swallowing exceptions; adding test-only branches to production code. Gate 15 catches most of these; the rest are rules.
4. A test may be changed only when it contradicts this spec. The agent records the AC ID, the old expectation, the spec section that proves it wrong, and the fix in `docs/DECISIONS.md`.
5. Stuck rule: if the same gate fails with the same error after 5 attempts, revert to the last green commit and try a different approach. After 3 different approaches, record the gate, the error and what was tried in `docs/BLOCKERS.md`, continue with work that does not depend on it, and list the blocker in the final summary. A milestone with a red gate is never marked done.
6. Determinism: inject `Clock`; no `Thread.sleep` in tests (use Awaitility); pin Testcontainers image tags; no random data without a fixed seed. An intermittently failing test is a bug to fix, never something to retry.

### 12.5 Escalation

The agent escalates to the owner only what it cannot safely decide itself, through the GitHub pull request, because cloud sessions are non-interactive and may end at any time.

**Three levels**

| Level | When | Agent does | Owner does |
| --- | --- | --- | --- |
| 1. Decide and log | Spec is silent; choice is low-impact, reversible, and touches no API, data model, security rule, acceptance criterion or gate | Picks the simplest option; adds a `DECISIONS.md` entry | Nothing |
| 2. Blocker, continue | Stuck rule (12.4 step 5) exhausted, but remaining work does not depend on it | Adds a `BLOCKERS.md` entry; continues; lists it in `STATE.md` and the milestone summary | Reads it at the final review |
| 3. Escalate and wait | Any trigger below | Raises an escalation (flow below); parks the item; continues independent work | Answers on the PR |

**Level 3 triggers**

1. Spec conflict: two sections contradict each other, or meeting one acceptance criterion breaks another.
2. A fix would change a locked item: an acceptance criterion, the API contract (sections 6 and 7), the data model (4), the security model (5), the banned-dependency lists, or a gate or threshold (11).
3. Critical-path blocker: a Level 2 blocker that all remaining work depends on.
4. Outside the code: missing access or permission, Docker or a package registry unavailable, a dependency with a license or security problem.
5. Target unreachable: a performance or cache target still missed after optimisation, with measurements.
6. Budget exceeded: more than 20 failed `make verify` runs within one milestone.
7. Risky action: force-push, history rewrite, deleting branches, changing repository settings, or anything outside this repository.

Before escalating, the agent checks that the spec, `DECISIONS.md` and earlier escalation answers do not already answer the question; if they do, it is Level 1.

**Flow on GitHub**

1. Working PR: in M1 the agent opens one draft PR from branch `feature/feature-flag-service` to `main`. All work is pushed there; the owner enables Auto-fix on it so PR comments reach the live session.
2. Raise: the agent commits `docs/escalations/ESC-<NNN>.md` (template below), pushes, adds it to `STATE.md`, and posts a PR comment starting with `[ESCALATION ESC-<NNN>]` that contains the trigger number, the one question, options A/B/C and its recommendation. It also asks the question in the session.
3. Notify: workflow `.github/workflows/escalation-notify.yml` runs on `issue_comment` created. If the body starts with `[ESCALATION`, it adds the label `needs-human` and posts `@<owner> ESC-<NNN> needs your answer` as the GitHub Actions bot (owner from repository variable `ESCALATION_OWNER`). This is needed because the agent's comments appear under the owner's own account, and GitHub does not notify people about their own comments. The workflow has only `issues: write` and `pull-requests: write`, never checks out or runs code.
4. Answer: the owner replies on the PR with `ESC-<NNN>: <option>` plus any notes, or answers in the session. If the session that asked has ended, the owner starts a new cloud session with the prompt `Resume from docs/STATE.md`; its startup ritual (12.6) reads the answer from the PR.
5. Close: the agent records the answer in `DECISIONS.md` under the escalation ID, sets the file's status to resolved, applies it, and posts `[RESOLVED ESC-<NNN>] applied option <X> in <commit>`. The workflow removes `needs-human` when no escalation remains open.
6. While waiting: never guess on the escalated item. Continue independent work; if none is left, push, update `STATE.md`, post `[WAITING] all remaining work blocked by ESC-<NNN>`, and end the session.
7. Batching: Level 3 items that are not yet blocking anything are collected and raised together in one escalation at the next milestone boundary.

**Escalation file template**

```markdown
# ESC-003: <short title>
Status: open            # open | resolved
Trigger: 2 (fix would change a locked item)
Milestone / gate / AC: M5 / gate 13 / AC-CACHE-9
Raised: <ISO time> at commit <sha>

## What happened
## Evidence (excerpt from build/verify-report.md)
## What was tried (attempts, approaches, commits)
## Options
A. <option> - trade-off
B. <option> - trade-off
## Recommendation
## Blocked meanwhile / continuing with
## Question (one, answerable with an option letter)
## Answer (filled in when resolved)
```

### 12.6 Session continuity

Any session can end at any time: cloud VMs are reclaimed after inactivity (uncommitted files, processes and containers are lost), sessions have been observed to disappear entirely after about 7 days, and long conversations are compacted. The repository is therefore the agent's only durable memory: a brand-new session with no history MUST be able to continue from the repository alone, losing at most one small chunk of work.

**Small green chunks**

1. A chunk is one small, self-contained change: one endpoint, one component, one migration, one group of tests. Aim for at most about 300 changed lines.
2. Per chunk: implement it with its tests → `make verify-fast` → green → update `docs/STATE.md` and append to `docs/PROGRESS.md` → commit → push immediately. Unpushed commits are never left behind.
3. Commit message: `M<n> <area>: <what changed>`, with a trailer `AC: AC-FLAG-3, AC-FLAG-4` naming the criteria it advances.
4. Only green chunks go to the PR branch. If a chunk cannot be made green before the session must stop, push it to `wip/<short-name>` instead, and list that branch in `STATE.md`.
5. Milestone end: full `make verify` green → `STATE.md` updated → commit `M<n>: complete` → PR comment with a milestone summary (what was built, gate results, decisions, blockers).

**Memory files**

| File | Holds | Updated |
| --- | --- | --- |
| `CLAUDE.md` (repo root) | Startup ritual and the short form of the working rules; loaded automatically by Claude Code | M1, rarely after |
| `docs/SPEC.md` | This specification | Only by the owner |
| `docs/STATE.md` | Where the work stands and what comes next (below) | Every commit |
| `docs/PROGRESS.md` | Append-only log: date, commit, milestone, chunk, verify result | Every commit |
| `docs/DECISIONS.md` | Level 1 decisions and escalation answers | When made |
| `docs/BLOCKERS.md` | Level 2 blockers | When raised or cleared |
| `docs/escalations/ESC-*.md` | Level 3 escalations | When raised or resolved |
| `docs/acceptance-criteria.md` | Criteria registry, read-only for the agent | M1 |

`docs/STATE.md` has these sections, kept under 150 lines (older detail lives in `PROGRESS.md`):

- Current milestone and chunk, with status.
- Last green commit SHA, and the last full `make verify` result per gate with its date.
- Chunks done in the current milestone, one line each.
- Next 3 steps, concrete enough to start without other context.
- Open escalations and blockers, one line each with ID.
- `wip/` branches and what each holds.
- Last processed PR comment (ID and time).
- Notes for the next session: environment quirks and lessons learned.

**Startup ritual** (in `CLAUDE.md`; run at the start of every session and again after any context compaction)

1. Read `CLAUDE.md`, `docs/STATE.md`, `docs/DECISIONS.md`, `docs/BLOCKERS.md` and every open `docs/escalations/ESC-*.md`; read the `docs/SPEC.md` sections the next steps touch.
2. `git fetch`, check out `feature/feature-flag-service`, and confirm the branch head matches or follows the last green commit in `STATE.md`. If they differ, reconcile from `PROGRESS.md` before changing anything.
3. Read PR comments newer than the last processed one; apply any escalation answers (12.5 step 5).
4. Check each listed `wip/` branch: finish it, or record why it was dropped.
5. Run `make verify-fast` to confirm a green baseline.
6. Continue with the next steps in `STATE.md`.

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
