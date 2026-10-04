#!/usr/bin/env bash
# Gate 12 (spec 11.3, 11.5, 11.6): Playwright end-to-end against fresh compose stacks.
# Runs with --repeat-each=2 and retries 0 (playwright.config.ts): a test that passes once
# and fails once fails the gate. Report: build/reports/playwright.json (read by gate 14).
set -uo pipefail
cd "$(dirname "$0")/.."
source scripts/lib/stack.sh

ID=$(run_id)
stack_up "ff-e2e-$ID" 38080 33000 || exit 1
stack_up "ff-e2e-limits-$ID" 38180 33100 || exit 1
stack_up "ff-e2e-shots-$ID" 38280 33200 || exit 1
for p in 38080 38180 38280; do wait_ready "http://localhost:$p/actuator/health/readiness" 90 || exit 1; done

mkdir -p build/reports
cd frontend
UI_URL=http://localhost:33000 API_URL=http://localhost:38080 \
LIMITS_UI_URL=http://localhost:33100 LIMITS_API_URL=http://localhost:38180 \
SHOTS_UI_URL=http://localhost:33200 SHOTS_API_URL=http://localhost:38280 \
  npx playwright test --repeat-each=2
