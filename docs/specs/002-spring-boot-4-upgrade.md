# Spec 002 — Spring Boot 4 upgrade (release 1.1.0)

Owner-authored, locked. Decision: `decisions/0009-upgrade-spring-boot-4.md`.
Milestone: **M9**. Working branch: **`feature/spring-boot-4`** (from `main`), one PR to `main`.

This is a change spec. It is executed by an autonomous builder session whose kickoff prompt
names this file. Every "MUST" is a hard requirement.

## 1. Relation to `docs/SPEC.md`

- `docs/SPEC.md` (spec 001) still defines the product: behaviour, API, UI, data, security,
  gates, thresholds and the working rules (its section 12). All of it still applies.
- This spec changes only what it names below. Where it names a part of `docs/SPEC.md`, this
  spec wins for M9:

| `docs/SPEC.md` part | Change in M9 |
| --- | --- |
| 2 Tech stack: Framework | Spring Boot 4.1.x (was 3.x) |
| 2 Tech stack: Backend tests | JUnit as managed by Boot 4.1 (JUnit 6), Testcontainers 2 |
| 2 Tech stack: new JSON row | Jackson 3 (`tools.jackson.*`); annotations stay `com.fasterxml.jackson.annotation` |
| 9.6 Versioning | This release is `1.1.0` |
| 11.3 Accepted vulnerabilities, rows 1 and 2 | End with M9: the agent removes their `.trivyignore` lines (section 5 item 4). The only case where the agent removes an accepted entry |
| 12.2 Milestones, 12.5 flow step 1, 12.6 startup step 2 | Milestone M9, branch `feature/spring-boot-4` instead of `feature/feature-flag-service` |

- After the PR is merged, the owner updates `docs/SPEC.md` to match (section 2 rows, 11.3
  table). The agent never edits `docs/SPEC.md` or this file.

## 2. Goal

Move the backend from Spring Boot 3.5 to Spring Boot 4.1 and release `1.1.0`, with **no change
in behaviour**. Reasons: CVE-2026-47884 and CVE-2026-47890 (CRITICAL, spring-webmvc 6.2.19) are
fixed only in Spring Framework 7.0.9; Spring Boot 3.5 is the last 3.x line; Jackson 3 is the
Boot 4 default.

Not in scope: new features, frontend dependency changes, Java version change, base image
change, database schema change.

## 3. Target versions

Latest patch of each line at build time, pinned exactly (spec 001 section 2).

| Item | 1.0.0 | 1.1.0 |
| --- | --- | --- |
| Spring Boot (parent) | 3.5.16 | 4.1.x |
| Spring Framework / Spring Security | 6.2.19 / 6.5.x | 7.0.x (7.0.9 or later) / 7.1.x |
| Jackson | 2.x (`com.fasterxml.jackson`) | 3.x (`tools.jackson`), annotations unchanged |
| Hibernate ORM / Tomcat / Flyway | 6.6 / 10.1 / 11 | as managed by Boot 4.1 (7.x / 11.0 / 12.x) |
| JUnit / Testcontainers | 5 / 1.21 | as managed by Boot 4.1 (6.x / 2.x) |
| springdoc-openapi | 2.8.x | the line that supports Boot 4.1 |
| Java / PostgreSQL / Temurin image | 21 / 16 / 21 JRE | unchanged |
| Frontend | — | unchanged |

Version properties that override a Boot-managed version (`docs/DECISIONS.md` D-007: Jackson,
Tomcat, PostgreSQL driver) are removed when Boot 4.1 manages a version without a HIGH or
CRITICAL vulnerability. An override that stays needs a new `DECISIONS.md` entry and must stay
inside the line Boot 4.1 manages.

## 4. MUST stay the same

The upgrade is proven by these. Breaking one is a bug in the upgrade.

1. Every acceptance criterion in `docs/acceptance-criteria.md` and every error-case ID passes.
   No test is deleted, skipped, loosened or re-baselined. A test changes only where an API of a
   new library forces it (package, annotation or class names); expected values and assertions
   stay the same. Each changed test is listed in `docs/DECISIONS.md`: file, old line, new line,
   the library change that forced it.
2. HTTP behaviour is the same for clients, byte for byte: status codes; headers (`Cache-Control`,
   `ETag`, security headers, `X-Request-Id`); problem details (spec 001 9.1); JSON field names,
   field order, `null` handling and number format; ISO-8601 UTC date-times; JWT claims and
   lifetimes; `/actuator/*` responses (9.3).
3. `backend/openapi.json` stays OpenAPI 3.0.x. Paths, operations, parameters, request bodies,
   responses and schemas do not change. Only generator noise (ordering, `info`, tool version)
   may change; each changed hunk is listed in `DECISIONS.md`. The regenerated
   `frontend/src/api/schema.d.ts` compiles with the UI code unchanged.
4. Logs keep the 9.3 content: ECS JSON, one line per request with method, path, status,
   duration and correlation id.
5. All 15 gates, every threshold and the banned-dependency lists stay as they are. If a gate
   tool (PIT, JaCoCo, ArchUnit, Spotless, Enforcer, Trivy, gitleaks) does not support the new
   platform, that is a Level 3 escalation; the gate is never dropped, skipped or weakened.
6. No new Flyway migration. V1 and V2 apply unchanged (same checksums) on an empty database
   and on a database created by 1.0.0.
7. The black-box acceptance suite v1 (owner review, `docs/VALIDATION.md`) passes against the
   M9 build, with no change to the suite.

## 5. Work items

1. Backend builds on Spring Boot 4.1.x with the section 3 versions; all gates green.
2. Backend main and test code on Jackson 3. New ArchUnit rule in gate 3 (`ArchitectureTest`):
   no class imports `com.fasterxml.jackson.core..` or `com.fasterxml.jackson.databind..`;
   `com.fasterxml.jackson.annotation` is allowed. Tagged `AC-UPG-2`.
3. `AC-UPG-1`: a backend test reads the versions at runtime from the libraries (for example
   `SpringBootVersion`, `SpringVersion`, `SpringSecurityCoreVersion`, Jackson's version class)
   and checks them against section 3.
4. `.trivyignore`: remove the lines for CVE-2026-47884 and CVE-2026-47890. Gate 10 (fs and
   images) passes without them. No new entry for a vulnerability that has a fixed version.
5. `VERSION` = `1.1.0` (and the versions that gate 15 compares with it). `CHANGELOG.md`
   gets a `1.1.0` section: Changed (platform versions), Security (the two CVEs). README: stack
   versions only.
6. `scripts/current-milestone` = `9`.
7. Docker: base images unchanged; `/actuator/info` shows `build.version` `1.1.0` and
   `git.commit.id`.

## 6. Acceptance criteria

Registered in `docs/acceptance-criteria.md` (section "Spec 002"), due M9.

- **AC-UPG-1** · The running backend reports Spring Boot 4.1.x, Spring Framework 7.0.9 or later,
  Spring Security 7.1.x and Jackson 3.x, read at runtime from the libraries (not from build files).
- **AC-UPG-2** · No backend class imports Jackson 2 `com.fasterxml.jackson.core` or
  `com.fasterxml.jackson.databind` (ArchUnit, gate 3).

## 7. Process

- Rules of spec 001 section 12 and `CLAUDE.md` apply: small green chunks (about 300 lines),
  `make verify-fast` per chunk, push at once, red work only on `wip/`, owner comments before
  every commit, escalation flow 12.5.
- Commit messages: `M9 <area>: <what changed>`, trailers `AC:` and `Spec: 002 §<n>`.
- First commit: `docs/STATE.md` for M9 (active spec `docs/specs/002-spring-boot-4-upgrade.md`,
  milestone M9, next 3 steps) and `scripts/current-milestone` = `9`. After the first green
  chunk, open one draft PR `feature/spring-boot-4` → `main`.
- Suggested chunk order: (a) build file and Boot 4 starters, code compiles; (b) Jackson 3 in
  main code; (c) tests on Jackson 3, Testcontainers 2, JUnit 6; (d) security and web
  configuration until gate 5 is green; (e) gates 6 to 8; (f) Docker images, gates 10 to 13;
  (g) AC-UPG-1, AC-UPG-2, `.trivyignore`, VERSION, CHANGELOG, README.
- Review: milestone audit (`docs/builder-agents.md` section 5, row M9) with the agents in
  section 7a. Fix every BLOCKER and CRITICAL before `M9: complete`. No separate final review.
- The limit of 20 failed `make verify` runs (spec 001 12.4) counts from the start of M9.

### 7a. Session environment: cloud, no plugins

M9 runs in an Anthropic cloud session (`docs/builder-prompt.md` phase 2). No plugins are
installed there. This overrides every mention of vendor agents in `docs/builder-agents.md`
and `CLAUDE.md` 6a for M9.

| Kind | Examples | In M9 |
| --- | --- | --- |
| Project agents (`.claude/agents/`, part of the repo) | `spec-auditor`, `test-auditor`, `security-reviewer`, `final-reviewer` | Available. Use them as `docs/builder-agents.md` says |
| Built-in agents | `Explore` | Available, search only |
| Vendor agents (plugins) | `ecc:*` (for example `ecc:java-reviewer`), `pr-review-toolkit:*`, any other `<plugin>:<agent>` | **Not in this session. Do not call or look for them** |

- The Java / Spring review that `ecc:java-reviewer` gives in the local phase is done by the
  project agent `final-reviewer` with scope `backend` (git range of M9). Brief it per
  `docs/builder-agents.md` section 3 and name the Spring Boot 4 areas of section 9.
- In the audit report, write "vendor agents: not available (cloud session)". That is the
  expected state, not a gap, a blocker or a reason to escalate.
- If a project agent cannot be started, that is a Level 3 escalation (trigger 4,
  environment). Never skip the audit and never review your own work instead.

**Level 3 triggers for M9** (in addition to spec 001 12.5)

- A library change would break an item of section 4.
- A needed library has no release that supports Spring Boot 4.1.
- The only way to make a gate green is a new exception, exclusion or `.trivyignore` entry.

## 8. Definition of done

- [ ] `make verify-all` passes from a fresh clone, all 15 gates green, on Spring Boot 4.1.x;
      report committed as `docs/verify-report.md`.
- [ ] Traceability shows every criterion (including AC-UPG-1 and AC-UPG-2) and every error-case
      ID with at least one passing test.
- [ ] `.trivyignore` has no line for CVE-2026-47884 or CVE-2026-47890; Trivy reports neither.
- [ ] The `backend/openapi.json` diff against 1.0.0 is only allowed noise, listed in
      `DECISIONS.md`.
- [ ] `VERSION` is `1.1.0`; CHANGELOG has the `1.1.0` section; `/actuator/info` shows `1.1.0`.
- [ ] Every test changed in M9 is listed in `DECISIONS.md`; gate 15 shows no violation.
- [ ] Milestone audit done, no open BLOCKER or CRITICAL; no open escalation; no `wip/` branch
      left; `docs/STATE.md` shows M9 complete.
- [ ] The PR `feature/spring-boot-4` → `main` is marked ready for review, with a final summary
      comment: what changed, versions before and after, gate results, changed tests, decisions.

Then the session stops. The owner validates with `scripts/owner-review.sh` and the black-box
suite v1, the same way as for 1.0.0.

## 9. Migration notes (informative)

Check each against the Spring Boot 4.0 and 4.1 migration guides and release notes.

- Boot 4 is split into smaller modules. Several starters and test annotations moved or were
  renamed (web MVC starter, test starters per technology, the package of
  `@AutoConfigureMockMvc`). Flyway needs the Boot Flyway starter; `flyway-core` alone is no
  longer auto-configured.
- Jackson 3: `ObjectMapper` becomes `JsonMapper` (immutable, builder); exceptions are
  unchecked; some defaults differ (for example property order and date-time format). Check
  every default against section 4 item 2 and configure Jackson to keep the 1.0.0 output.
- Spring Security 7: methods deprecated in 6.x are removed; only the lambda DSL remains.
- Testcontainers 2: new artifact ids and packages (`testcontainers-postgresql`,
  `org.testcontainers.postgresql.PostgreSQLContainer`).
- Hibernate 7 / Jakarta Persistence 3.2: check `@Version` optimistic locking, the audit
  `details` JSONB column and the prefix search on the `varchar_pattern_ops` index.
- Tomcat 11: check the body-size filter (9.2), chunked bodies and the 413 answer and headers.
- Structured logging: check the ECS field names against 9.3.
