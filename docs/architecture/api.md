# Admin API and Evaluation API

From spec 001 (`docs/SPEC.md`) section 6, 7. Section numbers are kept, so references
such as "spec 9.1" still point here. Map of all sections: `docs/README.md`.

## 6. Admin API

All endpoints are JSON, prefixed `/api/v1/admin`, require a JWT, and return errors as RFC 9457 problem details (section 9.1). Writes that modify an existing resource require the current `version` to prevent lost updates.

### 6.1 Endpoints

| Method | Path | Purpose | Success | Errors |
| --- | --- | --- | --- | --- |
| GET | `/groups` | List groups with flag counts. Query: `q` (search key/name), `sort` (`key`, `name`, `updatedAt`; default `key`) | 200 `GroupSummary[]` | 400, 401, 403 |
| POST | `/groups` | Create group | 201 `Group` + `Location` | 400, 401, 403, 409, 413 |
| GET | `/groups/{groupId}` | Group with all its flags | 200 `GroupDetail` | 400, 401, 403, 404 |
| PATCH | `/groups/{groupId}` | Update `name`, `description` (needs `version`) | 200 `Group` | 400, 401, 403, 404, 409, 413 |
| DELETE | `/groups/{groupId}` | Delete group AND all flags in one transaction | 204 | 400, 401, 403, 404 |
| POST | `/groups/{groupId}/flags` | Create flag in group | 201 `Flag` + `Location` | 400, 401, 403, 404, 409, 413 |
| PATCH | `/flags/{flagId}` | Update `description` and/or `enabled` (needs `version`) | 200 `Flag` | 400, 401, 403, 404, 409, 413 |
| POST | `/flags/{flagId}/toggle` | Set `enabled` to the value in the required body `{ "enabled": true\|false }` (idempotent). A missing body, `{}` or a non-boolean `enabled` returns 400 `malformed-request` | 200 `Flag` | 400, 401, 403, 404, 413 |
| DELETE | `/flags/{flagId}` | Delete one flag | 204 | 400, 401, 403, 404 |
| GET | `/audit` | Audit events, newest first: sorted by `occurredAt` descending, then `id` descending. Query: `page` (0-based), `size` (default 50, max 200), `targetKey` (prefix match) | 200 `Page<AuditEvent>` | 400, 401, 403 |

409 on POST means `duplicate-key` or `limit-reached`; 409 on PATCH means `version-conflict`. 413 means `payload-too-large` (9.1). A path id that is not a UUID returns 400 `malformed-request`. An id that is a UUID but unknown returns 404 `not-found`.

Design notes:

- The toggle endpoint takes the target value instead of blindly flipping, so double-clicks and retries are safe. Group deletion needs no request body; the UI is responsible for confirmation.
- `GET /groups`: `q` matches case-insensitively as a substring of key or name; empty `q` means no filter. `sort=key` and `sort=name` are ascending (`name` case-insensitive), `sort=updatedAt` is newest first; ties are ordered by key ascending. An unknown `sort` value returns 400 `validation` with `field` `sort`.
- PATCH is a partial update: an omitted field stays unchanged. `description` set to `null` or `""` clears the description. `name` set to `null` or to a blank value returns 400 `validation`. `version` is always required; a missing `version` returns 400 `validation` (`field` `version`).
- A toggle to the current value, or a PATCH that changes no field, is a no-op: the response is 200 with the unchanged resource, and `version`, `updatedAt`, `updatedBy`, the audit log and the evaluation `revision` do not change. The `version` check runs first: a PATCH whose `version` differs from the current one returns 409 `version-conflict`, even if it would change no field.
- `GET /audit`: `size` must be between 1 and 200 and `page` must be 0 or more; otherwise 400 `validation` (`field` `size` or `page`). A non-numeric value returns 400 `malformed-request`. A page past the end returns 200 with an empty `content`. `targetKey` is a case-sensitive prefix match; characters have no special meaning; an empty value means no filter.
- `Location` is an absolute path: `/api/v1/admin/groups/<id>` for a created group and `/api/v1/admin/flags/<id>` for a created flag.

### 6.2 Payloads (TypeScript notation)

```ts
type GroupSummary = { id: string; key: string; name: string; description?: string;
  flagCount: number; enabledCount: number; createdBy: string;
  updatedAt: string; updatedBy: string; version: number };

type Group = Omit<GroupSummary, "flagCount" | "enabledCount"> & { createdAt: string };

type GroupDetail = Group & { flags: Flag[] };        // flags sorted by key (code-point order)

type Flag = { id: string; groupId: string; key: string; fullKey: string;
  description?: string; enabled: boolean;
  createdAt: string; createdBy: string; updatedAt: string; updatedBy: string; version: number };

// Request bodies never carry createdBy / updatedBy; the server sets them from the JWT.
// A request `description` of "" or null means no description (4.2).
type CreateGroupRequest = { key: string; name: string; description?: string | null };
type UpdateGroupRequest = { name?: string; description?: string | null; version: number };
type CreateFlagRequest  = { key: string; description?: string | null; enabled?: boolean }; // enabled default false
type UpdateFlagRequest  = { description?: string | null; enabled?: boolean; version: number };
type ToggleFlagRequest  = { enabled: boolean };                // required, see 6.1

type AuditEvent = { id: number; occurredAt: string; actor: string; action: string;
  targetKey: string; details?: Record<string, unknown> };

// Spring Data stable page format ("VIA_DTO", @EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)).
// Not the default PageImpl JSON. `page.number` is the 0-based page index.
type Page<T> = { content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number } };
```

Timestamps are ISO-8601 UTC instants ending in `Z` (for example `2026-10-01T14:32:05.123Z`); the number of fraction digits is not fixed. IDs are UUID strings.

Optional fields (`?`) that have no value are omitted from the JSON; they are never `null`.

Keys are sorted ascending in code-point order (bytes of the lower-case ASCII key), for `GroupDetail.flags` and for `GET /groups` with `sort=key`.

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

Response bodies (200):

```ts
type AllFlags   = { flags: Record<string, boolean>; revision: number };            // key = full key
type GroupFlags = { group: string; flags: Record<string, boolean>; revision: number }; // key = flag key; {} if no flags
type OneFlag    = { key: string; enabled: boolean };                                // key = full key
```

`revision` in a body equals the number inside the `ETag` header of the same response.

A group or flag key that does not match the key regex (4.2) is unknown and returns 404.

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

**Since spec 003 (several instances):** every admin write increments the one-row `cache_state.revision` counter under its row lock as the last statements of its transaction and writes a `flag_change(revision, type, group_key)` row; each instance polls the counter every `FF_CACHE_SYNC_INTERVAL` (1 s) and reloads the named groups into an immutable snapshot; the writing instance syncs itself after commit, before the HTTP response returns. Full design, ETag rule, reconciliation lock and consistency bound: `docs/specs/003-high-availability/SPEC.md` section 5. The table below describes the resulting cache content per change; the `FlagsChangedEvent` payload of 1.0.0 is no longer applied directly.

| Change in the UI | Cache update |
| --- | --- |
| Flag created, updated or toggled | `put` the flag entry; put the new value into its group entry and into all-flags |
| Flag deleted | `put` a negative flag entry; remove it from its group and from all-flags |
| Group created | `put` an empty group entry |
| Group deleted | `put` negative entries for the group and each of its flags; remove those flags from all-flags |
| Group name or description edited | No cache entry changes (the Evaluation API uses keys only); `revision` still increments |

- Applying only after commit means a rolled-back write never reaches the cache.
- If applying an update fails, the listener invalidates the affected entries plus all-flags instead (logged at WARN), so the next read reloads them from the database.
- Each applied change, including a group name or description edit, increases `revision` (since spec 003: the database value `cache_state.revision`, commit-ordered, never reset; in 1.0.0 and 1.1.0: a long held in memory, initialised from `max(audit_event.id)` at startup). It is returned as `ETag: "<revision>"` with `Cache-Control: no-cache`; a request whose `If-None-Match` matches gets `304 Not Modified`. Monotonic rule across instances: spec 003 section 5.5.
- All three Evaluation endpoints return the current `revision` as `ETag: "<revision>"` and `Cache-Control: no-cache` on 200 and 304. A 304 has no body and repeats the ETag. Rejected or failed writes and no-op writes (6.1) do not change `revision`.

**Freshness safety net**

- Warm-up: at startup all groups and flags are loaded into the three caches; the readiness probe reports UP only after warm-up finishes, so live traffic starts at a near-100 % hit rate.
- No time-based expiry on positive entries: all changes are expected to go through the UI/Admin API, which keeps the cache current. Only negative entries expire (30 s).
- Daily reconciliation: a `@Scheduled` job (cron from `FF_CACHE_RECONCILE_CRON`, default `0 0 3 * * *`, server time zone) loads all groups and flags from the database, compares them with the cache and fixes every difference using the same copy-on-write updates as the write path. Each difference is logged at WARN with the key, cached value and database value; if any were found, `revision` increments. It also reports `ff_cache_reconcile_drift_total` and `ff_cache_reconcile_last_success_seconds`. A run that fails is logged at ERROR and leaves the cache as it was. There is no manual reconcile trigger in v1: an urgent direct database fix is picked up by the next nightly run, or immediately by restarting the service, which re-warms the cache.
- `maximumSize` 100,000 entries per cache; `recordStats()` on, exported to Micrometer as `cache_gets_total{cache,result}`.

**Scope: service-level cache only.** The cache lives in the memory of each backend process. A distributed cache (Redis, Hazelcast or similar) is out of scope, and the agent MUST NOT add one, nor any messaging between instances. Instances coordinate only through the database (spec 003).

Since spec 003 the backend runs as any number of instances (N ≥ 2 in the HA tests; `replicas: 1` still works). Daily reconciliation runs on one instance under a PostgreSQL advisory lock and increments the revision when it finds drift. Upgrade to the spec 003 release: stop the old instance before starting the first new instance; the README MUST state this.

### 7.3 Client usage (documented in README)

Fetch a token from `POST /api/v1/auth/token` and cache it; refresh it when less than 60 s remain or after a `401`. Poll `GET /api/v1/evaluate/flags` every 30 s with `If-None-Match`; keep the last good map; on any error (including token errors) keep using it. The README MUST include a curl example of both calls.
