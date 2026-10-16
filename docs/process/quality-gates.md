# Quality gates

From spec 001 (`docs/SPEC.md`) section 11.3. Section numbers are kept, so references
such as "spec 9.1" still point here. Map of all sections: `docs/README.md`.

### 11.3 Automated quality gates

One command, `make verify`, runs every gate below in order and is the agent's single source of truth for "done": it exits non-zero on the first failing gate and writes `build/verify-report.json` and `build/verify-report.md` (per gate: status, duration, failing test names, first 30 lines of each error, affected acceptance-criterion IDs). `make verify-all` runs every gate even after a failure; `make verify-fast` runs only the gates that need no Docker (1–6, 9, 10, 14, 15, 16) for the inner edit loop.

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
| 10 | Secrets | gitleaks with an allowlist for the documented dev defaults; Trivy filesystem scan of Maven and npm dependencies; Trivy scan of both Docker images once they are built (full verify only) | Any other secret-looking string, or any HIGH or CRITICAL vulnerability. Two exceptions only, both listed in `.trivyignore` (format below): (a) a vulnerability with no fixed version, recorded by the agent in <spec>/DECISIONS.md with a reason and an expiry date; (b) a vulnerability the owner has accepted, listed in the "Accepted vulnerabilities" table below. Any other entry in `.trivyignore` fails the gate | M1 |
| 11 | Docker smoke test | `scripts/smoke.sh` against `docker compose up` | Any step fails: health UP within 90 s → admin login → client token → evaluate seeded `orders.new-checkout` = true → toggle via Admin API → evaluate returns false → readiness was DOWN before warm-up → every UI security header from 10.2 present (HSTS only when the request has `X-Forwarded-Proto: https`, 10.2) | M5 |
| 12 | End-to-end | Playwright, `--repeat-each=2`, retries 0 | Any failure, including a test that passes once and fails once (flaky), or any Content-Security-Policy violation reported in the browser console | M8 |
| 13 | Performance | k6 script `perf/evaluate.js`, 200 req/s for 60 s against compose | Any error, or cache hit rate < 99 %. The p95 is measured and reported against the 50 ms target but does not fail the gate (9.2, decision 0007) | M8 |
| 14 | Traceability | `scripts/check-traceability.mjs` (section 11.4); runs the gate 16 self-test first (`scripts/test/check-test-changes.test.mjs`, spec 004) | Any acceptance criterion without a passing test, or a test tagged with an unknown ID | M1 |
| 15 | Test integrity | `scripts/check-integrity.mjs` | Any `@Disabled`, `assumeTrue(false)`, `.skip(`, `.only(`, `xit(`, `test.fixme`; any `eslint-disable` or `@SuppressWarnings` without a `// reason:` comment; thresholds in config files differing from this spec; coverage, mutation, lint or ArchUnit exclusions not listed in `<spec>/DECISIONS.md` | M1 |
| 16 | Test changes | `scripts/check-test-changes.mjs` (spec 004, decision 0012) | A test changed against `main` beyond the plumbing clause, unless one of its tags on `main` is listed in the active spec's `<spec>/test-changes.md`; a deleted test file or a removed tag; a changed `docs/acceptance-criteria.md` line whose AC ID is not listed | M1 |

A gate not yet active passes trivially but is already wired into `make verify` from M1, so later milestones only add tests, never plumbing.

**Accepted vulnerabilities (gate 10).** The owner has reviewed these and accepts the risk. They are excluded from the Trivy report in `make verify` and in CI. The agent adds them to `.trivyignore` and never removes them. Only the owner changes this table (decision record in `decisions/`).

Reason for every row: known, risk accepted by the owner (`decisions/0008`). Review by 2027-04-10 for every row.

| # | CVE | Severity | Where | Package | Installed | Fixed in |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ~~CVE-2026-47884~~ | CRITICAL | fixed in 1.1.0 (spec 002, Spring Framework 7.0.9); entry removed from `.trivyignore` | spring-webmvc | 6.2.19 | 7.0.9 |
| 2 | ~~CVE-2026-47890~~ | CRITICAL | fixed in 1.1.0 (spec 002, Spring Framework 7.0.9); entry removed from `.trivyignore` | spring-webmvc | 6.2.19 | 7.0.9 |
| 3 | CVE-2026-78667 | HIGH | backend image, `/usr/bin/pebble` | Go stdlib | 1.26.7 | 1.26.9 / 1.27.2 |
| 4 | CVE-2026-78669 | HIGH | backend image, `/usr/bin/pebble` | Go stdlib | 1.26.7 | 1.26.9 / 1.27.2 |
| 5 | CVE-2026-97031 | HIGH | backend image, `/usr/bin/pebble` | Go stdlib | 1.26.7 | 1.26.9 / 1.27.2 |
| 6 | CVE-2026-4775 | HIGH | UI image (alpine 3.23.3) | tiff | 4.7.1-r0 | 4.7.2-r0 |

`.trivyignore` format, one entry per vulnerability. The four open entries for this table (rows 3 to 6):

```
# <reason> — "owner accepted, spec 11.3" or "no fixed version, DECISIONS.md #n"
CVE-2026-47884 exp:2027-04-10
CVE-2026-47890 exp:2027-04-10
CVE-2026-78667 exp:2027-04-10
CVE-2026-78669 exp:2027-04-10
CVE-2026-97031 exp:2027-04-10
CVE-2026-4775 exp:2027-04-10
```

The `exp:` date is the review date. When it passes, Trivy reports the vulnerability again; the owner then renews the entry or removes it.
