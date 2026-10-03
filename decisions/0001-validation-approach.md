# 0001 — Validation approach: single builder + black-box acceptance suite vs dual-agent

Status: Proposed — benchmark pending
Date: 2026-10-03
Owner: @Yordan
Visual summary: https://claude.ai/artifact/DfB8K6jQ6h4kqwYHCoejBG (private)

## 1. Question

How do we make sure the built Feature Flag Service is really correct, when an AI agent
writes the code and can also write, weaken or game the tests?

Green tests prove only that code and tests agree. They do not prove that the tests match
the spec. Both approaches answer this with the same core idea: **a second agent, isolated
from the code, writes tests from the spec only.** They differ in how much that second
agent owns and how often its tests run.

What we validate (`docs/SPEC.md`): Java 21 / Spring Boot 3 / PostgreSQL 16 backend with a
Caffeine-cached evaluation API, React 18 / TypeScript / Vite admin UI, Docker. 40
acceptance criteria in 7 groups (11.2), 15 gates in `make verify` (11.3), 8 milestones
(12.2).

## 2. Trade-off in one line

| | Summary | Detail |
| --- | --- | --- |
| **A** | **Autonomy + late detection** | Builder fully autonomous through M1 to M8. A weakness in its own tests is found at the end by the black-box acceptance suite or planted bugs. Its internal tests and the 15 gates run on every chunk and catch most bugs at once; what arrives late is a weak test or a spec misread shared by code and tests. Optional milestone runs make it earlier |
| **B** | **Slow + early detection** | Human in the loop every cycle. Weak tests and misreads show up in the first loop that runs both sides. "Early" depends on how often the owner merges and runs; the tester cannot run anything alone |

## 3. Approach A — Single builder + black-box acceptance suite

One Claude cloud session builds all 8 milestones with its own tests, as spec 12
describes. A separate tester session writes a black-box acceptance suite the builder
never sees.

| Item | Detail |
| --- | --- |
| Builder owns | `backend/`, `frontend/` including its tests; `make verify` with all 15 gates |
| Black-box acceptance suite | Playwright (TypeScript) for API calls and UI, in the separate private repo `feature-flag-acceptance` (`docs/VALIDATION.md` 2.1) |
| Written | Before M1 by a separate tester session from `docs/SPEC.md` + `docs/design/` only; reviewed once by the owner; then frozen |
| Runs against | `make up`: UI `:3000`, API `:8080`, login `admin`/`admin123`, client `order-service` |
| UI selectors | Role and label from spec texts ("Sign in", "+ New group", `role="switch"` with `aria-checked`). The spec has no `data-testid` |
| Covers | 31 ACs fully, 2 partly (AC-CACHE-4, AC-CACHE-6), plus every `ERR-*` error case from spec 6.1 and 9.1 |
| Not covered (white-box only) | AC-CACHE-1, 2, 3, 5, 7, 8 and AC-AUD-3: database query counts, rollback, reconciliation job, purge with injected `Clock`. Only the builder's tests and PIT check them |
| Runs when | At the end, after M8. Optional: also after M3, M4, M5, M7 (table below) |
| Owner work | Review the suite once; run it at the end; fix rounds (max 3 per AC); final review |
| Guards | Locked files on protected `main`; `locked-files-guard` required check; owner review script from `main`; edit-block hook; outside protection (`docs/VALIDATION.md` 2.2, 2.3) |
| Fits current spec | Yes, as written |

Process: the 6 steps in `docs/VALIDATION.md` 4.1 (tester done first; builder done when the suite passes).

Optional earlier runs — when each part of the suite should start to pass:

| After | Builder finished | Suite tests that should now pass |
| --- | --- | --- |
| M3 | Auth | Login API, AUTH-5 (429), EVAL-1, EVAL-2, EVAL-4, `ERR-*` 401 / 403 / 429 |
| M4 | Admin API, audit | GRP-1…5, FLAG-1…6, AUD-1 at API level; admin `ERR-*` (400, 404, 409 duplicate-key, 409 version-conflict) |
| M5 | Evaluation API, cache | EVAL-3, 5, 6, 7; CACHE-4 (visible part) |
| M7 | UI features | UI parts of AUTH, GRP, FLAG; AUD-2 |
| M8 | Docker, hardening | OPS-1…4; CACHE-6 (partly); CACHE-9 with k6 |

Main risk: the 7 white-box ACs and any weak test of the builder are found only by PIT,
planted bugs, or at the next milestone run.

## 4. Approach B — Dual-agent (builder + blind tester)

Two local Claude sessions in separate clones. The builder writes production code only.
The tester writes tests without seeing the code. The owner merges, runs, triages and
relays. Source: `dual-agent-workflow-v1.md` (owner's pilot plan).

| Item | Detail |
| --- | --- |
| Builder owns | `backend/src/main/`, `frontend/src/` (no tests), Dockerfiles |
| Tester owns | `backend/src/test/`, `frontend/e2e/` |
| Owner owns | Gate scripts, thresholds, `ArchitectureTest`, `docs/acceptance-criteria.md`, CI |
| Isolation | Separate single-branch clones, sparse checkout, deny rules per clone |
| White-box ACs | Possible only through class names in spec 9.5 (`FlagCacheService`, `FlagGroupRepository`, `ClockConfig`). Method names are not in the spec, so tests break until the builder matches |
| When tests run | Every loop: integration branch, `make verify`, triage |
| Owner work | Every loop: merge, run, triage, redact, relay |
| Builder autonomy | None between loops |
| Fits current spec | No. Spec 11 and 12 need changes (below) |

What B changes in this spec:

| # | Spec item | Under B |
| --- | --- | --- |
| 1 | Spec 12: one agent, test-first | Rewrite: two roles, human relay, no TDD for the builder |
| 2 | Gate 9: Vitest + RTL + MSW | **Conflict**: a blind tester cannot test components it cannot see. Pragmatic variant: builder keeps frontend unit tests, tester owns `frontend/e2e/` |
| 3 | Gate 6: coverage 80% backend, 70% frontend | Tester's tests must reach 80% on `service` and `controller` without seeing them |
| 4 | Gate 3: ArchUnit `ArchitectureTest` | Moves to the owner: a rule about the code, not a spec test |
| 5 | Gates 14, 15: traceability, integrity scripts | Move to the owner, or the tester could weaken them |
| 6 | Spec 11.5: `FlagCacheService.reloadAll()` before each test | OK: the spec names it |
| 7 | Spec 12.5: escalation through the PR | Each role needs its own question channel |

Fixes needed in the v1 pilot file before running B:
1. Path guard runs on `push` with the branch's own workflow copy → builder can edit it.
   Fix: `pull_request_target` from `main`, as a required check.
2. Builder owns `backend/pom.xml`, which holds the JaCoCo and PIT thresholds. Fix: the
   owner review script checks the locked values, or human-owned `ci.yml` passes them.
3. "1 approval (you)" is impossible: GitHub does not allow approving your own PR.
   Fix: PR required, 0 approvals, no bypass — or a second account.
4. `path-guard` missing from required status checks.
5. Paths do not match this spec: use `docs/SPEC.md` (not `spec/`) and `frontend/e2e/`
   (not `e2e/`). Decide gate 9 ownership first.

Main risk: 8 milestones × many loops, all through the owner. Early loops are mostly test
mistakes because the tester cannot run anything before the merge.

## 5. Side by side

| # | Topic | A | B | Verdict |
| --- | --- | --- | --- | --- |
| 1 | Who writes tests | Builder (own) + black-box acceptance suite | Tester only (see gate 9) | B more independent |
| 2 | When independent tests run | After M8 (optional milestone runs) | Every loop | B earlier |
| 3 | Isolation | Separate private repo | Separate clones, deny rules | Both soft; guard from `main` is the hard part |
| 4 | Hard gate | Owner review + guard from `main` as required check; branch protection | Path guard on push (hole) + branch protection | Fix B's guard |
| 5 | Feedback to agents | Bug report by AC ID | Redaction rules, templates, triage table | Adopt B's into A |
| 6 | Loop limit | 20 failed `make verify` per milestone | 3 round-trips per scenario | Add B's per AC |
| 7 | UI selectors | Role + label | Same (no `data-testid` in spec) | Same |
| 8 | API contract | Spec 6 tables; `openapi.json` generated (gate 8) | Same, or hand-written OpenAPI | Spec tables enough |
| 9 | PIT on `auth`, `group`, `flag`, `evaluation` | Builder's tests | Tester's tests | Same tool, 60% |
| 10 | 7 white-box ACs | Builder covers them | Tester via spec 9.5 class names, fragile | A easier |
| 11 | Retro metrics | None | Loops, causes, flakes, breaches | Adopt B's into A |

## 6. Shared by both

- The 40 `AC-*` IDs and the `ERR-*` tags are the unit of tests and feedback.
- Feedback crosses the isolation only as behaviour: AC ID, observed vs expected, HTTP
  method/path/status, input category. Never test code, assertions, stack traces or
  application source.
- Loop cap: at most 3 round-trips per AC, then the owner decides.
- Locked values checked from `main` (coverage 80/70, PIT 60, Playwright retries 0 and
  repeat-each 2, `maxDiffPixelRatio` 0.01, k6 targets, banned dependencies, verbatim
  acceptance criteria): `docs/VALIDATION.md`.
- Public repo protections: `docs/VALIDATION.md` 2.2 and 2.3.
- Final review: planted bugs, then a click-through of spec 11.2 and
  `docs/design-compare/index.html`.

## 7. Benchmark

### 7.1 Setup — keep everything equal except the approach

| Item | Rule |
| --- | --- |
| Spec slice | Groups Admin API: 4.1 `flag_group` + `feature_flag`, 4.2 validation, 6.1 group endpoints, 9.1 problem details, audit events for groups |
| ACs | AC-GRP-1, 2, 3, 5 (API parts), AC-AUD-1 (group part) |
| Error cases | `ERR-POST-/groups-400`, `-409`; `ERR-GET-/groups/{id}-404`; `ERR-PATCH-/groups/{id}-400/404/409`; `ERR-DELETE-/groups/{id}-404` |
| Auth | Fixed admin user in a test profile, same for both, so M3 is not needed |
| Stack | Spring Boot + Flyway + Testcontainers only. No UI, no cache |
| Model and tools | Same model, same Claude Code version, same machine |
| Budget cap | Same token/$ cap and same wall-clock cap per approach |
| Starting repo | Fresh repo from the same `main` commit for each approach |
| Order | Same time or back to back; neither builder sees the other's result |

### 7.2 Independent scoring — neither approach's own tests decide the result

1. **Referee suite.** Playwright API tests from the slice, written before either run
   (third session or by hand), never shown to A or B. Run against both final builds.
2. **Planted bugs.** Inject each bug alone into each final build. Count how many the
   approach's own tests catch:
   1. Key regex accepts `1abc`
   2. Duplicate group key returns 500, not 409 `duplicate-key`
   3. Delete group leaves its flags (no cascade)
   4. `GROUP_DELETED` details miss the flag keys
   5. PATCH ignores `version` (no 409 `version-conflict`)
   6. `updatedBy` taken from the request body
   7. `createdBy` changes on update
   8. Description of 501 characters accepted
   9. `GET /groups` default sort is not by `key`
   10. Problem detail `type` suffix is wrong for 404
3. **Mutation score.** PIT on the `group` package for both.

### 7.3 Metrics

| # | Metric | How measured | Better |
| --- | --- | --- | --- |
| M1 | Escaped defects | Referee suite: failing ACs and `ERR-*` on the final build | Lower |
| M2 | Planted bugs caught | Caught / 10, by the approach's own tests | Higher |
| M3 | Mutation score | PIT on `group` | Higher |
| M4 | Owner time | Minutes on review, triage, relay — keep a log | Lower |
| M5 | Agent cost | Tokens / $ for all sessions | Lower |
| M6 | Wall-clock | Start to done | Lower |
| M7 | Loops | Round-trips per AC, and cause (app / test / spec) | Lower |
| M8 | Integrity incidents | Guard hits, weakened tests, locked-file edits | Lower |
| M9 | Spec questions | Questions raised per agent | Info |
| M10 | Common-mode errors | Both agents misread the same clause the same way | Lower |

### 7.4 Decision rule (proposal — confirm before the benchmark runs)

- Choose **B** only if it is clearly better on quality AND the owner cost is acceptable:
  - M1: at least 2 fewer escaped defects, OR M2: at least 20 percentage points more
    planted bugs caught; AND
  - M4: owner time at most 2× approach A.
- Otherwise choose **A** (fits the spec and the autonomous cloud session as written).
- Tie or unclear → choose A and repeat on a second slice (Evaluation API + cache).

## 8. Results

| Metric | A | B | Notes |
| --- | --- | --- | --- |
| M1 Escaped defects | | | |
| M2 Planted bugs caught | /10 | /10 | |
| M3 Mutation score | | | |
| M4 Owner time (min) | | | |
| M5 Agent cost ($) | | | |
| M6 Wall-clock | | | |
| M7 Loops (app/test/spec) | | | |
| M8 Integrity incidents | | | |
| M9 Spec questions | | | |
| M10 Common-mode errors | | | |

## 9. Decision

_Filled in after the benchmark._

## 10. Open items

1. Confirm the spec slice (Groups Admin API with a fixed admin user).
2. Confirm the decision rule thresholds in 7.4.
3. Gate 9 under B: builder keeps frontend unit tests, or the tester writes them blind.
4. Write the referee suite and freeze the planted-bug list before either run starts.

## 11. Phase 2 — only if the benchmark results are good and we continue

| Item | Why later |
| --- | --- |
| Stryker: frontend mutation testing (`docs/VALIDATION.md` idea #2) | PIT covers the backend only. Adding Stryker needs a spec change (new gate and dependency); not worth it before we know which approach we keep |
