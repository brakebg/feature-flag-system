# 0009 — Spec 002: upgrade the backend to Spring Boot 4.1

Status: **Accepted**
Decided by: owner (@brakebg), 2026-10-10
Changes: new `docs/specs/` folder and spec 002; `CLAUDE.md`; `docs/acceptance-criteria.md`;
`docs/builder-agents.md`; `docs/builder-prompt.md`; `scripts/locked-paths.txt`;
`scripts/owner-review.sh`; `scripts/lib/trace-registry.mjs`; `scripts/check-traceability.mjs`

## 1. Decision

- Work after 1.0.0 is described in change specs under `docs/specs/`. The kickoff prompt of a
  builder session names the one spec it works on. `docs/SPEC.md` stays the product spec (001).
- Spec 002 moves the backend from Spring Boot 3.5 to Spring Boot 4.1.x (Spring Framework
  7.0.x, Spring Security 7.1.x, Jackson 3, JUnit 6, Testcontainers 2) as milestone M9 on the
  new branch `feature/spring-boot-4`, release 1.1.0, with no change in behaviour.

## 2. Why

- CVE-2026-47884 and CVE-2026-47890 (CRITICAL, spring-webmvc 6.2.19) are fixed only in
  Spring Framework 7.0.9. Decision 0008 accepted them for a limited time; spec 002 removes them.
- Spring Boot 3.5 is the last 3.x line. Jackson 3 is the Boot 4 default; Boot 4's Jackson 2
  support is deprecated.
- A separate spec file keeps the product spec stable and tells the session exactly what to do.

## 3. Consequences

- `docs/specs/**` is locked (owner only), like `docs/SPEC.md`.
- New criteria AC-UPG-1 and AC-UPG-2, due M9. Gate 3 gets a rule against Jackson 2 imports.
- Rows 1 and 2 of the spec 001 11.3 accepted-vulnerability table end with M9; the agent
  removes their `.trivyignore` lines. The other rows stay.
- PR #3 (`feature/feature-flag-service`) is merged and closed for work.
- The owner validates M9 the same way as 1.0.0: `scripts/owner-review.sh` (now defaults to
  `origin/feature/spring-boot-4`) with the unchanged black-box suite v1.
- After the M9 PR is merged, the owner updates `docs/SPEC.md` (section 2 rows, 11.3 table).
