# Spec 004 — Test-changes gate (no release: tooling only)

Owner-authored, locked. Decision: `decisions/0012-test-changes-gate.md`.
Milestone: **M11**. Working branch: **`feature/test-changes-gate`** (not used: the owner built it).
Spec folder (`<spec>` below): `docs/specs/004-test-changes-gate/`.

This is a change spec. Unlike specs 002 and 003, the owner built it in one owner PR (issue #22),
because the gate script is owner-locked. No builder session runs it. Section 10 records the
owner's decisions.

## 1. Relation to `docs/SPEC.md`

`docs/SPEC.md` (spec 001) and every merged spec still apply. This spec changes only what it
names. Where it names a part of `docs/SPEC.md`, this spec wins for M11.

| `docs/SPEC.md` part | Change in M11 |
| --- | --- |
| 11.3 Automated quality gates | Adds gate 16 "Test changes", active from M1, part of `make verify-fast`. Gate 14 runs its self-test first |
| 12.2 Milestones | Adds M11 (this spec) |
| 12.3 Definition of done | "all 16 gates green" |

Not changed: every threshold, the banned-dependency lists, gates 1 to 15 (gate 15 now expects 16
gates), the product, `backend/openapi.json`, the tech stack.

In the same PR (issue #22) the owner updates `docs/SPEC.md`, `CLAUDE.md` and
`docs/VALIDATION.md`; no follow-up is needed after merge.

## 2. Goal

A test may change only in the spec that changes the behaviour it proves, and that spec names it
in advance in `<spec>/test-changes.md` (issue #21). Gate 15 catches skipped tests and lowered
thresholds, but nothing checked that a changed assertion was allowed. Gate 16 checks it on every
`make verify-fast`, in CI and in the owner review.

Not in scope: checking the content of a test that may change; per-test (instead of per-file)
permissions; test files outside the four paths in 4.1 (for example `scripts/smoke.sh`).

## 3. MUST stay the same

1. Every acceptance criterion of spec 001 and of every merged spec passes.
2. HTTP behaviour for clients: no change. The product code is not touched.
3. Security rules of spec 001 section 5.
4. `backend/openapi.json` unchanged.
5. Gates 1 to 15, thresholds, banned lists and test-integrity rules unchanged; gate 16 is added.
6. Existing Flyway migrations unchanged (same checksums).

## 4. Design

### 4.1 Input

`git diff` from the merge base of the branch and `origin/main` to the working tree (committed
and uncommitted changes), on `backend/src/test/**`, `frontend/src/**/*.test.*`,
`frontend/e2e/**` and `docs/acceptance-criteria.md`. Renames are followed. A new file changes
no existing test and is not checked.

The allowed IDs come from the "Allowed behaviour changes" section of the active spec's
`test-changes.md` (`scripts/lib/active-spec.mjs`), AC and ERR IDs. No active spec, or no
`test-changes.md`: nothing is allowed. The plumbing clause must equal `PLUMBING` in
`.claude/skills/write-spec/spec-tool.mjs`, else the gate fails.

### 4.2 Rules

1. A changed test file passes when one of its tags **on `main`** is allowed. Tags are the gate 14
   IDs in string literals: `@Tag("ID")`, `[ID]` in a test title. A tag added on the branch does
   not count.
2. Any other changed test file: each changed or removed line must be plumbing:
   - a blank line, a comment, an import, an annotation without arguments (not `@Tag`, not a test
     annotation such as `@Test`);
   - an identifier-only change: every literal, operator, keyword and assertion method stays;
     identifiers, generic type arguments and identifier arguments may change (a type rename, a
     helper signature change and its call sites);
   - a method signature or a field / variable type, with equal literals;
   - a removed field without a value, or its constructor assignment.
   Added lines are free (new tests, new assertions).
3. A deleted test file, or a tag that disappears from a file: FAIL.
4. `docs/acceptance-criteria.md`: every changed line must name only allowed AC IDs.

Every violation prints `FAIL <file>:<line>: <reason>`. Tuned towards false blocks: a blocked
plumbing change is a `DECISIONS.md` note and a Level 3 escalation, never an edit of the script.

### 4.3 Where it runs

| Where | How |
| --- | --- |
| `make verify-fast`, `make verify`, `make verify-all`, CI | Gate 16, `node scripts/check-test-changes.mjs` |
| Owner review | `scripts/owner-review.sh` section 3b runs main's copy with `--base <since> --head <branch> --spec <id>` |
| Self-test | `scripts/test/check-test-changes.test.mjs`, first step of gate 14; builds fixture repositories and writes `build/reports/script-results/check-test-changes.json` |

The script and its self-test are owner-locked (`scripts/locked-paths.txt`).

## 5. Acceptance criteria

Registered in `<spec>/acceptance-criteria.md` (`Due milestone: 11`): AC-TC-1 to AC-TC-2.

- **AC-TC-1** · A planted assertion change in a test not in the allowed list fails gate 16 with the file and line.
- **AC-TC-2** · An import, annotation or type-only change in an unlisted test passes; a listed test with a changed expected value passes.

None. No spec 001 criterion changes.

## 6. Test changes

The tests that may change are listed in `<spec>/test-changes.md`.
Allowed behaviour changes: none.
Every other test may change only by the plumbing clause in that file. A change to any other
test is a Level 3 escalation (section 8).

## 7. Acceptance delta

For the black-box acceptance suite (`docs/VALIDATION.md`):

- Added: AC-TC-1, AC-TC-2 (tooling; checked by the gate 16 self-test, not by the suite)
- Changed: none
- Retired: none

## 8. Process

- Built by the owner in one PR bound to issue #22. No builder session, no working branch.
- Calibration: on the spec 002 upgrade diff (Spring Boot 4, 27 test files), gate 16 accepts the
  pure plumbing and blocks the real refactors (helper rewrites, a strictness flag
  `true` → `JsonCompareMode.STRICT`, a new assertion block). That is the intended direction.

**Level 3 triggers for M11** (in addition to spec 001 12.5)

- A change would break an item of section 3.
- A test outside `<spec>/test-changes.md` would need more than a plumbing change.
- The only way to make a gate green is a new exception, exclusion or ignore entry.
- A builder session finds a plumbing change that gate 16 blocks: note it in `DECISIONS.md` and
  escalate; never edit the script.

## 9. Definition of done

- [x] `make verify-fast` passes with gate 16; the owner PR's CI runs `make verify-all`.
- [x] AC-TC-1 to AC-TC-2 pass (gate 14 matrix).
- [x] Only the tests in `<spec>/test-changes.md` changed beyond plumbing (none).
- [x] No `VERSION` change: tooling only.
- [x] Owner PR with summary, bound to issue #22.

## 10. Owner decisions

1. ~~Route: one owner PR, a builder spec, or a script without a spec?~~ **Decided: one owner PR.** The script is owner-locked, so the owner builds it; this folder records the criteria.
2. ~~Per test or per file?~~ **Decided: per file**, as issue #22 says. A file with one allowed tag may change freely.
3. ~~Where does the self-test run?~~ **Decided: first step of gate 14**, so its results reach the traceability matrix in the same run.

Accepted by the owner on 2026-10-11.
