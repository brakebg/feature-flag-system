# Progress log

Append-only. One line per commit: date · commit · milestone · chunk · verify result.
The SHA of a row is filled in by the next commit (a commit cannot contain its own SHA).

| Date | Commit | M | Chunk | Verify |
| --- | --- | --- | --- | --- |
| 2026-10-04 | 8490e7b | M1 | scaffold | verify-fast PASS |
| 2026-10-04 | 2592573 | M1 | UI image: apk upgrade, build --pull (gate 10 image scan) | verify PASS |
| 2026-10-04 | 42ba3f6 | M1 | CI: npm 11 for npm ci (lockfile v3 from npm 11) | verify-fast PASS |
| 2026-10-04 | 0c77c99 | M1 | audit fixes: JaCoCo/Failsafe/PIT/ArchUnit wiring, smoke/e2e/perf/api-contract scripts, Playwright config, alerts, ESC-001 | verify PASS (all 15 gates) |
| 2026-10-04 | 2135218 | M1 | M1: complete (STATE, audit 2 BLOCKER + 5 CRITICAL handled) | verify PASS |
| 2026-10-04 | ac86ad1 | M2 | Flyway V1/V2, MigrationIT, health body (AC-OPS-3), ArchitectureTest | verify-fast PASS |
| 2026-10-04 | 01f65fc | M2 | common: strict JSON types, UUIDv7, Clock, exceptions, GlobalExceptionHandler (9.1) | verify-fast PASS |
| 2026-10-04 | a0e498c | M2 | entities (AuditableEntity, FlagGroup, FeatureFlag, AuditEvent), repositories, JPA auditing, RepositoryIT | verify-fast FAIL (gate 1 format of one test file; pushed by mistake, fixed in the next commit) |
| 2026-10-04 | 2b3a6b7 | M2 | format fix for a0e498c | verify-fast PASS |
| 2026-10-04 | 2620c39 | M2 | DTO records (6.2), request validation (4.2), PATCH omitted vs null | verify-fast PASS |
| 2026-10-04 | 7a398b9 | M2 | ESC-001 applied (A), ESC-002 raised | docs only |
| 2026-10-04 | ba19b68 | M2 | M2 audit fixes: SecurityAuditor, RequestIdFilter, handler for framework errors, fixed test ids, profile seed IT, ArchUnit fixtures, git.commit.id in info | verify-fast PASS |
| 2026-10-04 | 09ce8fe | M2 | M2: complete | verify PASS (full) |
| 2026-10-04 | b0aec95 | M3 | auth: config, TokenIssuer, login, token endpoint, SecurityConfig (5.4), https-required, CORS, admin no-store; ESC-003 | verify-fast PASS |
| 2026-10-04 | e7dc662 | M3 | audit fixes: decoded-path filters, bearer resolver on protected paths, 413 limit, default cache headers, startup checks, evaluate rule tests; ESC-004 | verify-fast PASS |
| 2026-10-04 | 227dd7e | M3 | M3: complete | verify PASS (full) |
| 2026-10-04 | 400b82a | M4 | Admin API: groups, flags, audit, purge job, events, springdoc, openapi.json + schema.d.ts, unit tests for PIT | verify-fast PASS |
| 2026-10-04 | a486511 | M4 | audit fixes: OpenAPI optionality, audit paging edge, flag row locks, purge log, test hardening; ESC-005 | verify-fast PASS |
| 2026-10-04 | 00620e0 | M4 | M4: complete | verify PASS (full) |
| 2026-10-04 | a66a808 | M5 | evaluation API, flag cache (Caffeine, warm-up, write-through, reconciliation), ETag/304, metrics; smoke passes | verify-fast PASS |
| 2026-10-04 | c0c8d20 | M5 | schema.d.ts regenerated (gate 8) | verify FAIL gate 14 (PIT runs in tag log) |
| 2026-10-04 | 9db854d | M5 | PIT mutant runs kept out of the gate 14 tag log | verify PASS (full) |
| 2026-10-04 | 0a935f3 | M5 | audit fixes: reconcile under lock, all-flags race, revision in finally, stale-alert gauge, readiness log + smoke order check, test hardening; ESC-006 | verify-fast PASS |
| 2026-10-04 | 15cbac3 | M5 | M5: complete | verify PASS (full) |
| 2026-10-04 | 7d76ad0 | M6 | UI foundation: API client, token, routes, RequireAuth, login, shell, shared components, tokens.css; tests AC-AUTH-1..4, 6 | verify-fast PASS |
| 2026-10-04 | 553a217 | M6 | component, API client and token tests; fonts never inlined (CSP) | verify-fast PASS |
| 2026-10-04 | 4b0aee0 | M6 | review fixes: focus trap on document, latest Escape handler, TextField textarea props, pure RequireAuth, no request without token, late 401, guarded storage | verify-fast PASS |
| 2026-10-05 | ed91f3c | M6 | footer version from VERSION / build arg (no hard-coded fallback) | verify-fast PASS |
| 2026-10-05 | 9b4a89d | M6 | test audit fixes: lint option reverted, exact text checks, spinner/alert position/Back tests, App wiring test (D-027) | verify-fast PASS |
| 2026-10-05 | (next commit) | M6 | M6: complete | verify PASS (full) |
