# Spec flow

Owner-authored, locked. How one feature goes from spec to `main`. Decided 2026-10-16 (Spec
Delivery Flow, `spec-blackbox-builder` repo). Folder rules: `docs/specs/README.md`.

## 1. One branch per feature

```
main ──●────────────────────────────────────────────────●── merge (one PR)
        \                                              /
feature/<name>  ○──○──○──[spec-NNN-ready]──●──●──[spec-NNN-amend-1]──●──●
                 owner: spec                builder    owner: answer   builder
```

1. **Spec (owner).** `/write-spec` creates `feature/<name>` from `main` and commits the spec:
   `docs/specs/NNN-name/` (`spec.json`, `SPEC.md`, `test-changes.md`, `design/` if any), the new
   and changed lines in `docs/project-acceptance-criteria.md`, and the changes to
   `docs/product/` and `docs/architecture/` that describe the system after this spec. A spec
   that changes the API also updates the one project contract `docs/architecture/openapi.json`
   (with `api.md`); specs never have their own copy. The builder builds to it (gate 8). Every
   section 10 item is decided; `spec.json` says `"status": "ready"`.
2. **Handover check (owner).** `/handover-check <issue or folder>` checks the head of
   `feature/<name>` before any tag: spec files, no open decision, the spec check, criteria, CI
   and the gate baseline. READY prints the tag commands for exactly the commit it checked. NOT
   READY: fix the spec in a new commit and run the check again. No tag exists yet, so nothing
   has to move.
3. **Tag (owner).** The owner tags the commit the check passed: `spec-NNN-ready`. Everything
   before the tag is the approved spec; everything after it is the builder's. A tag is set only
   after a READY check, so it always marks a checked spec.
4. **Tests (tester session, in parallel).** In the private acceptance repo, branch `spec-NNN`.
   Input is only a snapshot the acceptance repo's intake workflow copies to `input/spec-NNN/`:
   the criteria file, the contract `docs/architecture/openapi.json` and `docs/product/design/`,
   all at the tag. Each `spec-NNN-amend-<k>` refreshes all three. Never `backend/openapi.json`:
   the builder's code generates it. Each snapshot opens an
   issue "Write tests for spec NNN" in the acceptance repo; the owner starts the tester session.
   The acceptance repo pulls from this public repo; this repo never pushes to it and holds no
   token for it. The tester never sees this repo; the builder never sees the acceptance repo. Tests name criterion and version: `[AC-UI-1 v1]`.
5. **Build (builder).** The owner starts a cloud session with the kickoff prompt
   (`docs/process/builder-prompt.md` section 5). The builder checks out `feature/<name>`,
   confirms `spec-NNN-ready` is in it, builds in green chunks, and opens one draft PR
   `feature/<name>` → `main`.
6. **Acceptance (owner).** `scripts/owner-review.sh docs/specs/NNN-name` runs the acceptance
   branch against the feature branch. It refuses to run while a criterion has no test at its
   current version.
7. **Merge (owner).** The feature PR into `main`; the acceptance branch into the acceptance
   repo's `main`, tagged `spec-NNN`. Then `spec.json`: `"status": "done"` and the release.

## 2. Amendments: spec changes during the build

Only the owner changes the spec, and only like this:

1. The builder raises an escalation (`docs/process/escalation.md`).
2. The owner answers on the PR (`ESC-NNN: …`) and, when the answer changes the spec, makes one
   commit on `feature/<name>`: the spec change, criteria lines with their version raised, the
   contract change in `docs/architecture/openapi.json` if the API changes,
   `builder/escalations/ESC-NNN.md` set to `Status: resolved` with the answer. Commit message
   `ESC-NNN: <answer>`.
3. The owner tags exactly that commit: `spec-NNN-amend-<k>` (k = 1, 2, …).
4. The builder pulls it. It never edits the spec, the criteria or `docs/product/`,
   `docs/architecture/` itself.

A tag marks one commit, never a range: a range tag would also approve the builder's commits
before it.

## 3. Telling owner changes from builder changes

The `locked-files-guard` workflow and `owner-review.sh` split the PR's commits:

| Commits | Treated as |
| --- | --- |
| Up to `spec-NNN-ready` | The approved spec: expected owner changes |
| Exactly a `spec-NNN-amend-<k>` commit | An owner amendment: expected |
| Every other commit after the tag | The builder's: any locked path changed here is flagged |
| PR without a ready tag in its history | Everything flagged (old flow or tag missing) |
| A locked file no commit above explains, whose content differs from the newest owner version (the last amendment, else the ready commit, also merged with main) | Flagged: changed through a merge |

Protection: a GitHub tag rule lets only admins create, move or delete `spec-*` tags, and a
branch rule forbids force-push and deletion on `feature/*`.

**Learning setup limit.** The builder runs on the owner's GitHub account, so an admin rule does
not stop it technically. The tags are a record, not a barrier: `owner-review.sh` lists every
`spec-*` tag with its commit, and the owner confirms each one is theirs. A production team
uses signed owner commits checked against an allowed-signers key instead.

## 4. Criteria versions and the acceptance suite

Each criterion has a version (`docs/process/traceability.md`). The acceptance suite is current
when every criterion that counts for the spec has a test titled with its ID and current
version. `owner-review.sh` compares the two and lists every gap:

```
stale: AC-STYLE-1 criteria v3 · tests v2
       AC-UI-4    criteria v1 · no test
```

Only an amendment that raises a version needs new tests. Two specs that change the same
criterion meet as a git conflict on its line; the owner resolves it.

## 5. Production team setup

This flow is a learning setup on one account. A team building production code changes two
things: owner answers and amendments count only as commits signed with the owner's key
(checked against an allowed-signers file read from `main`), and the acceptance suite runs in
the private acceptance repo's own CI against the public feature branch, as a required check.
