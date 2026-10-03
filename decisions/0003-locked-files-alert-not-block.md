# 0003 — Locked-files check alerts, it does not block

Status: **Accepted**
Decided by: owner (@Yordan), 2026-10-03
Changes: decision 0001, section 5 ("`locked-files-guard` required check")

## 1. Decision

`.github/workflows/locked-files-guard.yml` is a **non-blocking alert**. On every PR to
`main` it posts or updates one PR comment that lists changed owner-only paths and commit
hashes. The job always passes and is not a required status check.

## 2. Why

1. A block can be worked around (for example a branch named `owner/*`), so it is not a
   real guarantee.
2. Only the owner merges. A clear alert on the PR gives the owner the same information.
3. Blocking also gets in the way of the owner's own spec changes.

## 3. What stays the hard check

`scripts/owner-review.sh`, run by the owner from `main` before every merge. It checks
locked files, locked values, weakened tests, and runs the black-box acceptance suite.

## 4. Consequences

- Branch protection on `main`: PR required, no bypass, no force-push. No required checks
  from this workflow.
- The workflow needs `pull-requests: write` to comment. It still never runs PR code.
