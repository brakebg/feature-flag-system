# Final review

Range: main...2cee8d6   Round: 1   Date: 2026-10-05

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
| SA-3, JR-3 | CRITICAL | backend | auth/ClientRegistrationProperties.java:51-58 | 5.1 | TTL with fractions of a second (PT1.5S) fails startup; spec accepts any duration >= 1 s | escalated | ESC-007 (5.1 vs 5.2 conflict) |
| SA-4 | CRITICAL | backend | db/migration/{common,dev}/ (D-014) | 4.3 | Migrations sit in sub-folders, not in `db/migration`; decided at Level 1 | rejected | Files are in backend/src/main/resources/db/migration (sub-folders common/, dev/); V2 runs only in dev via spring.flyway.locations, as 4.3 says. FlywayProfileIT proves it (prod: no seed, dev: seed). Cannot make it fail. |

MAJOR

| ID(s) | Severity | Area | File:line | Spec / AC | Problem | Status | Commit / reason |
| --- | --- | --- | --- | --- | --- | --- | --- |
| BF-3 | MAJOR | backend | common/SecurityConfig.java | 9.1 | Firewall-rejected requests (`//`, `;`, `%2F`) get Boot's error JSON, not a problem detail | fixed | commit 'M8 review: fix BF-3, BF-4, BF-6' (FirewallIT) |
| BF-4, JR-4 | MAJOR | backend | group/GroupService.java:107-132 | 6.1 | Group PATCH racing a delete gives 409 instead of 404 | fixed | commit 'M8 review: fix BF-3, BF-4, BF-6' (race IT) |
| BF-5, SF-M2, JR-2 | MAJOR | backend | evaluation/FlagCacheService.java:127-138, 256-260 | 7.2, 9.2 | JDBC inside `synchronized(writeLock)`; pinned virtual threads; listeners wait holding pooled connections | open | Part fixed (ReentrantLock, no pinning); pool wait during reconcile left for the owner (D-034) |
| BF-6 | MAJOR | backend | common/GlobalExceptionHandler.java:128-138 | 9.1 | NUL character in name/description gives 500 instead of 400 | fixed | commit 'M8 review: fix BF-3, BF-4, BF-6' |
| SF-M1 | MAJOR | backend | evaluation/FlagCacheService.java:252-260 | 7.2, 9.3 | Reconcile can report false drift for a write committed during the snapshot; comment says it cannot | open | Known limit documented in code and D-034; value stays correct |
| SA-5 | MAJOR | backend | auth/HmacJwtEncoder.java | 5.1 vs 5.2 | Custom encoder instead of NimbusJwtEncoder (ESC-003 open) | open | Waits for ESC-003 |
| SA-6 | MAJOR | backend | auth/ClientRegistrationProperties.java:32-45 | 5.1 | Extra startup failures (blank admin password, blank client secret) decided at Level 1 | escalated | ESC-007 |
| SA-7 | MAJOR | docs | DECISIONS D-018, D-021, D-023 | 12.5 | Level 1 entries that touch the API | escalated | ESC-007 |
| FF-1 | MAJOR | frontend | hooks/queries.ts:146-150 | 8.5, AC-FLAG-3 | Refetch after one toggle can flip another in-flight optimistic toggle back | fixed | commit 'M8 review: fix FF-1..FF-4, TA-2' (D-035) |
| FF-2 | MAJOR | frontend | features/audit/AuditPage.tsx:57 | 8.6 | Load more by offset shows duplicate rows when new events arrive | fixed | commit 'M8 review: fix FF-1..FF-4, TA-2' (D-035) |
| FF-3 | MAJOR | frontend | components/Modal.tsx:17, GroupDialog, FlagDialog | 8.5 | Closing a dialog while its request runs loses a later field error (no toast) | fixed | commit 'M8 review: fix FF-1..FF-4, TA-2' (D-035) |
| FF-4, SF-M3 | MAJOR | frontend | hooks/queries.ts (delete hooks), DeleteFlagDialog, DeleteGroupDialog | 8.5 | Delete of an already deleted item: dialog stays, row stays, technical toast | fixed | commit 'M8 review: fix FF-1..FF-4, TA-2' (D-035) |
| TA-2 | MAJOR | tests | flags.test.tsx:259-268, GroupsPage.test.tsx:341-355 | AC-FLAG-6 | Refetch after 409 is asserted with data that is already on screen | fixed | commit 'M8 review: fix FF-1..FF-4, TA-2' (D-035) |
| TA-3, PT-3 | MAJOR | tests | evaluation/EvaluationIT.java:338-349 | 7.2 | Failed reconcile: ERROR level, ETag and last-success gauge not asserted | fixed | commit 'M8 review: tests for TA-3, PT-1, PT-2, PT-4' (fails if logged at WARN) |
| PT-1 | MAJOR | tests | FlagCacheServiceTest.java:229-252, EvaluationIT.java:200-211 | 7.2, AC-CACHE-4 | Deletes not checked to leave other entries in place | fixed | commit 'M8 review: tests for TA-3, PT-1, PT-2, PT-4' |
| PT-2 | MAJOR | tests | FlagCacheServiceTest | 7.2 | No concurrent onChange test for one group | fixed | commit 'M8 review: tests for TA-3, PT-1, PT-2, PT-4' (fails without the writer lock) |
| PT-4 | MAJOR | tests | AdminGroupsIT, GroupServiceTest:94 | 9.1, 9.2 | Group limit, real optimistic-lock 409 and real unique-violation 409 not tested under concurrency | fixed | commit 'M8 review: tests for TA-3, PT-1, PT-2, PT-4' |
| IF-1 | MAJOR | infra | frontend/nginx.conf:18 | 10.2 | nginx resolves BACKEND_URL once; a recreated backend gives 502 | open | |
| IF-2 | MAJOR | infra | frontend/nginx.conf:19 | 10.2 | `Host: $http_host` breaks a BACKEND_URL behind a host-routing ingress; no SNI | open | |
| IF-3 | MAJOR | infra | frontend/nginx.conf:13 | 9.1, 9.2 | nginx answers bodies > 1 MB with an HTML 413 | open | |
| IF-4 | MAJOR | infra | frontend/Dockerfile:22-23 | 10.2 | Non-root nginx on port 80 fails without unprivileged-port sysctl (Kubernetes) | open | |
| IF-5 | MAJOR | infra | docker-compose.yml, backend/Dockerfile | 9.4 | `SERVER_PORT` other than 8080 breaks healthcheck and UI proxy | open | |
| IF-6 | MAJOR | infra | Makefile:5,15-16 | 10.3, AC-OPS-1 | `make up` outside a git checkout fails at the sha tag | open | |
| IF-7 | MAJOR | infra | backend/Dockerfile:9-13 | 9.6 | Image built without GIT_COMMIT has no git.commit.id, silently | open | |
| IF-8 | MAJOR | gates | scripts/lib/trace-registry.mjs:60-79 | 11.4 | 9.1 rows that share a status (409 duplicate-key / limit-reached) are not checked per type | open | |

MINOR (optional; listed for the owner)

| ID(s) | Area | File:line | Problem | Status |
| --- | --- | --- | --- | --- |
| DB-1 | backend | GroupService.java:61 | GET /groups aggregates all flags in memory (300 ms target at 100k flags not measured) | open |
| DB-2 | backend | AuditEventRepository.java:18 | Bound LIKE pattern may skip the pattern index on a generic plan | open |
| DB-3 | backend | V1:41 | No CHECK on `action` (spec does not ask) | open |
| JR-5, BF-10 | backend | FlagCacheService.java:216-220, TokenController.java:100-107 | Duplicated / stale Javadoc | open |
| JR-6 | backend | FlagCacheService.java:94-97 | Revision restarts from max(audit id); can repeat after reconcile + restart | open |
| JR-7, SR-2 | backend | RequestIdFilter.java:37-50, Problems.java:30 | Request id not length-capped; X-Forwarded-Prefix shows in logs / `instance` | open |
| BF-7 | backend | TokenController.java:88-98 | Wildcard content types and query-string params accepted | open |
| BF-8 | backend | AuditService.java:47 | `ff_admin_writes_total` counted before commit | open |
| BF-9 | backend | SecurityConfig.java:57-122 | Method longer than 50 lines | open |
| SA-8 | backend | SecurityConfig.java | CORS values not compared case-insensitively | open |
| SR-1 | docs | README.md:37 | README should say the ingress must overwrite X-Forwarded-* and hide ports 8080/80 | open |
| SF-m3 | backend | AuditPurgeJob.java:44-53 | Normal INFO line after a failed purge | open |
| SA-9 | frontend | D-029, D-030 | Extra UI texts (logged) | open |
| RR-1 | frontend | Modal.tsx:23 | First focus on Close, not the first field | open |
| RR-2 | frontend | useFocusTrap.ts:60 | Focus lost after deleting a group | open |
| RR-3 | frontend | AuditPage.tsx:13 | One request per keystroke; table flickers | open |
| RR-4 | frontend | LoginPage.tsx:34 | Blank username/password shows "Sign in failed" | open |
| RR-5 | frontend | FlagTable.tsx:20 | Section and table share the name "Flags" | open |
| RR-6, FF-5 | frontend | ConfirmDialog.tsx:70-78 | Cancel works while a delete runs | fixed |
| FF-6 | frontend | RequireAuth.tsx:6-11 | Expired redirect does not clear the query cache | open |
| FF-7 | frontend | queries.ts:63-70 | Extra GET of the deleted group | open |
| FF-8 | frontend | GroupPanel.tsx and others | Components longer than 50 lines | open |
| FF-9 | frontend | FlagTable.tsx:92 | Relative times only refresh on re-render | open |
| SF-m1 | frontend | lib/errors.ts:35-42 | Raw server detail in toasts | open |
| SF-m2 | frontend | GroupsPage.tsx:23 | Failed background refetch keeps stale data silently | open |
| SF-m4 | frontend | auth/token.ts:13-19 | Blocked sessionStorage loops login silently | open |
| DC-1 | frontend | AppShell.module.css:83 | Footer cuts the white groups pane 40 px short | open |
| DC-2 | frontend | GroupDialog.tsx:94,165; FlagDialog.tsx:176 | "(optional)" not muted in three dialogs | open |
| DC-3 | frontend | DeleteGroupDialog.tsx:49 | Empty list box for a group with 0 flags | open |
| TA-4 | gates | backend/pom.xml:214-215 | JaCoCo class-name scope not recorded in DECISIONS | open |
| TA-5 | tests | e2e/ui/groups.spec.ts:150-153 | deletedFlags order not checked in e2e | open |
| TA-6, IF-10 | tests | scripts/smoke.sh:61 | Condition always true; only log order checked | open |
| TA-7 | tests | e2e/api/evaluation.spec.ts:79-81 | Client 200 case checks only shapes | open |
| PT-m1..m4 | tests | EvaluationIT, FlagCacheServiceTest | Exact drift count, revision from DB, AC-CACHE-2 via reflection, create checks only one endpoint | open |
| IF-9 | gates | check-api-contract.sh:11 | `git diff` against the index, not HEAD | open |
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
