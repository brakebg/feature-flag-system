# Templates for a new spec

Owner-authored, locked. Used by `/write-spec` (`SKILL.md`). Replace every `<...>`. Text in
`<!-- -->` is guidance: remove it in the output. Keep the headings, their order and the fixed
lines exactly; `spec-tool.mjs check` tests them.

`check` fails on any `<!--` and any `<word>` placeholder left outside code spans and fences.
Only the builder placeholders `<spec>`, `<n>`, `<area>` and `<what>` may stay; the template
keeps them in code spans.

Placeholders used below:

| Placeholder | Example | From |
| --- | --- | --- |
| `<NNN>` | `004` | `spec-tool.mjs next` |
| `<name>` | `flag-description-limit` | kebab-case, short |
| `<Title>` | `Flag description limit` | owner |
| `<M>` | `11` | `spec-tool.mjs next` |
| `<branch>` | `feature/flag-description-limit` | `feature/<name>`: the spec and the build share it |
| `<owner>` | `brakebg` | the repository owner's GitHub login |
| `<issue>` | `36` | the issue the spec is bound to, or `null` |

Write in plain, short English (`CLAUDE.md` section 10): the owner is not a native speaker.

---

## File 1: `docs/specs/<NNN>-<name>/spec.json`

```json
{
  "id": "<NNN>",
  "title": "<Title>",
  "owner": "<owner>",
  "status": "draft",
  "milestones": [<M>],
  "branch": "<branch>",
  "issue": <issue>,
  "release": null
}
```

The only place for the spec's facts. `status`: `draft` while section 10 has an open item,
`ready` when every item is decided (the owner then tags that commit `spec-<NNN>-ready`), `done`
after the merge. `release` is filled at the merge (the next free minor version). Optional
`"scope": ["frontend/**", ...]`: the paths the build is expected to change;
`scripts/owner-review.sh` warns on any change outside them.

---

## File 2: `docs/specs/<NNN>-<name>/SPEC.md`

````markdown
# Spec <NNN> — <Title>

Owner-authored, locked. Facts (status, milestone, branch, release): `spec.json`. Flow:
`docs/process/spec-flow.md`. Spec folder (`<spec>` below): `docs/specs/<NNN>-<name>/`.

This is a change spec, executed by an autonomous builder session. Every "MUST" is a hard
requirement. Section 10 records the owner's decisions.

## 1. Relation to the current docs

`docs/product/` and `docs/architecture/` describe the system. This spec's own commits change
them, so on its branch they already describe the system after the spec. Everything they say that
this spec does not change still applies.

| File | Change |
| --- | --- |
| <`docs/product/ui.md` section 8.4> | <what changes, one line> |

<!-- One row per changed file; check fails when a file changes on the branch but is not named. -->

Not changed: <for example gate 4 banned dependencies, `backend/openapi.json`, tech stack>.

The builder never edits these files, the criteria or this spec. A spec change during the build
is an owner amendment (`docs/process/spec-flow.md` section 2).

## 2. Goal

<Two to four sentences: what the change gives, and why now.>

Not in scope: <list>.

## 3. MUST stay the same

1. Every acceptance criterion in `docs/project-acceptance-criteria.md` passes at its current
   version, with the changes of section 5.
2. <HTTP behaviour for clients: status codes, headers, problem details, JSON — or say what changes.>
3. Security rules of spec 001 section 5.
4. <`backend/openapi.json` unchanged — or name the exact change.>
5. All 16 gates, thresholds, banned lists and test-integrity rules unchanged.
6. <Existing Flyway migrations unchanged (same checksums).>

<!-- Each item is something a reviewer can check. Breaking one is a Level 3 trigger (section 8). -->

## 4. Design

<!-- Subsections 4.1, 4.2, ... Use exact names, values, defaults, error cases. A mermaid
diagram helps when more than one component is involved. Name the open points; they go to section 10. -->

### 4.1 <Part>

<...>

## 5. Acceptance criteria

In `docs/project-acceptance-criteria.md` (File 4), due M<M>. New: <AC-XXX-1> to <AC-XXX-n>.

- **<AC-XXX-1>** · <short form of the criterion>

**Criteria that change** (same ID, same position, version + 1, `changed by spec <NNN>`):

| ID | Now | Becomes |
| --- | --- | --- |
| <AC-YYY-n> | <vN: text today> | <vN+1: new text> |

<Unchanged neighbours, for example "AC-YYY-1, 2, 3 unchanged".>

<!-- Every behaviour change has an AC ID: a new one above, or a changed one in the table.
A changed criterion keeps its ID and its place; never a new ID for a changed old criterion.
No changed criteria: write "None. No existing criterion changes." and no table.
No new, changed or retired criteria at all (a pure platform or tooling change): section 5 is
the single line "No behaviour change." `check` fails on zero IDs without that line. -->

## 6. Test changes

The tests that may change are listed in `<spec>/test-changes.md`.
Allowed behaviour changes: <AC-YYY-n, ...> <!-- or "none" -->.
Every other test may change only by the plumbing clause in that file. A change to any other
test is a Level 3 escalation (section 8).

## 7. Acceptance delta

For the black-box acceptance suite (`docs/process/validation.md`); the tests name criterion and
version (`[AC-YYY-n v2]`):

- Added: <AC-XXX-1 … AC-XXX-n> <!-- or "none" -->
- Changed: <AC-YYY-n, AC-YYY-m: what the suite scenarios gain or lose> <!-- or "none" -->
- Retired: <AC-ZZZ-n (reason), AC-ZZZ-m (reason)> <!-- or "none" -->

<!-- One line each. Put all IDs of a kind on its line and name no other ID in that line:
`check` compares Added, Changed and Retired with the criteria file. -->

## 8. Process

- `docs/process/` and `CLAUDE.md` apply. Commit messages `M<M> <area>: <what>`, trailers `AC:`
  and `Spec: <NNN> §<n>`.
- The builder works on `<branch>`, which already holds this spec (tag `spec-<NNN>-ready`).
  First commit: the memory files in `<spec>/builder/` (`STATE.md`, `PROGRESS.md`,
  `DECISIONS.md`, `MILESTONE` = `<M>`). After the first green chunk, one draft PR `<branch>` →
  `main`.
- Phases, each chunk green: **A** <...>; **B** <...>; **C** <...>, release: the next free minor
  version (`VERSION`, `CHANGELOG.md`).
- Milestone audit (`docs/process/builder-agents.md` section 5): `spec-auditor`, `test-auditor`,
  <`security-reviewer`, `design-checker`, `final-reviewer` (scope) — only those that fit>.

**Level 3 triggers for M<M>** (in addition to `docs/process/escalation.md`)

- A change would break an item of section 3.
- A test outside `<spec>/test-changes.md` would need more than a plumbing change.
- The only way to make a gate green is a new exception, exclusion or ignore entry.
- <spec-specific trigger, if any>

## 9. Definition of done

- [ ] `make verify-all` passes, all 16 gates; report committed as `<spec>/builder/verify-report.md`.
- [ ] <AC-XXX-1> to <AC-XXX-n> and every other criterion (with section 5 changes) pass.
- [ ] Only the tests in `<spec>/test-changes.md` changed beyond plumbing.
- [ ] `VERSION` and `CHANGELOG.md` for the next free minor version<, README section>.
- [ ] Milestone audit done, no open BLOCKER or CRITICAL; no open escalation; PR ready with summary.

## 10. Owner decisions

<!-- The spec's decision log: every open question from the draft and from the two-reviewer
challenge, one item each.
Answered: "N. ~~<question>~~ **Decided: <answer>.** <reason, section link>"
Not yet:  "N. **Open:** <question> Options: <a> / <b>. Proposal: <a>."
spec.json stays "draft" while any item is Open (check fails on "ready" with an Open item). -->

1. **Open:** <question> Options: <a> / <b>. Proposal: <a>.
````

---

## File 3: `docs/specs/<NNN>-<name>/test-changes.md`

The plumbing clause is fixed: copy it byte for byte. The test list comes from
`node .claude/skills/write-spec/spec-tool.mjs tests --md <IDs>`.

```markdown
# Test changes — spec <NNN>

Owner-authored, locked. Read by the builder and by `test-auditor`.

## Allowed behaviour changes

<AC-YYY-n, AC-YYY-m, ERR-METHOD-path-status>
(every test tagged with one of these IDs may change freely)

## Plumbing allowed everywhere

Imports, annotations, helper signatures, type renames inside an unchanged assertion.
No assertion method, expected value, @Tag or [AC-…] title may change in any other test.

## Tagged tests today

<output of `spec-tool.mjs tests --md <IDs>`, for reference; the IDs above are the rule>
```

No allowed IDs: write `none` under "Allowed behaviour changes" and leave the list out.
ERR IDs (`ERR-<METHOD>-<path>-<status>`, gate 14) are allowed too when an error case changes;
`check` validates them against `errIds` in `scripts/lib/trace-registry.mjs`.

---

## File 4: lines in `docs/project-acceptance-criteria.md`

The one complete list of the product's criteria; the black-box suite is built from it alone.
Line format (gates 14 to 16 parse it; `docs/process/traceability.md`):

```markdown
- [ ] **<AC-XXX-1>** v1 · <one testable sentence: given, when, then; exact values> · spec <NNN> · due M<M>
```

- **New criterion:** add it under the product-area heading it belongs to (or a new heading),
  `v1`, `spec <NNN>`, `due M<M>`. The prefix `AC-<AREA>` is new or continues an area without
  clashing with an existing ID.
- **Changed criterion:** replace its line in place: same ID and position, version + 1, the new
  text, `spec <its creator>`, `· changed by spec <NNN>`, `due M<M>`.
- **Retired criterion:** keep the line, end it with `· retired by spec <NNN>` instead of the due
  part. Never delete a line.
- Never touch another spec's line for any other reason. `check` compares the file with the
  branch base.

---

## File 5: `docs/product/` and `docs/architecture/`

Change the files that describe what this spec changes, so that on the branch they describe the
system after the spec (product behaviour in `docs/product/`, how it is built in
`docs/architecture/`). Keep their section numbers. Name every changed file in SPEC.md section 1;
`check` fails on a changed file that section 1 does not name.
