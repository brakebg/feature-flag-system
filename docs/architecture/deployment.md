# Deployment, images and CI

From spec 001 (`docs/SPEC.md`) section 9.6, 10.2, 10.3, 10.5. Section numbers are kept, so references
such as "spec 9.1" still point here. Map of all sections: `docs/README.md`.

### 9.6 Versioning and releases

- **Evaluation API compatibility:** `/api/v1` is a stable contract. Within v1 only additive changes are allowed: new endpoints and new optional response fields. Removing or renaming a field, changing a status code or changing meaning requires `/api/v2`, served alongside v1 for a deprecation period announced in the CHANGELOG. Consumers MUST ignore unknown response fields.
- **Admin API:** used only by the bundled UI and released together with it; it still lives under `/api/v1/admin`.
- **Version numbers:** one Semantic Versioning number (`MAJOR.MINOR.PATCH`) for the repository, kept in the file `VERSION` and tagged in git as `v<version>`. The first release is `1.0.0`; `1.1.0` is spec 002 (Spring Boot 4). A change spec names no version number: each builder PR releases the next free minor version. The builder reads `VERSION` on `main` when it merges `origin/main` before marking its PR ready, adds one minor, and writes it to `VERSION` and a `CHANGELOG.md` section. After merging a release PR, the owner tags the merge commit on `main` as `v<version>` and pushes the tag; the builder never creates tags.
- **Docker images:** both images are tagged `<version>` and `sha-<short commit>`, for example `feature-flag-backend:1.0.0` and `feature-flag-ui:1.0.0`. Deployment manifests never use `latest`. Each image can still be deployed on its own.
- **Version visibility:** the backend reports its version and commit in `/actuator/info` (Spring Boot build info); the UI shows its version in the footer of the app shell. The footer element contains the text `v<version>` (for example `v1.0.0`). `build.version` is a SemVer `MAJOR.MINOR.PATCH` string, `git.commit.id` is a lower-case hex commit id (7 to 40 characters), and the UI footer shows `v` followed by the same `build.version`.
- **CHANGELOG.md** in Keep a Changelog format: every chunk that changes behaviour adds a line under `Unreleased`; the final milestone moves them under `1.0.0`.

### 10.2 Docker images

- Backend: multi-stage, `eclipse-temurin:21-jdk` build → `eclipse-temurin:21-jre` runtime, non-root user, `EXPOSE 8080`, `HEALTHCHECK` on `/actuator/health/readiness`, JVM flags `-XX:MaxRAMPercentage=75`.
- Frontend: multi-stage, `node:20-alpine` build → `nginx:alpine` serving `dist/`. `nginx.conf` does SPA fallback (`try_files $uri /index.html`) and reverse-proxies `/api/` to `${BACKEND_URL}` (envsubst template), so the UI can be deployed on its own host and pointed at any backend.
- Container ports: backend 8080, UI 80. The backend image starts with only `FF_DB_URL`, `FF_DB_USER` and `FF_DB_PASSWORD` set; the UI image starts with only `BACKEND_URL` set. Image names are the `image:` names in `docker-compose.yml`.
- Standalone check (AC-OPS-2): with a separate `postgres:16-alpine` container, the backend image started with only `FF_DB_URL`, `FF_DB_USER` and `FF_DB_PASSWORD` answers `GET /actuator/health/readiness` with 200 `{"status":"UP"}` within 90 s. The UI image started with only `BACKEND_URL` pointing at that backend answers `GET /` with 200 `text/html`, and `GET /api/v1/evaluate/flags` without a token with the backend's 401.

**HTTPS in production.** TLS is terminated by an ingress in front of both services (a Kubernetes Ingress controller, a cloud load balancer, or Traefik or Caddy on a VM; the concrete choice comes with the hosting target). The ingress holds the certificates and routes the UI host and the API host to the two containers, which speak plain HTTP on a private network, so the images stay independent.

- The backend sets `server.forward-headers-strategy=framework` and trusts `X-Forwarded-Proto` from the ingress.
- The ingress (or the CDN / API gateway in front of it) also rate-limits `POST /api/v1/auth/login` and `POST /api/v1/auth/token` per client IP (section 5.3). Its concrete limits come with the hosting target.
- With `FF_REQUIRE_HTTPS=true` (default in `prod`, `false` in `dev`), `POST /api/v1/auth/login` and `POST /api/v1/auth/token` reject requests that did not arrive over HTTPS with `403` and problem type `https-required`, so credentials are never accepted in clear text. A request without `X-Forwarded-Proto` or with `http` counts as not HTTPS. Only these two POST endpoints are affected. The 403 body is a problem detail with type `https-required`, also for the token endpoint. In the AC-OPS-4 check, the login and token requests are sent to the backend port directly; whether nginx passes `X-Forwarded-Proto` on to the backend is not specified.
- Local docker-compose stays plain HTTP in the `dev` profile.

**Security headers.** IBM Plex fonts are bundled with the UI via `@fontsource` packages, so the UI loads nothing from other origins. nginx adds to every UI response:

| Header | Value |
| --- | --- |
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'` |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` (only on requests with `X-Forwarded-Proto: https`, see below) |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `no-referrer` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=()` |

nginx sends these headers on every response it returns, including error responses (`always`), SPA fallback pages and proxied `/api/` responses. Each header in the table appears exactly once. For proxied `/api/` responses nginx removes the same headers from the backend response (`proxy_hide_header`) before adding its own. `Strict-Transport-Security` is sent exactly when the request arrived with `X-Forwarded-Proto: https`, in every profile and stack. A plain-HTTP request (no header, or `http`) gets no `Strict-Transport-Security`.

The backend keeps Spring Security's default security headers, except `Cache-Control`. Exact `Cache-Control` values: every Admin API response (any status) has `Cache-Control: no-store`; every Evaluation API 200 and 304 response has `Cache-Control: no-cache`. These replace Spring Security's default `Cache-Control` for those paths. The UI build must contain no inline scripts, so the policy needs no `'unsafe-inline'`.

### 10.3 docker-compose

Services: `postgres` (16-alpine, volume `pgdata`, healthcheck); `backend-1` and `backend-2` (same image and environment, depend on healthy postgres, host ports `${FF_BACKEND_1_PORT:-18081}` and `${FF_BACKEND_2_PORT:-18082}`; one `backend` before spec 003); `api-lb` (nginx, `${FF_BACKEND_PORT:-8080}`, proxies `/api/` and `/actuator/` to `backend-1` only, so API scripts and tests see one instance); `frontend` (port 3000 → nginx 80, `BACKEND_URL=http://backend`). Only an nginx `upstream backend` block listing both backends (round-robin, `max_fails=2 fail_timeout=5s`, `proxy_next_upstream error timeout http_502 http_503 http_504`) is mounted into the UI image's `/etc/nginx/conf.d/`; the image keeps `BACKEND_URL` as its default (spec 003 section 4.12). `make up` builds and starts; UI at `http://localhost:3000`, login `admin` / `admin123`. The compose stack is the local stack and the test rig for the gates, not the deployment model.

Acceptance check for `make up` (AC-OPS-1): the services `postgres`, `backend-1`, `backend-2`, `api-lb` and `frontend` are running, `postgres` and each `backend-<n>` report healthy, and `GET /` on the UI port returns 200 `text/html`, within 90 seconds after the containers were started (image build time is not counted).

For black-box tests, the harness that runs `make up` (the owner review script `scripts/owner-review.sh`) exports `FF_STACK_STARTED_AT` (ISO-8601 UTC, taken right after `docker compose up` returns) and `FF_COMPOSE_PROJECT` (the compose project name). The test passes when `docker compose -p $FF_COMPOSE_PROJECT ps` shows the five services running, `postgres` and each `backend-<n>` healthy, and `GET /` on the UI port returns 200 `text/html`, all observed no later than `FF_STACK_STARTED_AT` + 90 s.

`docker-compose.yml` passes every variable listed in 9.4 to each backend service as `${VAR:-<default from 9.4>}`. `backend-2` gets `FF_CACHE_SYNC_INTERVAL` as `${FF_BACKEND_2_CACHE_SYNC_INTERVAL:-${FF_CACHE_SYNC_INTERVAL:-PT1S}}`, so a test stack can keep one instance behind. Host ports are `${FF_BACKEND_PORT:-8080}` for `api-lb`, `${FF_BACKEND_1_PORT:-18081}` and `${FF_BACKEND_2_PORT:-18082}` for the backends and `${FF_UI_PORT:-3000}` for the UI, so several stacks (different `-p` project names) can run side by side, for example one with `FF_ADMIN_TOKEN_TTL=PT2S` or `FF_REQUIRE_HTTPS=true`.

### 10.5 CI (GitHub Actions)

On every push and PR CI runs `make verify-all` (section 11.3) in one workflow with Docker available, uploads `build/verify-report.md`, JaCoCo, PIT and Playwright reports as artifacts, and fails the build if any gate fails. CI and the agent run exactly the same command, so a green local run means a green CI run. Docker images are built but not pushed in v1.
