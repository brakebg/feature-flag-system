# Session continuity

From spec 001 (`docs/SPEC.md`) section 12.6. Section numbers are kept, so references
such as "spec 9.1" still point here. Map of all sections: `docs/README.md`.

### 12.6 Session continuity

Any session can end at any time: cloud VMs are reclaimed after inactivity (uncommitted files, processes and containers are lost), sessions have been observed to disappear entirely after about 7 days, and long conversations are compacted. The repository is therefore the agent's only durable memory: a brand-new session with no history MUST be able to continue from the repository alone, losing at most one small chunk of work.

**Small green chunks**

1. A chunk is one small, self-contained change: one endpoint, one component, one migration, one group of tests. Aim for at most about 300 changed lines.
2. Per chunk: implement it with its tests → `make verify-fast` → green → update `<spec>/STATE.md` and append to `<spec>/PROGRESS.md` → commit → push immediately. Unpushed commits are never left behind.
3. Commit message: `M<n> <area>: <what changed>`, with a trailer `AC: AC-FLAG-3, AC-FLAG-4` naming the criteria it advances.
4. Only green chunks go to the PR branch. If a chunk cannot be made green before the session must stop, push it to `wip/<short-name>` instead, and list that branch in `STATE.md`.
5. Milestone end: set `<spec>/MILESTONE` to `<n> complete` → full `make verify` green → `STATE.md` updated → commit `M<n>: complete` → PR comment with a milestone summary (what was built, gate results, decisions, blockers).

**Memory files**

Every spec has its own folder `docs/specs/<NNN-name>/`, written `<spec>` below (`docs/specs/README.md`). Spec 001 (this document) uses `docs/specs/001-feature-flag-service/`. All agent memory of a spec lives in its folder and nowhere else, so several specs can be worked on in parallel, each in its own session, branch and PR, without touching each other's files. A session reads and writes only the folder of its active spec.

| File | Holds | Updated |
| --- | --- | --- |
| `CLAUDE.md` (repo root) | Startup ritual and the short form of the working rules; loaded automatically by Claude Code | M1, rarely after |
| `docs/product/`, `docs/architecture/`, `docs/process/` | The current product, architecture and process | Only by the owner |
| `<spec>/STATE.md` | Where the work stands and what comes next (below) | Every commit |
| `<spec>/PROGRESS.md` | Log of this spec only: date, commit, milestone, chunk, verify result | Every commit |
| `<spec>/MILESTONE` | `<n>` while milestone n runs, `<n> complete` when it is done; read by the gates | Milestone start and end |
| `<spec>/verify-report.md`, `<spec>/reviews/` | Final verify report and review reports of this spec | Milestone end, review |
| `<spec>/DECISIONS.md` | Level 1 decisions and escalation answers | When made |
| `<spec>/BLOCKERS.md` | Level 2 blockers | When raised or cleared |
| `<spec>/escalations/ESC-*.md` | Level 3 escalations | When raised or resolved |
| `docs/acceptance-criteria.md`, `<spec>/acceptance-criteria.md` | Criteria registries (product; per later spec), read-only for the agent | By the owner |
| `<spec>/spec.json`, `<spec>/SPEC.md` | Spec metadata (id, branch, milestones, registry) and the spec text | Only by the owner |

`<spec>/STATE.md` has these sections, kept under 150 lines (older detail lives in `PROGRESS.md`):

- Current milestone and chunk, with status.
- Last green commit SHA, and the last full `make verify` result per gate with its date.
- Chunks done in the current milestone, one line each.
- Next 3 steps, concrete enough to start without other context.
- Open escalations and blockers, one line each with ID.
- `wip/` branches and what each holds.
- Last processed PR comment (ID and time).
- Notes for the next session: environment quirks and lessons learned.

**Startup ritual** (in `CLAUDE.md`; run at the start of every session and again after any context compaction)

1. Read `CLAUDE.md`, `<spec>/STATE.md`, `<spec>/DECISIONS.md`, `<spec>/BLOCKERS.md` and every open `<spec>/escalations/ESC-*.md`; read the `docs/product/` and `docs/architecture/` files the next steps touch (map: `docs/README.md`).
2. `git fetch`, check out the spec's working branch (`<spec>/spec.json`), and confirm the branch head matches or follows the last green commit in `STATE.md`. If they differ, reconcile from `PROGRESS.md` before changing anything.
3. Read PR comments newer than the last processed one; apply any escalation answers (12.5 step 5).
4. Check each listed `wip/` branch: finish it, or record why it was dropped.
5. Run `make verify-fast` to confirm a green baseline.
6. Continue with the next steps in `STATE.md`.
