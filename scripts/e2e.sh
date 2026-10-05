#!/usr/bin/env bash
# Gate 12 (spec 11.3, 11.5, 11.6): Playwright end-to-end against fresh compose stacks.
# Runs with --repeat-each=2 and retries 0 (playwright.config.ts): a test that passes once
# and fails once fails the gate. Report: build/reports/playwright.json (read by gate 14).
# Playwright runs in its official image, so browsers, fonts and screenshot baselines are the
# same on every machine (spec 11.6 baselines are compared pixel by pixel).
#   scripts/e2e.sh [extra playwright args]   e.g. --update-snapshots, a file filter
set -uo pipefail
cd "$(dirname "$0")/.."
source scripts/lib/stack.sh

PW_IMAGE="mcr.microsoft.com/playwright:v$(node -p "require('./frontend/node_modules/@playwright/test/package.json').version")-noble"
ID=$(run_id)
stack_up "ff-e2e-$ID" 38080 33000 || exit 1
stack_up "ff-e2e-limits-$ID" 38180 33100 || exit 1
stack_up "ff-e2e-shots-$ID" 38280 33200 || exit 1
for p in 38080 38180 38280; do wait_ready "http://localhost:$p/actuator/health/readiness" 90 || exit 1; done
bash scripts/lib/seed-shots.sh http://localhost:38280 || exit 1

mkdir -p build/reports
HOST=host.docker.internal
docker run --rm --ipc=host --add-host=host.docker.internal:host-gateway \
  -v "$PWD:/repo" -w /repo/frontend \
  -e CI=1 \
  -e UI_URL="http://$HOST:33000" -e API_URL="http://$HOST:38080" \
  -e LIMITS_UI_URL="http://$HOST:33100" -e LIMITS_API_URL="http://$HOST:38180" \
  -e SHOTS_UI_URL="http://$HOST:33200" -e SHOTS_API_URL="http://$HOST:38280" \
  "$PW_IMAGE" npx playwright test --repeat-each=2 "$@"
