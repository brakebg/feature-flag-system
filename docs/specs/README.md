# Specs

Owner-authored, locked. Decision: `decisions/0010-spec-folders.md`.

One folder per spec. A builder session works on exactly one spec: the folder its kickoff or
resume prompt names. Everything that session writes about its work stays in that folder, so
several sessions can run at the same time on different specs without touching each other's
files.

## Index

| # | Folder | Spec file | Milestones | Branch | Status |
| --- | --- | --- | --- | --- | --- |
| 001 | [001-feature-flag-service](001-feature-flag-service/) | `docs/SPEC.md` (the product spec) | M1–M8 | `feature/feature-flag-service` | Done, merged (PR #3), release 1.0.0 |
| 002 | [002-spring-boot-4-upgrade](002-spring-boot-4-upgrade/) | `SPEC.md` | M9 | `feature/spring-boot-4` | Ready |

The owner adds a row for each new spec and gives it the next free milestone numbers.

## Folder layout

```
docs/specs/<NNN-name>/
  spec.json                 owner   id, title, spec file, registry file, branch, milestones
  SPEC.md                   owner   the spec (spec 001: docs/SPEC.md instead)
  acceptance-criteria.md    owner   AC IDs of this spec, "Due milestone: <n>" (spec 001: docs/acceptance-criteria.md)
  STATE.md                  agent   where the work stands, next 3 steps (< 150 lines)
  PROGRESS.md               agent   one row per commit of this spec
  DECISIONS.md              agent   Level 1 decisions and escalation answers (D-1, D-2, ...)
  BLOCKERS.md               agent   Level 2 blockers
  MILESTONE                 agent   "<n>" while milestone n runs, "<n> complete" when done
  escalations/ESC-NNN.md    agent   Level 3 escalations (ESC-001, ... per spec)
  reviews/                  agent   audit and review reports
  verify-report.md          agent   last full verify report (copied from build/)
```

Agent files are created by the session in its first commit. Owner files are locked
(`scripts/locked-paths.txt`).

## Rules

1. Spec 001 (`docs/SPEC.md`) defines the product and the working rules (its section 12). It
   stays valid. A later spec changes only what it names; for those items it wins while it is
   active. After its PR is merged, the owner folds the changes into `docs/SPEC.md`.
2. One spec = one session at a time = one branch = one PR to `main`.
3. A session reads and writes only its own folder. Another spec's folder is read-only for it.
4. Numbers are per spec: decisions `D-<n>`, escalations `ESC-<NNN>`. In text outside the
   folder, write them with the spec id, for example `002/D-3`, `002/ESC-001`. The escalation
   workflow works per PR, so equal numbers in two PRs do not clash.
5. Milestone numbers are global and given by the owner in this index (M9, M10, ...), so the
   gate scripts keep one order.
6. Gates find the active spec from the branch (`spec.json` `branch`) or from `FF_SPEC=<id>`
   (`scripts/lib/active-spec.mjs`). On `main` no spec is active and the highest `MILESTONE`
   of all specs applies. A spec's criteria count on a branch when it is the active spec or
   when its `MILESTONE` file exists there (its work is merged).
7. Shared files (code, `VERSION`, `CHANGELOG.md`, `backend/openapi.json`, `docs/acceptance-criteria.md`)
   can change in parallel specs. Before a session marks its PR ready it merges `origin/main`
   into its branch, resolves conflicts and runs `make verify-all` again.
8. Parallel sessions need separate machines (cloud sessions are). On one machine the gate
   stacks use fixed ports and would collide.
