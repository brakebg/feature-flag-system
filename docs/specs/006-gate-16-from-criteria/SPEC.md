# Spec 006 — Gate 16 from the criteria

Owner-authored, locked. Facts (status, milestone, branch, release): `spec.json`. Flow:
`docs/process/spec-flow.md`. Spec folder (`<spec>` below): `docs/specs/006-gate-16-from-criteria/`.

This is a change spec. Like spec 004, the owner builds it in one owner PR, because the gate script
is owner-locked; no builder session runs it. Every "MUST" is a hard requirement. Section 10
records the owner's decisions.

## 1. Relation to the current docs

`docs/product/` and `docs/architecture/` describe the system. This spec's own commits change
them, so on its branch they already describe the system after the spec. Everything they say that
this spec does not change still applies.

| File | Change |
| --- | --- |
| none | Tooling only: the product and its architecture do not change |

Not changed: the product, `backend/openapi.json`, gates 1 to 15, every threshold, the banned
lists, the plumbing clause, the format of `test-changes.md`.

The owner also changes, in the same PR: `scripts/check-test-changes.mjs` and its self-test,
`CLAUDE.md` section 4, `docs/process/quality-gates.md` gate 16, `docs/process/validation.md`,
`docs/specs/README.md`, `.claude/rules/tests.md`, `.claude/agents/test-auditor.md`, the write-spec
template and `spec-tool.mjs` (ERR IDs), `/handover-check` check 9, `scripts/owner-review.sh` 3b.

## 2. Goal

Gate 16 decides which tests may change from the one place that already says which behaviour
changes: the versions in `docs/project-acceptance-criteria.md`. Today it reads the active spec's
`test-changes.md`, and the active spec comes from the branch name. That chain fails on a tag push
(`spec-003-ready`, CI run 39147741927), on a mistyped branch name and on a `wip/` branch, and a
branch name is something anyone can choose.

Not in scope: the active spec for gate 14 milestones (`scripts/lib/active-spec.mjs` stays);
per-test instead of per-file permissions; owner PRs that delete criteria lines.

## 3. MUST stay the same

1. Every acceptance criterion in `docs/project-acceptance-criteria.md` passes at its current
   version, with the changes of section 5.
2. HTTP behaviour for clients: no change. The product code is not touched.
3. Security rules of spec 001 section 5.
4. `backend/openapi.json` unchanged.
5. Gates 1 to 15, thresholds, banned lists and test-integrity rules unchanged; gate 16 changes
   only as section 4 says, and its plumbing rules (rule 2) stay byte for byte.
6. Existing Flyway migrations unchanged (same checksums).

## 4. Design

### 4.1 Allowed IDs

The gate compares `docs/project-acceptance-criteria.md` at the merge base of the head and
`origin/main` (else `main`) with the head. An ID is allowed when its version went up or its line
became `retired by`. This holds whatever `--base` says, because the spec's own commits before
its ready tag are the ones that raise the versions. No branch name, no `FF_SPEC`, no
`test-changes.md`; the option `--spec` is removed.

### 4.2 Criteria file rules

A removed criterion line, a text change without a version + 1, a version change other than + 1
and a new criterion not at `v1` fail. Checkbox ticks, headings and notes pass. The rule that the
builder never edits the file stays with the `locked-files-guard` workflow and
`scripts/owner-review.sh`; so the builder cannot free a test by raising a version itself.

### 4.3 ERR IDs

`ERR-<METHOD>-<path>-<status>` IDs are not in the criteria file, so they cannot free a test. An
error case that changes belongs to a criterion, and that criterion's version goes up.
`spec-tool.mjs check` rejects ERR IDs in `test-changes.md`. No spec has used one so far.

### 4.4 `test-changes.md`

Stays, same format: it names the same IDs in words for the builder and `test-auditor`, and
`spec-tool.mjs check` keeps it equal to the changed and retired criteria. Gate 16 no longer reads it.

## 5. Acceptance criteria

In `docs/project-acceptance-criteria.md` (File 4), due M13. No new criteria.

**Criteria that change** (same ID, same position, version + 1, `changed by spec 006`):

| ID | Now | Becomes |
| --- | --- | --- |
| AC-TC-1 | v1: a planted change in a test whose tags are not in the active spec's `test-changes.md` makes gate 16 fail and print the file and line | v2: the same for a test whose tags name no criterion with a raised version or a retirement; also a criterion text change without a version + 1, a removed criterion line, a version jump of more than 1 or a new criterion not at v1 |
| AC-TC-2 | v1: plumbing in an unlisted test passes; a listed test with a changed expected value passes | v2: plumbing in any test passes; a test of a raised or retired criterion may change its expected value, whatever the branch name, also checked from a tag |

## 6. Test changes

The tests that may change are listed in `<spec>/test-changes.md`.
Allowed behaviour changes: AC-TC-1, AC-TC-2.
Every other test may change only by the plumbing clause in that file. A change to any other
test is a Level 3 escalation (section 8).

## 7. Acceptance delta

For the black-box acceptance suite (`docs/process/validation.md`); the tests name criterion and
version (`[AC-TC-1 v2]`):

- Added: none
- Changed: AC-TC-1, AC-TC-2: none for the suite; both are proved by the gate 16 self-test (gate 14)
- Retired: none

## 8. Process

- `docs/process/` and `CLAUDE.md` apply. Commit messages `M13 <area>: <what>`, trailers `AC:`
  and `Spec: 006 §<n>`.
- Built by the owner on `feature/gate-16-from-criteria` in one PR to `main`, like spec 004; no
  builder session and no `spec-006-ready` tag. `<spec>/builder/MILESTONE` = `13 complete` comes
  with the PR, so gate 14 counts AC-TC-1 and AC-TC-2 at v2.
- Phases: **A** the gate and its self-test; **B** the docs, agents, skills and review script
  that describe gate 16. No release: tooling only.
- Milestone audit (`docs/process/builder-agents.md` section 5): `spec-auditor`, `test-auditor`.

**Level 3 triggers for M13** (in addition to `docs/process/escalation.md`)

- A change would break an item of section 3.
- A test outside `<spec>/test-changes.md` would need more than a plumbing change.
- The only way to make a gate green is a new exception, exclusion or ignore entry.

## 9. Definition of done

- [ ] `make verify-all` passes, all 16 gates; report committed as `<spec>/builder/verify-report.md`.
- [ ] AC-TC-1 and AC-TC-2 at v2 and every other criterion pass.
- [ ] Only the tests in `<spec>/test-changes.md` changed beyond plumbing.
- [ ] `VERSION` and `CHANGELOG.md`: unchanged, tooling only (as spec 004).
- [ ] Milestone audit done, no open BLOCKER or CRITICAL; no open escalation; PR ready with summary.

## 10. Owner decisions

1. ~~Source of the allowed IDs~~ **Decided: the criteria versions, not the branch name** (owner,
   2026-10-18). The branch name failed on the tag push of `spec-003-ready`.
2. ~~ERR IDs~~ **Decided: not allowed on their own; an error case that changes belongs to a
   criterion** (4.3).
3. ~~Keep `test-changes.md`~~ **Decided: yes, as the readable list, checked by `spec-tool`** (4.4).
4. ~~Built by whom~~ **Decided: the owner, in one PR, like spec 004** (section 8).
