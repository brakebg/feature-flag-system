# Non-functional requirements

From spec 001 (`docs/SPEC.md`) section 9, 9.1–9.4. Section numbers are kept, so references
such as "spec 9.1" still point here. Map of all sections: `docs/README.md`.

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

`type` is always `https://featureflags.local/problems/<suffix>` using the suffix from the table. `title`, `status` (equal to the HTTP status), `detail` and `type` are always present; `instance` is the request path without the query. `errors` is present only for `validation`; each entry has `field` (the JSON property name, or the query parameter name) and `message`. The content type is `application/problem+json`. Tests do not assert `title`, `detail` or `message` texts, except the login `detail` in 5.2.

| Situation | Status | `type` suffix |
| --- | --- | --- |
| Bean Validation / malformed JSON | 400 | `validation`, `malformed-request` |
| Missing, expired or invalid JWT; wrong admin credentials | 401 | `unauthorized` |
| Valid JWT without the required scope or audience | 403 | `forbidden` |
| HTTPS required (10.2) | 403 | `https-required` |
| Resource not found | 404 | `not-found` |
| Duplicate key | 409 | `duplicate-key` |
| Stale `version` (`OptimisticLockException`) | 409 | `version-conflict` |
| Group or flag limit reached (9.2) | 409 | `limit-reached` |
| Request body above 65,536 bytes | 413 | `payload-too-large` |
| Unexpected | 500 | `internal` (no stack trace in body; logged with a correlation id) |

Exception: `POST /api/v1/auth/token` returns OAuth 2.0 error bodies (`invalid_client`, `invalid_scope`, `unsupported_grant_type`, `invalid_request`) as section 5.5 defines. Its `403 https-required` (10.2) is still a problem detail.

Implemented once in a `@RestControllerAdvice` (`GlobalExceptionHandler`). DB unique violations MUST be mapped to 409, not 500.

### 9.2 Performance and limits

- Evaluation API: p95 < 50 ms at 200 req/s on 1 vCPU / 512 MB (served from cache; hit rate ≥ 99 % after warm-up).
- Admin API: p95 < 300 ms for any call with 1,000 groups × 100 flags.
- Limits: max 1,000 groups, 500 flags per group (enforced, 409 `limit-reached`). The 1,001st group and the 501st flag in a group are rejected with 409 `limit-reached`; deleting an item frees its slot. Request body max 64 KB (65,536 bytes); a larger body returns 413 `payload-too-large`.
- Hit rate = Δ`cache_gets_total{result="hit"}` / (Δ`hit` + Δ`miss`), summed over the caches `flagCache`, `groupCache` and `allFlagsCache`, between the start and the end of the 60 s load on a freshly started stack after warm-up. The load calls only existing keys, split equally over the three Evaluation endpoints.
- How the targets are checked (decision 0007): gate 13 blocks only on errors and on the cache hit rate. It measures the Evaluation API p95 and writes it to the verify report, but a p95 at or above 50 ms does not fail the gate. The p95 targets above (Evaluation and Admin API) are verified in a production-like test environment after the PR is approved, not on a developer machine or in CI.

### 9.3 Observability

- Logs: JSON to stdout (Spring Boot structured logging, `ecs` format). Every request logs method, path, status, duration and a correlation id (`X-Request-Id` header, generated if missing, echoed back). Every response carries `X-Request-Id`. A value sent by the client is returned unchanged; otherwise a UUID is generated.
- Never log passwords, client secrets or JWTs. Evaluation requests log the consumer's client id (JWT sub).
- Actuator: `/actuator/health` (with DB check, liveness and readiness groups), `/actuator/info`, `/actuator/prometheus` (Micrometer; admin token, see 5.4).
- `GET /actuator/health` is public and returns 200 `{"status":"UP","components":{"db":{"status":"UP"}}}` (components are shown without authentication). `GET /actuator/health/liveness` and `/actuator/health/readiness` are public and return 200 `{"status":"UP"}`, or 503 `{"status":"DOWN"}`. `GET /actuator/info` is public and contains `build.version` and `git.commit.id`.
- Custom metrics: `ff_evaluations_total{endpoint,result,client}` (`endpoint`: `all`, `group`, `flag`; `result`: `found`, `not_found`; `client`: the token `sub`), `ff_admin_writes_total{action}` (`action`: the audit action names).

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
| `FF_REQUIRE_HTTPS` | `false` (`true` in `prod`) | Reject login and token requests that did not arrive over HTTPS (10.2) |
| `SERVER_PORT` | `8080` | HTTP port |
| `VITE_API_BASE_URL` (UI build) | `/api` | API base URL |
