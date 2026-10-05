# Feature Flag Service

Boolean feature flags in groups, an admin UI, and a read-only Evaluation API for client
services. Requirements: `docs/SPEC.md`. Changes: `CHANGELOG.md`. Version: `VERSION`.

- **Admin UI** (React): create groups and flags, toggle flags, read the audit log.
- **Admin API** `/api/v1/admin/**`: used by the UI; admin login (JWT, 8 hours).
- **Evaluation API** `/api/v1/evaluate/**`: for client services; OAuth 2.0 client
  credentials (JWT, 15 minutes); answered from an in-memory cache.

## Run it

Needs Docker with Compose v2.

```bash
make up        # builds and starts postgres, backend, frontend
               # UI:      http://localhost:3000   (login admin / admin123)
               # backend: http://localhost:8080
make down      # stops the stack (the database volume stays; `docker compose down -v` removes it)
```

The `dev` profile seeds the group `orders` with the flags `new-checkout` (on) and
`split-payments` (off).

### One backend instance only

Run **exactly one backend instance** (`replicas: 1` in `docker-compose.yml` and in any
orchestrator). Each instance keeps its own flag cache. A change made through one instance
updates only that instance's cache, so a second instance would serve old values until its daily
reconciliation (03:00). Do not scale the backend horizontally in v1.

### Production notes

- Set `SPRING_PROFILES_ACTIVE=prod`. Then `FF_REQUIRE_HTTPS` defaults to `true`, and the backend
  logs a WARN for every default password or secret that is still in use.
- Put an ingress (load balancer, Kubernetes Ingress, Traefik, Caddy) in front of both containers.
  It terminates TLS and rate-limits `POST /api/v1/auth/login` and `POST /api/v1/auth/token` per
  client IP. The service itself has no rate limiting.
- The backend trusts `X-Forwarded-*` headers. The ingress MUST set `X-Forwarded-Proto` itself
  (overwrite, never pass on the client's value) and drop client-sent `X-Forwarded-Host`,
  `X-Forwarded-Prefix` and `Forwarded`. Ports 8080 (backend) and 80 (UI) must be reachable only
  through the ingress, never directly from clients.
- The UI image runs nginx as a non-root user on port 80. Docker allows this by default. On
  Kubernetes or containerd, set the pod sysctl `net.ipv4.ip_unprivileged_port_start=0` (or run
  the pod with the `NET_BIND_SERVICE` capability).
- The images run on their own:
  - backend: needs only `FF_DB_URL`, `FF_DB_USER`, `FF_DB_PASSWORD` (plus real secrets, below);
  - UI: needs only `BACKEND_URL` (nginx proxies `/api/` to it).
- Images are tagged `<version>` and `sha-<short commit>` (`make build-images`). Never deploy `latest`.

## Configuration

Backend environment variables (defaults are for `dev` only; change every secret in production):

| Variable | Default | Purpose |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` or `prod` |
| `FF_DB_URL` | `jdbc:postgresql://localhost:5432/featureflags` (compose: host `postgres`) | JDBC URL |
| `FF_DB_USER` / `FF_DB_PASSWORD` | `featureflags` / `featureflags` | Database credentials |
| `FF_ADMIN_USERNAME` / `FF_ADMIN_PASSWORD` | `admin` / `admin123` | The admin login |
| `FF_JWT_SECRET` | dev string | HS256 signing key, at least 32 bytes (startup fails otherwise) |
| `FF_ADMIN_TOKEN_TTL` | `PT8H` | Admin token lifetime (ISO-8601 duration) |
| `FF_CLIENT_TOKEN_TTL` | `PT15M` | Client token lifetime |
| `FF_CLIENT_ORDER_SERVICE_SECRET` | `order-service-dev-secret` | Secret of the sample client `order-service` |
| `FF_AUTH_CLIENTS_<n>_CLIENT_ID`, `_CLIENT_SECRET`, `_SCOPES` | not set | More clients from the environment; added after the built-in list (`<n>` = 0, 1, ...) |
| `FF_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Allowed browser origins (Vite dev server) |
| `FF_REQUIRE_HTTPS` | `false` (`true` in `prod`) | Reject login and token requests that did not arrive over HTTPS (403 `https-required`) |
| `FF_CACHE_RECONCILE_CRON` | `0 0 3 * * *` | Daily cache reconciliation (server time zone) |
| `FF_AUDIT_RETENTION` | `P365D` | Audit events older than this are purged |
| `FF_AUDIT_PURGE_CRON` | `0 30 3 * * *` | Audit purge job |
| `SERVER_PORT` | `8080` | HTTP port |

UI: `BACKEND_URL` (container start, default `http://backend:8080`); `VITE_API_BASE_URL` (build,
default `/api`). Compose host ports: `FF_BACKEND_PORT` (8080) and `FF_UI_PORT` (3000).

## Admin API

All calls need the admin token. Errors are RFC 9457 problem details. Writes to an existing item
send its current `version`; a stale `version` gives `409 version-conflict`.

```bash
API=http://localhost:8080
TOKEN=$(curl -s -X POST $API/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r .accessToken)
AUTH="Authorization: Bearer $TOKEN"

# Groups
curl -s $API/api/v1/admin/groups -H "$AUTH"
GROUP=$(curl -s -X POST $API/api/v1/admin/groups -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"key":"payments","name":"Payments","description":"Payment providers"}' | jq -r .id)
curl -s $API/api/v1/admin/groups/$GROUP -H "$AUTH"            # group with its flags
curl -s -X PATCH $API/api/v1/admin/groups/$GROUP -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Payments EU","version":0}'

# Flags
FLAG=$(curl -s -X POST $API/api/v1/admin/groups/$GROUP/flags -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"key":"apple-pay","description":"Apple Pay at checkout","enabled":false}' | jq -r .id)
curl -s -X POST $API/api/v1/admin/flags/$FLAG/toggle -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"enabled":true}'
curl -s -X PATCH $API/api/v1/admin/flags/$FLAG -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"description":"Apple Pay everywhere","version":1}'
curl -s -X DELETE $API/api/v1/admin/flags/$FLAG -H "$AUTH"     # 204

# Audit log (newest first, 50 per page, filter by target key prefix)
curl -s "$API/api/v1/admin/audit?page=0&size=50&targetKey=payments" -H "$AUTH"

# Delete the group and all its flags
curl -s -X DELETE $API/api/v1/admin/groups/$GROUP -H "$AUTH"   # 204
```

Keys: 2 to 50 characters, lowercase letters, digits and hyphens, starting with a letter
(`^[a-z][a-z0-9-]{1,49}$`). Limits: 1,000 groups, 500 flags per group, 64 KB per request body.

## Evaluation API (for client services)

### 1. Get a token

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -u 'order-service:order-service-dev-secret' \
  -d 'grant_type=client_credentials&scope=flags:read'
# {"access_token":"eyJ...","token_type":"Bearer","expires_in":900,"scope":"flags:read"}
```

Cache the token. Get a new one when less than 60 seconds remain, or after any `401`.

### 2. Read flags

```bash
CLIENT=$(curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -u 'order-service:order-service-dev-secret' -d 'grant_type=client_credentials' | jq -r .access_token)

curl -s -i http://localhost:8080/api/v1/evaluate/flags -H "Authorization: Bearer $CLIENT"
# ETag: "42"
# {"flags":{"orders.new-checkout":true,"orders.split-payments":false},"revision":42}

curl -s -i http://localhost:8080/api/v1/evaluate/flags -H "Authorization: Bearer $CLIENT" \
  -H 'If-None-Match: "42"'
# 304 Not Modified while nothing changed

curl -s http://localhost:8080/api/v1/evaluate/groups/orders -H "Authorization: Bearer $CLIENT"
# {"group":"orders","flags":{"new-checkout":true,"split-payments":false},"revision":42}

curl -s http://localhost:8080/api/v1/evaluate/flags/orders/new-checkout -H "Authorization: Bearer $CLIENT"
# {"key":"orders.new-checkout","enabled":true}
```

An unknown group or flag returns `404`, never a silent `false`.

### 3. How a client should poll

- Poll `GET /api/v1/evaluate/flags` every **30 seconds** with `If-None-Match: "<last ETag>"`.
  A `304` means nothing changed.
- Keep the last good map in memory. On **any error** (network, 5xx, token error) keep using it.
- If a flag is missing (404, or not in the map) or there was never a good answer, use
  **`false`** as the default.
- Ignore unknown response fields (new optional fields may be added within `/api/v1`).

## Operations

- `GET /actuator/health` (public): `{"status":"UP","components":{"db":{"status":"UP"}}}`.
- `GET /actuator/health/readiness` (public): `503 DOWN` until the flag cache is warmed up.
- `GET /actuator/info` (public): `build.version` and `git.commit.id`.
- `GET /actuator/prometheus` (admin token): `cache_gets_total`, `ff_evaluations_total`,
  `ff_admin_writes_total`, `ff_cache_reconcile_*`, `ff_readiness_up`.
- Prometheus alert rules: `ops/prometheus/alerts.yml`.
- Logs: JSON (ECS) to stdout; every response carries `X-Request-Id`.

## Development

```bash
docker compose up -d postgres
cd backend && ./mvnw spring-boot:run          # Java 21, dev profile
cd frontend && npm ci && npm run dev           # Vite on http://localhost:5173, proxy to 8080
```

Quality gates (spec 11.3); the report goes to `build/verify-report.md`:

```bash
make verify-fast   # gates without Docker: lint, types, architecture, tests, coverage, secrets
make verify        # all 15 gates, stops at the first failure
make verify-all    # all 15 gates, same as CI
```

Gate tools (gitleaks, trivy, promtool, k6) are downloaded by `make tools`. End-to-end tests run
Playwright in its Docker image against fresh compose stacks (`scripts/e2e.sh`).

Project memory for the builder: `docs/STATE.md`, `docs/PROGRESS.md`, `docs/DECISIONS.md`.
