# 0010 — One folder per spec; parallel builder sessions

Status: **Accepted**
Decided by: owner (@brakebg), 2026-10-10
Changes: `docs/specs/` layout; spec 001 memory files moved; `CLAUDE.md`; `docs/SPEC.md` 12.5,
12.6; `docs/builder-prompt.md`; `docs/builder-agents.md`; `.claude/agents/*`; gate scripts
(`scripts/lib/active-spec.mjs` new, `verify.mjs`, `check-integrity.mjs`,
`check-traceability.mjs`, `lib/trace-registry.mjs`); `scripts/locked-paths.txt`. Refines 0009.

## 1. Decision

- Every spec has its own folder `docs/specs/<NNN-name>/` with its owner files (`spec.json`,
  `SPEC.md`, `acceptance-criteria.md`) and all agent memory of that spec (`STATE.md`,
  `PROGRESS.md`, `DECISIONS.md`, `BLOCKERS.md`, `MILESTONE`, `escalations/`, `reviews/`,
  `verify-report.md`). No agent memory file is shared between specs.
- The kickoff and resume prompts name the spec folder. One session = one spec = one branch =
  one PR. Several sessions can run at the same time on different specs.
- Spec 001's files moved from `docs/` to `docs/specs/001-feature-flag-service/` (history kept,
  `git mv`). `docs/SPEC.md` and `docs/acceptance-criteria.md` stay where they are: they are the
  product spec and its registry.
- `scripts/current-milestone` is replaced by `<spec>/MILESTONE` ("<n>" or "<n> complete").
  The gates find the active spec from the branch or `FF_SPEC` (`scripts/lib/active-spec.mjs`).
- A criterion due in milestone n is required once n is complete or a later milestone runs.
  Before, it was required from the start of milestone n, so a spec could not start green
  when its criteria need the whole milestone (spec 002: AC-UPG-1, AC-UPG-2).

## 2. Why

Shared, append-only files (`docs/STATE.md`, `docs/PROGRESS.md`, `docs/DECISIONS.md`,
`scripts/current-milestone`) conflict as soon as two sessions work at the same time, and make
it unclear which spec a line belongs to.

## 3. Consequences

- Decision and escalation numbers start at 1 per spec; outside the folder they are written
  with the spec id (`002/D-3`).
- Shared code files can still conflict; each session merges `origin/main` before its PR is
  ready (`CLAUDE.md` 9).
- Parallel sessions need separate machines (fixed gate ports).
