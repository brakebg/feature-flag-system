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
| `<DDDD>` | `0012` | `spec-tool.mjs next` |
| `<branch>` | `feature/flag-description-limit` | builder branch, never `main`, `spec/*` or `wip/*` |
| `<X.Y.Z>` | `1.3.0` | next release, from `VERSION` |
| `<date>` | `2026-10-12` | today |

Write in plain, short English (`CLAUDE.md` section 10): the owner is not a native speaker.

---

## File 1: `docs/specs/<NNN>-<name>/spec.json`

```json
{
  "id": "<NNN>",
  "title": "<Title>",
  "spec": "docs/specs/<NNN>-<name>/SPEC.md",
  "acceptance": "docs/specs/<NNN>-<name>/acceptance-criteria.md",
  "branch": "<branch>",
  "milestones": [<M>]
}
```

---

## File 2: `docs/specs/<NNN>-<name>/SPEC.md`

````markdown
# Spec <NNN> — <Title> (release <X.Y.Z>)

Owner-authored, locked. Decision: `decisions/<DDDD>-<name>.md`.
Milestone: **M<M>**. Working branch: **`<branch>`** (from `main`), one PR to `main`.
Spec folder (`<spec>` below): `docs/specs/<NNN>-<name>/`.

This is a change spec, executed by an autonomous builder session. Every "MUST" is a hard
requirement. Section 10 records the owner's decisions.

## 1. Relation to `docs/SPEC.md`

`docs/SPEC.md` (spec 001) and every merged spec still apply. This spec changes only what it
names. Where it names a part of `docs/SPEC.md`, this spec wins for M<M>.

| `docs/SPEC.md` part | Change in M<M> |
| --- | --- |
| <section number and name> | <what changes, one line> |
| 11.2 <AC IDs> | Re-expressed (table in section 5). The owner extends `docs/acceptance-criteria.md` |

<!-- One row per changed part. Name the exact section. Add a row for 11.2 only if section 5 re-expresses IDs. -->

Not changed: <for example gate 4 banned dependencies, `backend/openapi.json`, tech stack>.

After the PR is merged, the owner updates `docs/SPEC.md` and `docs/acceptance-criteria.md`
(re-expressed IDs in place, new IDs appended as a section; `docs/specs/README.md` "After a spec
is merged"). The agent never edits them or this file.

## 2. Goal

<Two to four sentences: what the change gives, and why now.>

Not in scope: <list>.

## 3. MUST stay the same

1. Every acceptance criterion of spec 001 and of every merged spec passes, except those
   re-expressed in section 5.
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

Registered in `<spec>/acceptance-criteria.md` (`Due milestone: <M>`): <AC-XXX-1> to <AC-XXX-n>.

- **<AC-XXX-1>** · <short form of the criterion>

**Spec 001 criteria that change** (same ID, same position; the owner extends
`docs/acceptance-criteria.md` after merge):

| ID | Now | Becomes |
| --- | --- | --- |
| <AC-YYY-n> | <text today, from `docs/acceptance-criteria.md`> | <new text> |

<Unchanged neighbours, for example "AC-YYY-1, 2, 3 unchanged".>

<!-- Every behaviour change has an AC ID: a new one above, or a re-expressed one in the table.
A re-expressed ID keeps its ID and its place; never a new ID for a changed old criterion.
No re-expressed IDs: write "None. No spec 001 criterion changes." and no table.
No new and no re-expressed IDs at all (a pure platform or tooling change): section 5 is the
single line "No behaviour change." and acceptance-criteria.md has no AC line. `check` fails
on zero IDs without that line. -->

## 6. Test changes

The tests that may change are listed in `<spec>/test-changes.md`.
Allowed behaviour changes: <AC-YYY-n, ...> <!-- or "none" -->.
Every other test may change only by the plumbing clause in that file. A change to any other
test is a Level 3 escalation (section 8).

## 7. Acceptance delta

For the black-box acceptance suite (`docs/VALIDATION.md`):

- Added: <AC-XXX-1 … AC-XXX-n> <!-- or "none" -->
- Changed: <AC-YYY-n, AC-YYY-m: what the suite scenarios gain or lose> <!-- or "none" -->
- Retired: <AC-ZZZ-n (reason), AC-ZZZ-m (reason)> <!-- or "none" -->

<!-- One line each. Put all IDs of a kind on its line and name no other ID in that line:
`check` compares Added with acceptance-criteria.md and Changed with the section 5 table. -->

## 8. Process

- Rules of spec 001 section 12 and `CLAUDE.md` apply. Commit messages `M<M> <area>: <what>`,
  trailers `AC:` and `Spec: <NNN> §<n>`.
- First commit: the memory files in `<spec>/` (`STATE.md`, `PROGRESS.md`, `DECISIONS.md`,
  `BLOCKERS.md`, `MILESTONE` = `<M>`). After the first green chunk, one draft PR
  `<branch>` → `main`.
- Phases, each chunk green: **A** <...>; **B** <...>; **C** <...>, release <X.Y.Z> (`VERSION`,
  `CHANGELOG.md`).
- Milestone audit (`docs/builder-agents.md` section 5): `spec-auditor`, `test-auditor`,
  <`security-reviewer`, `design-checker`, `final-reviewer` (scope) — only those that fit>.

**Level 3 triggers for M<M>** (in addition to spec 001 12.5)

- A change would break an item of section 3.
- A test outside `<spec>/test-changes.md` would need more than a plumbing change.
- The only way to make a gate green is a new exception, exclusion or ignore entry.
- <spec-specific trigger, if any>

## 9. Definition of done

- [ ] `make verify-all` passes, all 16 gates; report committed as `<spec>/verify-report.md`.
- [ ] <AC-XXX-1> to <AC-XXX-n> and every spec 001 criterion (with section 5 changes) pass.
- [ ] Only the tests in `<spec>/test-changes.md` changed beyond plumbing.
- [ ] `VERSION` <X.Y.Z>, `CHANGELOG.md` section<, README section>.
- [ ] Milestone audit done, no open BLOCKER or CRITICAL; no open escalation; PR ready with summary.

## 10. Owner decisions

<!-- Every open question from the draft and from the two-reviewer challenge, one item each.
Answered: "N. ~~<question>~~ **Decided: <answer>.** <reason, section link>"
Not yet:  "N. **Open:** <question> Options: <a> / <b>. Proposal: <a>."
The PR is not ready while any item is Open. Only when no item is Open, add the last line
"Accepted by the owner on <date>." (check fails on it while an item is Open). -->

1. **Open:** <question> Options: <a> / <b>. Proposal: <a>.
````

---

## File 3: `docs/specs/<NNN>-<name>/acceptance-criteria.md`

```markdown
# Acceptance criteria — spec <NNN>

Owner-authored, locked. IDs for gate 14 (traceability).

Due milestone: <M>

- [ ] **<AC-XXX-1>** · <one testable sentence: given, when, then; exact values>
- [ ] **<AC-XXX-2>** · <...>
```

Rules: the prefix `AC-<AREA>` is new or continues an area without clashing with an existing
ID. One line per criterion, exactly `- [ ] **AC-…** · text`. Re-expressed spec 001 IDs are
NOT listed here; they stay in `docs/acceptance-criteria.md`.

---

## File 4: `docs/specs/<NNN>-<name>/test-changes.md`

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

## File 5: `decisions/<DDDD>-<name>.md`

```markdown
# <DDDD> · Spec <NNN>: <one line decision>

Status: **Proposed** (<date>). Owner-authored.

## Decision

<What is decided, in a few sentences.>

## Why

<The problem; the options compared; why this one. Name the two-reviewer challenge result.>

## What changes

- <The `docs/SPEC.md` parts, as in spec section 1.>
- <Re-expressed AC IDs, if any (spec <NNN> section 5).>
- New spec folder `docs/specs/<NNN>-<name>/` (milestone M<M>).

## Consequences

- <What gets better, what costs more, what stays a limit.>
```

Set `Status: **Accepted**` only when the owner accepts and no section 10 item is Open.

---

## File 6: section in `docs/acceptance-criteria.md` (append at the end of the file)

The registry is the one complete list of the product's criteria; the black-box suite is built
from it alone (issue #31). The spec PR adds its new IDs there, so one merge brings both files up
to date.

```markdown
## <Title> (spec <NNN>, due milestone <M>)

<optional one-line note, for example a definition the criteria share>

- [ ] **<AC-XXX-1>** · <the same line as in File 3, byte for byte>
- [ ] **<AC-XXX-2>** · <...>
```

Rules: the heading format is fixed (`check` and gate 14 parse `(spec <NNN>, due milestone <M>)`);
every AC line is copied from File 3 unchanged; nothing but this spec's new IDs goes under the
heading. Re-expressed spec 001 IDs are not added here: their existing line is replaced in place
with the "Becomes" text of section 5 (same ID, same position). No new IDs: no section.

---

## Index row 1: `docs/specs/README.md`, table "Index" (append as the last row)

```markdown
| <NNN> | [<NNN>-<name>](<NNN>-<name>/) | `SPEC.md` | M<M> | `<branch>` | Draft |
```

Status `Draft` while section 10 has open items, `Ready` when all are decided (check fails on
`Ready` with open items).

## Index row 2: `decisions/README.md`, decision table (append as the last row, INSIDE the table, above "Status values")

```markdown
| <DDDD> | [Spec <NNN>: <one line decision>](<DDDD>-<name>.md) | Proposed |
```

Status `Proposed` while section 10 has open items, `Accepted` when all are decided.
