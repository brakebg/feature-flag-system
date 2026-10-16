# 0007 — Performance gate: hit rate blocks, p95 is report-only

Status: **Accepted**
Decided by: owner (@brakebg), 2026-10-05
Changes: spec 9.2 and 11.3 (gate 13)

## 1. Decision

1. Gate 13 still runs the k6 load (200 req/s for 60 s against compose).
2. It **fails** only on:
   - any error (non-200 answer or failed request);
   - cache hit rate < 99 % (AC-CACHE-9).
3. The Evaluation API **p95 is measured and reported** in the verify report against the
   50 ms target. A p95 at or above 50 ms **does not fail** the gate.
4. The p95 targets in spec 9.2 (Evaluation 50 ms, Admin API 300 ms) stay as product
   targets. They are verified in a **production-like test environment after the PR is
   approved**, not on a developer machine or in CI.

## 2. Why

1. Latency measured in Docker on a developer Mac with an artificial 1 vCPU limit mostly
   measures the machine (Docker VM network, other tests on the same CPU, cold JVM). It is
   noisy and says little about production.
2. A noisy hard gate can stop the build for hours with no real defect behind it.
3. Load testing in a real test environment after approval is the standard approach and
   gives more reliable numbers.
4. The cache hit rate is a correctness check (does the cache serve reads after warm-up?),
   not a hardware number. It stays cheap and reliable, so it stays blocking.

## 3. Consequences

- `scripts/perf.sh` / `perf/evaluate.js`: errors and hit rate decide pass/fail; p95 is
  written to `build/verify-report.md` (value and target). The 50 ms target and 200 req/s
  rate stay in `perf/evaluate.js` (`scripts/owner-review.sh` still checks them).
- AC-CACHE-9 does not change.
- Escalation trigger 5 (spec 12.5, "target unreachable") now applies to the cache target
  only. A p95 above target is reported in the milestone and final PR comments, not
  escalated.
- The Admin API p95 (300 ms) has no gate. The post-approval test environment run covers it.
