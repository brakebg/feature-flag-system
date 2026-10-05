# Final review

Range: main...HEAD (round 1 on 2cee8d6, round 2 on e720537...bda0766)   Round: 2   Date: 2026-10-05

Agents (builder-agents 6, step 1): final-reviewer backend (BF), frontend (FF), infra (IF);
security-reviewer (SR); spec-auditor (SA); test-auditor (TA); design-checker (DC);
ecc:java-reviewer (JR); ecc:database-reviewer (DB); ecc:react-reviewer (RR);
pr-review-toolkit:silent-failure-hunter (SF); pr-review-toolkit:pr-test-analyzer (PT).

## Summary
| Severity | Total | Fixed | Rejected | Disputed | Escalated | Blocked | Owner-accepted | Open |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| BLOCKER | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| CRITICAL | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 6 |
| MAJOR | 30 | 0 | 0 | 0 | 0 | 0 | 0 | 30 |
| MINOR | 45 | 0 | 0 | 0 | 0 | 0 | 0 | 45 |

## Findings

BLOCKER and CRITICAL

| ID(s) | Severity | Area | File:line | Spec / AC | Problem | Status | Commit / reason |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SA-1 | BLOCKER | docs | docs/verify-report.md | 12.3 | Final verify report and this review not committed yet | open | Last step of the review (builder-agents 6 step 6) |
| SA-2 | BLOCKER | process | docs/escalations/ESC-002..006 | 12.3, 12.5 | Five escalations are still open | open | Needs owner answers |
| BF-1, JR-1 | CRITICAL | backend | evaluation/FlagCacheService.java:148-199 | 7.2, AC-CACHE-4 | AFTER_COMMIT listeners of two writes to the same key can apply in reverse commit order; cache keeps the older value | fixed | commit 'M8 review: fix BF-1' (D-033) |
| BF-2 | CRITICAL | backend | audit/AuditController.java:43-52 | 6.1 GET /audit | `page`/`size` outside int range give 400 malformed-request instead of 200 empty page / 400 validation | fixed | commit 'M8 review: fix BF-2' |
| SF-C1 | CRITICAL | backend | common/BodySizeLimitFilter.java:36-63, auth/TokenController.java:55 | 9.1, 9.2 | Chunked form body > 64 KB to /auth/token gives 400/401 instead of 413 payload-too-large | fixed | commit 'M8 review: fix SF-C1' |
| TA-1 | CRITICAL | tests | evaluation/EvaluationIT.java:273-289 | AC-CACHE-5, 7.2 | Rollback test cannot tell AFTER_COMMIT from BEFORE_COMMIT; a commit failure is untested | fixed | commit 'M8 review: fix TA-1' (test added; fails with BEFORE_COMMIT, passes with AFTER_COMMIT) |
| SA-3, JR-3 | CRITICAL | backend | auth/ClientRegistrationProperties.java:51-58 | 5.1 | TTL with fractions of a second (PT1.5S) fails startup; spec accepts any duration >= 1 s | fixed | ESC-007 A, commit 'M8 review: apply ESC-007 A' (D-039) |
| SA-4 | CRITICAL | backend | db/migration/{common,dev}/ (D-014) | 4.3 | Migrations sit in sub-folders, not in `db/migration`; decided at Level 1 | rejected | Files are in backend/src/main/resources/db/migration (sub-folders common/, dev/); V2 runs only in dev via spring.flyway.locations, as 4.3 says. FlywayProfileIT proves it (prod: no seed, dev: seed). Cannot make it fail. |

Round 2 (re-check: spec-auditor, final-reviewer backend, silent-failure-hunter, test-auditor)

| ID(s) | Severity | Area | File:line | Spec / AC | Problem | Status | Commit / reason |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SF-N1, BF-FR-7 | CRITICAL | backend | common/BodySizeLimitFilter.java (FormBody) | 5.5, 9.1 | SF-C1 fix: a bad percent escape or unknown charset in a chunked form gave 500 | fixed | commit 'M8 review: round 2 fixes' (skips bad pairs, unknown charset gives no parameters, like Tomcat; ChunkedBodyIT compares chunked with Content-Length) |
| BF-FR-8 | CRITICAL | backend | audit/AuditController.java | 6.1, 9.1 | `targetKey` with U+0000 gave 500 | fixed | commit 'M8 review: round 2 fixes' (400 validation, AuditApiIT) |
| TA-8 | CRITICAL | tests | FlagCacheServiceTest | AC-CACHE-4, D-033 | No test for a late flag change after a newer group delete | fixed | commit 'M8 review: round 2 fixes' (test added) |
| BF-FR-9 | MAJOR | backend | evaluation/FlagCacheService.java | 7.2, 9.2 | Per-key ids grew without bound | fixed | commit 'M8 review: round 2 fixes' (cleared after each reconciliation; ids up to the snapshot count as loaded) |
| TA-9 | MAJOR | tests | FlagCacheServiceTest | D-033 | No test for a change older than the warm-up data | fixed | commit 'M8 review: round 2 fixes' |
| TA-10 | MINOR | tests | FlagServiceTest, GroupServiceTest | D-033 | Expected seq was the mock default 0 | fixed | commit 'M8 review: round 2 fixes' (audit id 77 stubbed and expected) |
| SF-N2 | MINOR | backend | BodySizeLimitFilter.java | – | FormBody does not override getReader (nothing calls it) | open | |

Round 2 re-check results: BF-1, BF-2, BF-3, BF-4, BF-6, SF-C1, SF-M2, SF-M3, TA-1, TA-2, TA-3 confirmed
fixed; SA-4 rejection accepted; SA-3 still holds until ESC-007 is answered; BF-5 still open (owner).

Round 3 (re-check: final-reviewer backend, silent-failure-hunter, test-auditor): SF-N1, BF-FR-7,
BF-FR-8, BF-FR-9, TA-8, TA-9, TA-10 confirmed fixed; no new BLOCKER or CRITICAL.

| ID(s) | Severity | Area | File:line | Spec / AC | Problem | Status | Commit / reason |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TA-11 | MAJOR | tests | ChunkedBodyIT | 5.5 | Bad-encoding cases only checked < 500 and parity | fixed | commit 'M8 review: apply ESC-007 A; round 3 items' (exact 200 / 400 invalid_request) |
| TA-12 | MINOR | tests | FlagCacheServiceTest | D-033 | Boundary seq == loaded id untested | fixed | same commit (fails with `<`) |
| TA-13, TA-14, BF-FR-10, SF-r3-1 | MINOR | backend | FlagServiceTest:192, FlagCacheService | – | Unrelated 77L; stacked Javadoc; max id read after the snapshot | fixed | same commit |
| BF-FR-11, SF-r3-2 | MINOR | backend | BodySizeLimitFilter | 9.1 | Query string decoded with the body charset; no DEBUG line for a skipped pair | open | No spec path affected (token reads body parameters) |

MAJOR

| ID(s) | Severity | Area | File:line | Spec / AC | Problem | Status | Commit / reason |
| --- | --- | --- | --- | --- | --- | --- | --- |
| BF-3 | MAJOR | backend | common/SecurityConfig.java | 9.1 | Firewall-rejected requests (`//`, `;`, `%2F`) get Boot's error JSON, not a problem detail | fixed | commit 'M8 review: fix BF-3, BF-4, BF-6' (FirewallIT) |
| BF-4, JR-4 | MAJOR | backend | group/GroupService.java:107-132 | 6.1 | Group PATCH racing a delete gives 409 instead of 404 | fixed | commit 'M8 review: fix BF-3, BF-4, BF-6' (race IT) |
| BF-5, SF-M2, JR-2 | MAJOR | backend | evaluation/FlagCacheService.java:127-138, 256-260 | 7.2, 9.2 | JDBC inside `synchronized(writeLock)`; pinned virtual threads; listeners wait holding pooled connections | open | Part fixed (ReentrantLock, no pinning); pool wait during reconcile left for the owner (D-034) |
| BF-6 | MAJOR | backend | common/GlobalExceptionHandler.java:128-138 | 9.1 | NUL character in name/description gives 500 instead of 400 | fixed | commit 'M8 review: fix BF-3, BF-4, BF-6' |
| SF-M1 | MAJOR | backend | evaluation/FlagCacheService.java:252-260 | 7.2, 9.3 | Reconcile can report false drift for a write committed during the snapshot; comment says it cannot | open | Known limit documented in code and D-034; value stays correct |
| SA-5 | MAJOR | backend | auth/HmacJwtEncoder.java | 5.1 vs 5.2 | Custom encoder instead of NimbusJwtEncoder (ESC-003 open) | escalated | ESC-003 (waiting for the owner) |
| SA-6 | MAJOR | backend | auth/ClientRegistrationProperties.java:32-45 | 5.1 | Extra startup failures (blank admin password, blank client secret) decided at Level 1 | owner-accepted | ESC-007 A item 2 |
| SA-7 | MAJOR | docs | DECISIONS D-018, D-021, D-023 | 12.5 | Level 1 entries that touch the API | owner-accepted | ESC-007 A item 3 |
| FF-1 | MAJOR | frontend | hooks/queries.ts:146-150 | 8.5, AC-FLAG-3 | Refetch after one toggle can flip another in-flight optimistic toggle back | fixed | commit 'M8 review: fix FF-1..FF-4, TA-2' (D-035) |
| FF-2 | MAJOR | frontend | features/audit/AuditPage.tsx:57 | 8.6 | Load more by offset shows duplicate rows when new events arrive | fixed | commit 'M8 review: fix FF-1..FF-4, TA-2' (D-035) |
| FF-3 | MAJOR | frontend | components/Modal.tsx:17, GroupDialog, FlagDialog | 8.5 | Closing a dialog while its request runs loses a later field error (no toast) | fixed | commit 'M8 review: fix FF-1..FF-4, TA-2' (D-035) |
| FF-4, SF-M3 | MAJOR | frontend | hooks/queries.ts (delete hooks), DeleteFlagDialog, DeleteGroupDialog | 8.5 | Delete of an already deleted item: dialog stays, row stays, technical toast | fixed | commit 'M8 review: fix FF-1..FF-4, TA-2' (D-035) |
| TA-2 | MAJOR | tests | flags.test.tsx:259-268, GroupsPage.test.tsx:341-355 | AC-FLAG-6 | Refetch after 409 is asserted with data that is already on screen | fixed | commit 'M8 review: fix FF-1..FF-4, TA-2' (D-035) |
| TA-3, PT-3 | MAJOR | tests | evaluation/EvaluationIT.java:338-349 | 7.2 | Failed reconcile: ERROR level, ETag and last-success gauge not asserted | fixed | commit 'M8 review: tests for TA-3, PT-1, PT-2, PT-4' (fails if logged at WARN) |
| PT-1 | MAJOR | tests | FlagCacheServiceTest.java:229-252, EvaluationIT.java:200-211 | 7.2, AC-CACHE-4 | Deletes not checked to leave other entries in place | fixed | commit 'M8 review: tests for TA-3, PT-1, PT-2, PT-4' |
| PT-2 | MAJOR | tests | FlagCacheServiceTest | 7.2 | No concurrent onChange test for one group | fixed | commit 'M8 review: tests for TA-3, PT-1, PT-2, PT-4' (fails without the writer lock) |
| PT-4 | MAJOR | tests | AdminGroupsIT, GroupServiceTest:94 | 9.1, 9.2 | Group limit, real optimistic-lock 409 and real unique-violation 409 not tested under concurrency | fixed | commit 'M8 review: tests for TA-3, PT-1, PT-2, PT-4' |
| IF-1 | MAJOR | infra | frontend/nginx.conf:18 | 10.2 | nginx resolves BACKEND_URL once; a recreated backend gives 502 | open | Left for the owner: the DNS resolver address depends on the platform (Docker 127.0.0.11, Kubernetes cluster DNS); a fixed value could break the standalone image |
| IF-2 | MAJOR | infra | frontend/nginx.conf:19 | 10.2 | `Host: $http_host` breaks a BACKEND_URL behind a host-routing ingress; no SNI | fixed | commit 'M8 review: infra fixes IF-2, IF-3, IF-5, IF-6' (D-036) |
| IF-3 | MAJOR | infra | frontend/nginx.conf:13 | 9.1, 9.2 | nginx answers bodies > 1 MB with an HTML 413 | fixed | commit 'M8 review: infra fixes IF-2, IF-3, IF-5, IF-6' (D-036) |
| IF-4 | MAJOR | infra | frontend/Dockerfile:22-23 | 10.2 | Non-root nginx on port 80 fails without unprivileged-port sysctl (Kubernetes) | open | Documented in README (sysctl / NET_BIND_SERVICE); image unchanged: setcap would fail under no-new-privileges |
| IF-5 | MAJOR | infra | docker-compose.yml, backend/Dockerfile | 9.4 | `SERVER_PORT` other than 8080 breaks healthcheck and UI proxy | fixed | commit 'M8 review: infra fixes IF-2, IF-3, IF-5, IF-6' (D-036) |
| IF-6 | MAJOR | infra | Makefile:5,15-16 | 10.3, AC-OPS-1 | `make up` outside a git checkout fails at the sha tag | fixed | commit 'M8 review: infra fixes IF-2, IF-3, IF-5, IF-6' (D-036) |
| IF-7 | MAJOR | infra | backend/Dockerfile:9-13 | 9.6 | Image built without GIT_COMMIT has no git.commit.id, silently | open | Left for the owner: make and CI always pass GIT_COMMIT; failing the build without it would break a plain docker compose build |
| IF-8 | MAJOR | gates | scripts/lib/trace-registry.mjs:60-79 | 11.4 | 9.1 rows that share a status (409 duplicate-key / limit-reached) are not checked per type | open | Left for the owner: gate 14 strengthening (type-aware 9.1 tags) is larger than a small fix; all 9.1 rows have tests today |

MINOR (optional; listed for the owner)

| ID(s) | Area | File:line | Problem | Status |
| --- | --- | --- | --- | --- |
| DB-1 | backend | GroupService.java:61 | GET /groups aggregates all flags in memory (300 ms target at 100k flags not measured) | open |
| DB-2 | backend | AuditEventRepository.java:18 | Bound LIKE pattern may skip the pattern index on a generic plan | open |
| DB-3 | backend | V1:41 | No CHECK on `action` (spec does not ask) | open |
| JR-5, BF-10 | backend | FlagCacheService.java:216-220, TokenController.java:100-107 | Duplicated / stale Javadoc | fixed |
| JR-6 | backend | FlagCacheService.java:94-97 | Revision restarts from max(audit id); can repeat after reconcile + restart | open |
| JR-7, SR-2 | backend | RequestIdFilter.java:37-50, Problems.java:30 | Request id not length-capped; X-Forwarded-Prefix shows in logs / `instance` | open |
| BF-7 | backend | TokenController.java:88-98 | Wildcard content types and query-string params accepted | open |
| BF-8 | backend | AuditService.java:47 | `ff_admin_writes_total` counted before commit | open |
| BF-9 | backend | SecurityConfig.java:57-122 | Method longer than 50 lines | open |
| SA-8 | backend | SecurityConfig.java | CORS values not compared case-insensitively | open |
| SR-1 | docs | README.md:37 | README should say the ingress must overwrite X-Forwarded-* and hide ports 8080/80 | fixed |
| SF-m3 | backend | AuditPurgeJob.java:44-53 | Normal INFO line after a failed purge | open |
| SA-9 | frontend | D-029, D-030 | Extra UI texts (logged) | open |
| RR-1 | frontend | Modal.tsx:23 | First focus on Close, not the first field | open |
| RR-2 | frontend | useFocusTrap.ts:60 | Focus lost after deleting a group | open |
| RR-3 | frontend | AuditPage.tsx:13 | One request per keystroke; table flickers | open |
| RR-4 | frontend | LoginPage.tsx:34 | Blank username/password shows "Sign in failed" | open |
| RR-5 | frontend | FlagTable.tsx:20 | Section and table share the name "Flags" | open |
| RR-6, FF-5 | frontend | ConfirmDialog.tsx:70-78 | Cancel works while a delete runs | fixed |
| FF-6 | frontend | RequireAuth.tsx:6-11 | Expired redirect does not clear the query cache | fixed |
| FF-7 | frontend | queries.ts:63-70 | Extra GET of the deleted group | open |
| FF-8 | frontend | GroupPanel.tsx and others | Components longer than 50 lines | open |
| FF-9 | frontend | FlagTable.tsx:92 | Relative times only refresh on re-render | open |
| SF-m1 | frontend | lib/errors.ts:35-42 | Raw server detail in toasts | open |
| SF-m2 | frontend | GroupsPage.tsx:23 | Failed background refetch keeps stale data silently | open |
| SF-m4 | frontend | auth/token.ts:13-19 | Blocked sessionStorage loops login silently | open |
| DC-1 | frontend | AppShell.module.css:83 | Footer cuts the white groups pane 40 px short | open |
| DC-2 | frontend | GroupDialog.tsx:94,165; FlagDialog.tsx:176 | "(optional)" not muted in three dialogs | fixed |
| DC-3 | frontend | DeleteGroupDialog.tsx:49 | Empty list box for a group with 0 flags | fixed |
| TA-4 | gates | backend/pom.xml:214-215 | JaCoCo class-name scope not recorded in DECISIONS | fixed |
| TA-5 | tests | e2e/ui/groups.spec.ts:150-153 | deletedFlags order not checked in e2e | fixed |
| TA-6, IF-10 | tests | scripts/smoke.sh:61 | Condition always true; only log order checked | fixed |
| TA-7 | tests | e2e/api/evaluation.spec.ts:79-81 | Client 200 case checks only shapes | open |
| PT-m1..m4 | tests | EvaluationIT, FlagCacheServiceTest | Exact drift count, revision from DB, AC-CACHE-2 via reflection, create checks only one endpoint | open |
| IF-9 | gates | check-api-contract.sh:11 | `git diff` against the index, not HEAD | fixed |
| IF-11 | infra | docker-compose.yml, smoke/perf names | Named pgdata volume in test stacks; project names | open |
| IF-12 | infra | scripts/e2e.sh | Playwright container runs as root | open |
| IF-13 | infra | nginx.conf | index.html without Cache-Control | open |
| IF-14 | infra | docker-compose.yml:19,47,51 | Version default hard-coded 1.0.0 | open |
| IF-15 | infra | ops-standalone.sh:29-33 | 90 s counted in loop passes | open |
| IF-16 | infra | backend/Dockerfile:23 | "Picked up JAVA_TOOL_OPTIONS" plain-text line | open |
| IF-17 | infra | ci.yml, install-tools.sh | Actions pinned by tag, not SHA | open |

## Ignored suggestions
- none yet

## Not checked by any agent
- Fresh-clone `git clean -xfd` + `make verify-all` (12.3 item 1): to be run in step 6.
- Firefox / WebKit and narrow-width screenshots: only chromium-desktop baselines exist (spec 11.6 asks only for those).
