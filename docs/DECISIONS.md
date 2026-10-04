# Decisions

Level 1 decisions of the builder (spec 12.5) and answers to escalations.
Owner decisions live in `decisions/` (not here).

Format: ID · date · spec section · decision · why.

## D-001 · 2026-10-04 · 10.3, 9.4 · Compose default for `FF_DB_URL`

In `docker-compose.yml` the default for `FF_DB_URL` is
`jdbc:postgresql://postgres:5432/featureflags`, not the 9.4 default (`localhost`).
Inside the compose network the database host is the `postgres` service; `localhost`
would point at the backend container itself. The application default in
`application.yml` stays the 9.4 value.

## D-002 · 2026-10-04 · 11.3 gates 1, 10, 13 · Gate tools are downloaded

`gitleaks`, `trivy`, `promtool` and `k6` are not npm or Maven packages. `scripts/install-tools.sh`
downloads pinned release binaries into `build/tools/` and checks each against the
release checksum file. `make verify*` calls it when a gate needs the tool.

## D-003 · 2026-10-04 · 11.3 gate 14, 11.4 · Gate 14 before M8

Gate 14 is active from M1, but most acceptance criteria can only have tests later
(spec 12.2). `scripts/lib/trace-registry.mjs` gives every AC and ERR ID the milestone
whose "Done when" first needs it. Gate 14 always fails on an unknown ID or a failing
tagged test. A missing test fails from its due milestone; before that it shows as
`pending`. From M8 every ID is required, exactly as spec 11.4 says. Due milestones:
auth/token IDs M3, admin IDs and AC-AUD-1/3 M4, evaluation and cache IDs M5, UI auth
M6, UI groups/flags/audit M7, AC-OPS-1/2 and AC-CACHE-9 M8, AC-OPS-3 M2.
This is listed in the M1 PR comment so the owner can object.

## D-004 · 2026-10-04 · 11.4 · ERR ID format and the 9.1 rows

ERR IDs use the path relative to `/api/v1`, for example `ERR-POST-/admin/groups-409`.
The list is in `scripts/lib/trace-registry.mjs`: every status in the 6.1 Errors column,
plus `POST /auth/login` (400, 401, 403), `POST /auth/token` (400, 401, 403) and the
evaluation endpoints (401, 403, 404). Each 9.1 row is mapped to one ERR ID whose test
asserts that row's status and problem `type` (for example `409 limit-reached` →
`ERR-POST-/admin/groups/{groupId}/flags-409`). The 500 row uses `ERR-GET-/admin/groups-500`.

## D-005 · 2026-10-04 · 11.3 gate 6 · Frontend coverage step from M6

Gate 6 is active from M2. Its backend step (JaCoCo) runs from M2. Its frontend step
(Vitest thresholds) runs from M6, when the first frontend tests exist (gate 9 is also
active from M6). Before M6 `src/features` and `src/api` do not exist.

## D-006 · 2026-10-04 · 2, 10.2 · Node versions

The UI image builds with `node:20-alpine` (spec 10.2); it only runs `vite build`.
Vitest 5, MSW 3 and jsdom need Node 22 or newer, so tests run on the Node of the
machine (CI: Node 22).

## D-007 · 2026-10-04 · 11.3 gate 10 · Patched library versions

The Spring Boot 3.5.16 BOM pins versions with HIGH/CRITICAL CVEs. `backend/pom.xml`
overrides `jackson-bom.version`, `tomcat.version` and `postgresql.version` with the
patched releases.

## D-008 · 2026-10-04 · 9.6 · Commit id in the backend image

The backend Docker build has no `.git`. `make up` and CI pass `GIT_COMMIT` as a build
argument; the Dockerfile writes `git.properties` from it, so `/actuator/info` shows
`git.commit.id`.
