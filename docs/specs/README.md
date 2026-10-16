# Specs

Owner-authored, locked. Decision: `docs/process/decisions/0010-spec-folders.md`.

One folder per spec. A builder session works on exactly one spec: the folder its kickoff or
resume prompt names. Everything that session writes about its work stays in that folder, so
several sessions can run at the same time on different specs without touching each other's
files.

## Which specs exist

`spec.json` is the only place for a spec's facts (id, title, owner, status, milestones, branch,
issue, release, scope). For the table of all specs run:

```
node .claude/skills/write-spec/spec-tool.mjs list
```

## Starting a spec

The owner starts every new spec with `/write-spec` (`.claude/skills/write-spec/`). It gives the
next free spec, milestone and decision numbers, writes the spec files from one template, adds
the spec facts, runs a two-reviewer challenge on the design and checks the folder.

Every change spec has the sections "Test changes" (the AC IDs whose tests may change, in
`test-changes.md`; all other tests only by the plumbing clause) and "Acceptance delta" (AC IDs
added, changed and retired for the black-box suite).

## The criteria: `docs/project-acceptance-criteria.md`

That file is the one complete list of the product's criteria; the black-box acceptance suite
(`docs/process/validation.md`) is built from it alone. A spec adds its new criteria there, and
changes existing ones in place (same ID, same position, version +1, `changed by spec <NNN>`).
Every line names its spec and its due milestone; format and rules in
`docs/process/traceability.md`. A spec has no criteria file of its own; its `SPEC.md` section 5
names the IDs.

## After a spec is merged (owner)

1. Apply the spec's section 1 table to `docs/product/` and `docs/architecture/`.
2. Update the acceptance suite from the criteria diff and tag it `spec-NNN` (acceptance repo,
   issue #29). The suite is ready before the builder session starts (#24).

## Folder layout

```
docs/specs/<NNN-name>/
  spec.json                 owner   id, title, owner, status (draft | ready | done), milestones,
                                    branch, issue, release, scope (optional)
  SPEC.md                   owner   the spec; section 10 is its decision log
  test-changes.md           owner   AC IDs whose tests may change; the fixed plumbing clause
  design/                   owner   designs, when the spec has any
  history/                  owner   old decision records that belong to this spec only
  builder/                  builder the builder's memory, nothing else:
    STATE.md                        where the work stands, next 3 steps (< 150 lines)
    PROGRESS.md                     one row per commit of this spec
    DECISIONS.md                    Level 1 decisions, stuck choices, escalation answers (D-1, ...)
    MILESTONE                       "<n>" while milestone n runs, "<n> complete" when done
    escalations/ESC-NNN.md          Level 3 escalations (ESC-001, ... per spec)
    reviews/                        audit and review reports
    verify-report.md                last full verify report (copied from build/)
```

Builder files are created by the session in its first commit, only inside `builder/`. Owner files are locked
(`scripts/locked-paths.txt`).

## Rules

1. `docs/product/` and `docs/architecture/` describe the current system; `docs/process/` holds
   the working rules. A later spec changes only what it names; for those items it wins while it
   is active. After its PR is merged, the owner folds the changes into those files.
2. One spec = one session at a time = one branch = one PR to `main`.
3. A session reads and writes only its own folder. Another spec's folder is read-only for it.
4. Numbers are per spec: decisions `D-<n>`, escalations `ESC-<NNN>`. In text outside the
   folder, write them with the spec id, for example `002/D-3`, `002/ESC-001`. The escalation
   workflow works per PR, so equal numbers in two PRs do not clash.
5. Milestone numbers are global (M9, M10, ...); `spec-tool.mjs next` gives the next free one, so
   the gate scripts keep one order.
6. Gates find the active spec from the branch (`spec.json` `branch`) or from `FF_SPEC=<id>`
   (`scripts/lib/active-spec.mjs`). On `main` no spec is active and the highest `builder/MILESTONE`
   of all specs applies. A spec's criteria count on a branch when it is the active spec or
   when its `builder/MILESTONE` file exists there (its work is merged).
7. Shared files (code, `VERSION`, `CHANGELOG.md`, `backend/openapi.json`, `docs/project-acceptance-criteria.md`)
   can change in parallel specs. Before a session marks its PR ready it merges `origin/main`
   into its branch, resolves conflicts and runs `make verify-all` again.
8. Parallel sessions need separate machines (cloud sessions are). On one machine the gate
   stacks use fixed ports and would collide.
