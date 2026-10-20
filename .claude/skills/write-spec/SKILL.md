---
name: write-spec
description: Owner-only. Writes a new change spec on its feature branch from TEMPLATE.md - spec.json, SPEC.md, test-changes.md, its versioned criteria lines and the product/architecture doc changes - with a two-reviewer challenge and a structural check, ready for the spec-NNN-ready tag (docs/process/spec-flow.md).
argument-hint: "<issue number, notes file, or short description of the change>"
disable-model-invocation: true
---

# /write-spec — start a new change spec

Owner-authored, locked (`scripts/locked-paths.txt`: `.claude/skills/**`).

Turn the owner's input (an issue, notes, or a short text in the arguments) into a complete
spec on its branch `feature/<name>`, which a builder session then continues
(`docs/process/spec-flow.md`). `TEMPLATE.md` holds every file. Write in plain, short English: the owner is not a native speaker
(`CLAUDE.md` section 10).

Files of this skill:

- `TEMPLATE.md` — every output file, with placeholders.
- `spec-tool.mjs` — `next` (numbers), `tests` (tagged tests per ID), `check` (validation),
  `list` (all specs).
  Run it from the repo root: `node .claude/skills/write-spec/spec-tool.mjs <cmd>`.

## Output (one pass, nothing else)

| File | Note |
| --- | --- |
| File | Note |
| --- | --- |
| `docs/specs/<NNN>-<name>/spec.json` | File 1: the spec's facts, `status` `draft` → `ready` |
| `docs/specs/<NNN>-<name>/SPEC.md` | File 2: the 10 sections, fixed order; section 10 is the decision log |
| `docs/specs/<NNN>-<name>/test-changes.md` | File 3: allowed AC IDs + fixed plumbing clause |
| lines in `docs/project-acceptance-criteria.md` | File 4: new criteria `v1`; changed ones in place, version + 1 |
| `docs/product/`, `docs/architecture/` | File 5: describe the system after this spec |
| `docs/specs/<NNN>-<name>/design/` | only when the spec has designs |

Never code, never a builder file: no `builder/` folder (the builder creates it). No decision
record and no index row: the spec's facts are in `spec.json`, its decisions in section 10.
Never edit `CLAUDE.md`, `docs/process/`, another spec's folder or any gate script.

## Procedure

### 1. Preconditions

1. Confirm this is an owner session (the owner typed `/write-spec`). In a builder session
   (a kickoff or resume prompt names a spec), stop and change nothing.
2. `git fetch origin --tags`. Read `CLAUDE.md`, `docs/process/spec-flow.md`,
   `docs/specs/README.md` and `docs/README.md` (where everything lives).
3. Ask the owner for the GitHub issue the spec is bound to. No issue: create one first
   (`gh issue create`) with the change statement, and use its number.

### 2. Numbers and branch

1. Run `node .claude/skills/write-spec/spec-tool.mjs next`. It scans the working tree and every
   branch (local and `origin`), so numbers held by another spec branch are skipped.
2. Choose `<name>` (short kebab-case). The branch is `feature/<name>`: the spec and the build
   share it.
3. Create `feature/<name>` from `origin/main`. Work only there.

### 3. Gather the change

1. Read the input: the issue body and comments by the owner, notes, linked decisions.
2. Read the `docs/product/` and `docs/architecture/` files the change touches and the code paths
   behind them (search, do not guess). List: which files change (section 1 table), what MUST
   stay the same, the design, the criteria.
3. Ask the owner only what the input and the code cannot answer. Ask few questions at a time.
   Everything still unclear goes to section 10 as an open question with options and a proposal.

### 4. Draft from `TEMPLATE.md`

Fill File 1 to File 5. Rules:

- Every behaviour change names an AC ID: a new criterion in File 4 and section 5, or a changed
  one in the section 5 table.
- A changed criterion keeps its ID and its place ("same ID, same position"), its version goes up
  by one and it says `changed by spec <NNN>`. Copy its current text into the "Now" column. Never
  give a changed old criterion a new ID.
- New IDs must not clash with any existing ID in `docs/project-acceptance-criteria.md`.
- No new, changed or retired ID at all: section 5 is the single line "No behaviour change."
  `check` fails on zero IDs without it.
- Acceptance delta (section 7): Added = all new IDs; Changed = all changed IDs, with what the
  black-box scenario gains; Retired = retired IDs with a reason each, or `none`. Each kind on
  one line; name no other IDs in that line.
- File 5: edit the `docs/product/` and `docs/architecture/` files so they describe the system
  after this spec, and name each one in section 1.
- Leave no `<!--` and no `<word>` placeholder outside code; only `<spec>`, `<n>`, `<area>`,
  `<what>` may stay.

### 5. Derive the test changes

1. Run `node .claude/skills/write-spec/spec-tool.mjs tests <every re-expressed or retired ID>`.
   It lists each test tagged with the ID (JUnit `@Tag("ID")`, `[ID]` in Vitest, Playwright and
   script test titles) and warns on an ID with no test.
2. Read each listed test. If it asserts the old behaviour, its ID goes into
   "Allowed behaviour changes". ERR IDs (`ERR-<METHOD>-<path>-<status>`) go there too when an
   error case changes. If no test asserts it, leave the ID out and say so in the
   PR body.
3. Put the `--md` output under "Tagged tests today" for reference. Copy the plumbing clause
   byte for byte. Name the same IDs in SPEC.md section 6.
4. A test that must change but carries none of these IDs means a behaviour change without an
   AC ID: go back to step 4 and name one.

### 6. Two-reviewer challenge

Spawn two agents in parallel (one message, two `Agent` calls, `subagent_type: general-purpose`).
Give each the path of the draft `SPEC.md`, its lines in `docs/project-acceptance-criteria.md`,
the touched `docs/product/` and `docs/architecture/` files and code paths, and this brief:

> Read-only review. Do not edit, create or delete any file, do not run git commands that change
> state. Review the draft spec for <change> with a **<bias>** bias. Find real problems only:
> wrong or missing behaviour, untestable or ambiguous criteria, a broken "MUST stay the same"
> item, a hidden change without an AC ID, a test-changes list that is too wide or too narrow.
> For each finding: section, problem, why it matters, a concrete fix. Max 10 findings, worst
> first. Plain English.

- Reviewer 1, **simplicity** bias: is there a smaller design that meets the goal? Anything
  built that the goal does not need? Fewer moving parts, fewer new IDs, fewer changed tests?
- Reviewer 2, **robustness** bias: failure modes, concurrency, upgrades, data limits,
  security, operations, and what the black-box suite cannot see.

Wait for both. For every finding: fix it in the draft, or reject it with a reason, or add it to
section 10 as an `**Open:**` question with options and a proposal. Do not drop a finding
silently. Name the challenge and its result in section 10.

### 7. Check

Run `node .claude/skills/write-spec/spec-tool.mjs check docs/specs/<NNN>-<name>` and fix every
`FAIL` until it passes. Exit codes: `0` pass, `1` problems, `2` only open owner questions left,
`3` usage error (wrong arguments or no such folder; the same for `next` and `tests`).
`--allow-open` accepts open questions for a draft PR. Read every `WARN` and decide.

The check covers: folder name and `spec.json` fields; id, branch and milestones unique across
all specs (also on other branches); no per-spec criteria file and no builder files; the criteria
file against the branch base (new lines `v1 · spec <NNN>`, changed lines version + 1 and
`changed by spec <NNN>`, retired lines `retired by spec <NNN>`, due milestone of this spec, no
other line touched, no line deleted); section 1 names every changed `docs/product/` and
`docs/architecture/` file; the 10 headings in order; section 5 and section 7 equal the criteria
diff; `test-changes.md` shape, plumbing clause and allowed IDs (all changed or retired);
section 10 items marked; status `draft` while items are Open; placeholders and comments left;
scope (only this spec's folder, the criteria file, `docs/product/` and `docs/architecture/`
changed since the branch left `main`). The last line lists the new, changed and retired IDs.

### 8. Commit, push, ready, tag

1. While section 10 has any `**Open:**` item, `spec.json` says `"status": "draft"`.
2. Commit: `Spec <NNN>: <Title>`, with the attribution trailer the session gives. Push
   `feature/<name>`. Do not open a PR: the builder opens the one PR of this branch.
3. Report to the owner: the files, the open questions of section 10, and the check result.
4. When the owner answers a question: mark it `~~question~~ **Decided: …**`, apply the answer in
   the sections it touches, re-run step 7, push.
5. When no item is Open and `check` exits 0: set `"status": "ready"`, commit
   `Spec <NNN>: ready`, push. Do not tag: the owner runs `/handover-check` on this commit
   first, and tags only the commit it says READY for (`docs/process/spec-flow.md` section 1,
   steps 2 and 3).
6. Never tag on your own, never move a tag, never merge. Next owner steps:
   `docs/process/spec-flow.md` section 1 (`/handover-check`, tag, acceptance tests, kickoff).
