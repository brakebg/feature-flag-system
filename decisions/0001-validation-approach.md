# 0001 — Validation approach: single builder + black-box acceptance suite

Status: **Accepted**
Decided by: owner (@Yordan), 2026-10-03
Applied in: `docs/VALIDATION.md` (the go-to validation model)

## 1. Decision

Use **approach A: single builder + black-box acceptance suite.**

- One builder agent builds all 8 milestones of `docs/SPEC.md` autonomously, with its own
  tests and the 15 gates, as spec 12 describes.
- A separate tester session writes a black-box acceptance suite from the spec only, before
  the build, in a separate private repo. The builder never sees it.
- The owner runs the suite and `scripts/owner-review.sh` against the finished build. The
  builder is done only when the suite passes.

Approach B (dual-agent: builder writes no tests, blind tester writes all tests, owner
relays every loop) is **not chosen**.

## 2. Question

How do we make sure the built system is really correct, when an AI agent writes the code
and can also write, weaken or game the tests? Green tests prove only that code and tests
agree, not that the tests match the spec.

Both options use the same core idea: a second agent, isolated from the code, writes tests
from the spec only. They differ in how much the second agent owns and how often its tests
run.

## 3. Options

| | A — single builder + black-box acceptance suite (chosen) | B — dual-agent (not chosen) |
| --- | --- | --- |
| Builder | Code and its own tests, test-first, `make verify` | Production code only, no tests |
| Second agent | Extra black-box suite (HTTP + browser), separate private repo | Writes all tests, cannot see code |
| When independent tests run | After M8 (optional earlier runs) | Every loop |
| Owner work | Review suite once, run it at the end, fix rounds | Merge, run, triage and relay every loop |
| Builder autonomy | Full | None between loops |
| Fits the spec | Yes, as written | No: spec 11 and 12 need rewriting; gate 9 (frontend unit tests) conflicts with a blind tester |
| Trade-off | Autonomy + late detection | Slow + early detection |

## 4. Why A

1. It fits spec 12 as written: one autonomous agent, test-first, `make verify`.
2. The builder's internal tests and the 15 gates run on every chunk and catch most issues
   on the way.
3. The black-box acceptance suite still gives an independent check: 31 of 40 ACs fully,
   2 partly, and every `ERR-*` error case.
4. Owner time stays low. B needs the owner in every loop for 8 milestones.
5. B adds conflicts this spec cannot absorb cheaply: frontend unit tests (gate 9) need a
   tester who sees components; coverage, ArchUnit and integrity gates would change owner.

## 5. Consequences

- **Late detection accepted.** A weak builder test or a spec misread shared by code and
  tests is found after M8. Mitigations: optional suite runs after M3, M4, M5, M7; PIT
  (gate 7); planted bugs at the final review.
- **7 white-box ACs** (AC-CACHE-1, 2, 3, 5, 7, 8, AC-AUD-3) are not covered by the suite.
  They rely on the builder's tests and PIT.
- **The suite repo must stay private**, and the Claude GitHub app must not have access to it.
- **Hard gates live on `main`**: locked files and values, the review script, and the
  `locked-files-guard` required check. CI on the builder's branch is a signal only.
- Feedback to the builder is by AC ID in plain words, never test code; max 3 rounds per AC.

## 6. Ideas considered

Collected from Claude, ChatGPT, Gemini and Meta AI while deciding.

| # | Idea | From | Outcome |
| --- | --- | --- | --- |
| 1 | Separate agent writes black-box tests | Gemini, Meta | Adopted: black-box acceptance suite |
| 2 | Frontend mutation testing (Stryker) | Gemini | Phase 2, later, if we continue |
| 3 | CI outside the agent's control | ChatGPT, Meta | Adopted: owner review script from `main` |
| 4 | Edit-block hook | Meta | Adopted: early warning; the shell can get around it. Removed by 0006 |
| 5 | Limits outside the agent | Meta | Adopted: spec escalation after 20 failed `make verify`; account spend limit |
| 6 | Builder cannot read or run tests | Gemini | Rejected: breaks test-first work and `make verify` |
| 7 | `CODEOWNERS`, agent cannot merge | Gemini, ChatGPT | Adopted with limits: branch protection on public repo; approvals impossible (same account); `CODEOWNERS` as record only |
| 8 | Signed hashes on locked files | Meta | Not needed: `main` is the sealed copy |
| 9 | Policy engine, auto-merge, canary | ChatGPT | Out of scope: no production system yet |

## 7. Changing this decision

This decision is locked. To change it, write a new decision file (`0002-...`) that states
what changes and why, and set this file's status to "Superseded by 0002".
