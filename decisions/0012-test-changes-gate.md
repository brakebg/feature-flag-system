# 0012 · Spec 004: gate 16 enforces test-changes.md

Status: **Accepted** (2026-10-11). Owner-authored.

## Decision

Add gate 16, `scripts/check-test-changes.mjs`. A test that changed against `main` passes when one
of its tags on `main` is listed in the active spec's `test-changes.md`. Every other test may change
only by the plumbing clause. The gate runs in `make verify-fast`, `make verify`, CI and the owner
review (main's copy). The script and its self-test are owner-locked.

## Why

Issue #21 made each spec declare its allowed test changes; issue #22 asks for a gate that checks
the declaration. Gate 15 sees skipped tests and lowered thresholds, not a changed expected value.
The rule is tuned towards false blocks: a blocked plumbing change costs an escalation, a missed
behaviour change costs a wrong test that still passes.

The owner chose one owner PR over a builder spec: the script is owner-locked, and a builder must
not write the gate that judges it.

## What changes

- `docs/SPEC.md` 11.3: gate 16 row; verify-fast list; gate 14 runs the self-test first. 12.2: M10.
  12.3: all 16 gates.
- `scripts/gates.mjs`, `scripts/check-integrity.mjs` (16 gates), `scripts/owner-review.sh`
  (section 3b), `scripts/locked-paths.txt`.
- `CLAUDE.md` section 4 and 7, `docs/VALIDATION.md`, `README.md`, the write-spec template.
- `.claude/skills/write-spec/spec-tool.mjs`: runs its commands only when called as a command, so
  gate 16 can import `PLUMBING` and `idsIn`.
- New spec folder `docs/specs/004-test-changes-gate/` (milestone M10, AC-TC-1, AC-TC-2).

## Consequences

- A builder cannot change an expected value, an assertion method or a tag in a test its spec does
  not list; it must escalate.
- Some plumbing gets blocked (helper rewrites, a changed flag argument). On the spec 002 diff that
  was about 6 of 27 files. Accepted.
- Permission is per file: a file with one allowed tag may change freely.
- Added lines are not checked: a new assignment before an old assertion is not seen. The owner
  review and `test-auditor` still read the diff.
