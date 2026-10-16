# 0006 — Remove the edit-block hook

Status: **Accepted**
Decided by: owner (@brakebg), 2026-10-04
Changes: decision 0001, idea 4 ("Edit-block hook")

## 1. Decision

Remove `.claude/hooks/block_locked_files.py` and its entry in `.claude/settings.json`.
Owner-only files are checked only on the PR:

1. `locked-files-guard` workflow: a PR comment lists every changed locked path and its
   commits (decision 0003).
2. `scripts/owner-review.sh`, run by the owner from `main` before every merge.

## 2. Why

1. The hook was only an early warning; the shell could get around it.
2. It got in the way of the owner's own changes (needed `FF_OWNER_SESSION=1` and a
   restart).
3. Only the owner merges, so the PR comment and the owner review are enough.

## 3. Consequences

- A locked-file change is no longer stopped during the session. It shows in the PR
  comment and in the owner review.
- The builder rule does not change: never edit a locked file; a needed change is a Level 3
  escalation (`CLAUDE.md` section 3).
- `FF_OWNER_SESSION` is no longer used.
