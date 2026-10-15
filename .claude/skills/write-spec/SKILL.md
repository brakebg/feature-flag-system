---
name: write-spec
description: Owner-only. Creates a new change spec folder (spec.json, SPEC.md, acceptance-criteria.md, test-changes.md, decision, index rows) on its own spec branch from TEMPLATE.md, with a two-reviewer challenge and a structural check.
argument-hint: "<issue number, notes file, or short description of the change>"
disable-model-invocation: true
---

# /write-spec — start a new change spec

Owner-authored, locked (`scripts/locked-paths.txt`: `.claude/skills/**`).

Turn the owner's input (an issue, notes, or a short text in the arguments) into a complete
spec folder that a builder session can run. Specs 002 and 003 are the models; `TEMPLATE.md`
generalises them. Write in plain, short English: the owner is not a native speaker
(`CLAUDE.md` section 10).

Files of this skill:

- `TEMPLATE.md` — every output file and the two index rows, with placeholders.
- `spec-tool.mjs` — `next` (numbers), `tests` (tagged tests per ID), `check` (validation).
  Run it from the repo root: `node .claude/skills/write-spec/spec-tool.mjs <cmd>`.

## Output (one pass, nothing else)

| File | Note |
| --- | --- |
| `docs/specs/<NNN>-<name>/spec.json` | id, title, spec, acceptance, branch, milestones |
| `docs/specs/<NNN>-<name>/SPEC.md` | the 10 sections of `TEMPLATE.md`, fixed order |
| `docs/specs/<NNN>-<name>/acceptance-criteria.md` | new AC IDs, `Due milestone: <M>` |
| `docs/specs/<NNN>-<name>/test-changes.md` | allowed AC IDs + fixed plumbing clause |
| `decisions/<DDDD>-<name>.md` | the owner decision |
| section in `docs/acceptance-criteria.md` | the new IDs, verbatim, under `## <Title> (spec <NNN>, due milestone <M>)`; re-expressed IDs updated in place (File 6, issue #31) |
| row in `docs/specs/README.md` | Index table |
| row in `decisions/README.md` | inside the table, above "Status values" |

Never code, never a builder file. Do not create `STATE.md`, `PROGRESS.md`, `DECISIONS.md`,
`BLOCKERS.md`, `MILESTONE`, `escalations/`, `reviews/` or `verify-report.md`: the builder creates
them in its first commit. Never edit `docs/SPEC.md`, `CLAUDE.md`, another spec's folder or any
gate script. `docs/acceptance-criteria.md` changes only as File 6 says (the registry is the one
complete list the black-box suite is built from; issue #31). The `docs/SPEC.md` follow-up after
merge is a separate step, not this skill.

## Procedure

### 1. Preconditions

1. Confirm this is an owner session (the owner typed `/write-spec`). In a builder session
   (a kickoff or resume prompt names a spec), stop and change nothing.
2. `git fetch origin`. Read `CLAUDE.md`, `docs/specs/README.md`, `decisions/README.md`, and
   `docs/specs/003-high-availability/SPEC.md` as the style model.
3. Ask the owner for the GitHub issue the spec PR is bound to. No issue: create one first
   (`gh issue create`) with the change statement, and use its number.

### 2. Numbers and branch

1. Run `node .claude/skills/write-spec/spec-tool.mjs next`. It scans the working tree and every
   branch (local and `origin`), so numbers held by an open spec PR are skipped. Also
   check `gh pr list --state open` for spec PRs whose branch is not fetched yet.
2. Choose `<name>` (short kebab-case), the builder branch `feature/<name>` and the release
   (`VERSION` + 1 minor, unless the owner says otherwise).
3. Create the branch `spec/<NNN>-<name>` from `origin/main`. Work only there.

### 3. Gather the change

1. Read the input: the issue body and comments by the owner, notes, linked decisions.
2. Read the `docs/SPEC.md` sections the change touches and the code paths behind them
   (search, do not guess). List: which spec parts change (section 1 table), what MUST stay the
   same, the design, the criteria.
3. Ask the owner only what the input and the code cannot answer. Ask few questions at a time.
   Everything still unclear goes to section 10 as an open question with options and a proposal.

### 4. Draft from `TEMPLATE.md`

Fill File 1 to File 6 and both index rows. Rules:

- Every behaviour change names an AC ID: a new ID in `acceptance-criteria.md` and section 5, or
  a re-expressed spec 001 ID in the section 5 table.
- A changed spec 001 criterion keeps its ID and its place ("same ID, same position"). Copy its
  current text from `docs/acceptance-criteria.md` into the "Now" column. Never give a changed
  old criterion a new ID.
- New IDs must not clash with any existing ID (`docs/acceptance-criteria.md` and every spec's
  `acceptance-criteria.md`).
- No new and no re-expressed ID at all: section 5 is the single line "No behaviour change."
  `check` fails on zero IDs without it.
- Acceptance delta (section 7): Added = all new IDs; Changed = all re-expressed IDs, with what
  the black-box scenario gains; Retired = removed IDs with a reason each, or `none`.
  Changed: all IDs on one line; name no other IDs in that line. The same for Added and Retired.
- File 6: append the new IDs to `docs/acceptance-criteria.md` as one section, each AC line byte
  for byte the same as in `acceptance-criteria.md`; replace each re-expressed line in place with
  its "Becomes" text (same ID, same position). `check` compares both.
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
Give each the path of the draft `SPEC.md` and `acceptance-criteria.md`, the touched
`docs/SPEC.md` sections and code paths, and this brief:

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
silently. Name the challenge and its result in the decision file ("Why").

### 7. Check

Run `node .claude/skills/write-spec/spec-tool.mjs check docs/specs/<NNN>-<name>` and fix every
`FAIL` until it passes. Exit codes: `0` pass, `1` problems, `2` only open owner questions left,
`3` usage error (wrong arguments or no such folder; the same for `next` and `tests`).
`--allow-open` accepts open questions for a draft PR. Read every `WARN` and decide.

The check covers: folder name and `spec.json`; id, branch and milestones unique across all
specs (also on other branches); `Due milestone` and AC line format (the gate parsers in
`scripts/lib/`); new IDs named in section 5 and not clashing; re-expressed IDs exist in
`docs/acceptance-criteria.md`; the 10 headings in order; section 7 Added/Changed equal the new
and re-expressed IDs; `test-changes.md` shape, plumbing clause and allowed IDs (all Changed or
Retired); section 10 items marked; decision file and both index rows inside their tables;
placeholders and comments left; status (Draft / Proposed, no "Accepted by the owner on")
while items are Open; the registry section (every new ID present under this spec's heading,
verbatim, with the spec's milestone; nothing else under that heading); scope (only this spec's
folder, its decision file, the two README files and `docs/acceptance-criteria.md` changed
against `origin/main`).

Also run the gate parser once; it must list exactly the new IDs with `due <M>`:
`FF_SPEC=<NNN> node -e "import('./scripts/lib/trace-registry.mjs').then(m=>console.log(m.specAcs().filter(a=>a.spec==='<NNN>').map(a=>a.id+' due '+a.due).join(', ')))"`
(`specAcs().length` alone also counts specs whose `MILESTONE` exists, for example 002.)

### 8. Commit, push, PR

1. Status while section 10 has any `**Open:**` item: index row `Draft`, decision row and
   decision file `Proposed`, no "Accepted by the owner on" line. Switch to `Ready`, `Accepted`
   and add that line only after the owner has answered every item and `check` exits 0.
2. Commit: `Spec <NNN>: <Title> (decision <DDDD>)`, with the attribution trailer the session
   gives. Push `spec/<NNN>-<name>`.
3. Open one PR to `main`, body: summary, files, the open questions of section 10, the check
   result, and `Refs #<issue>` (or `Closes #<issue>` when the PR fully answers it). With open
   questions, open it as a draft.
4. Never merge, never enable auto-merge. The owner merges.
5. When the owner answers a question: mark it `~~question~~ **Decided: …**`, apply the answer
   in the sections it touches, re-run step 7, push.
