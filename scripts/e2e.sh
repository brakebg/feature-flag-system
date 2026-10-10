#!/usr/bin/env bash
# Gate 12 (spec 11.3, 11.5, 11.6): Playwright end-to-end against fresh compose stacks.
# Runs with --repeat-each=2 and retries 0 (playwright.config.ts): a test that passes once
# and fails once fails the gate. Report: build/reports/playwright.json (read by gate 14).
# Playwright runs in its official image, so browsers, fonts and screenshot baselines are the
# same on every machine (spec 11.6 baselines are compared pixel by pixel).
#   scripts/e2e.sh                          (FF_UPDATE_BASELINES=1 records new baselines)
set -uo pipefail
cd "$(dirname "$0")/.."
source scripts/lib/stack.sh

# Gate 12 settings live in playwright.config.ts (retries 0); the script takes no extra arguments,
# so no caller can change them. New screenshot baselines need FF_UPDATE_BASELINES=1 (and a
# DECISIONS.md entry in the spec folder, spec 11.6).
if [ "$#" -gt 0 ]; then echo "e2e.sh: no arguments allowed" >&2; exit 2; fi
EXTRA=()
if [ "${FF_UPDATE_BASELINES:-}" = 1 ]; then EXTRA=(--update-snapshots); fi

PW_IMAGE="mcr.microsoft.com/playwright:v$(node -p "require('./frontend/node_modules/@playwright/test/package.json').version")-noble"
ID=$(run_id)
mkdir -p build/reports/script-results
# AC-OPS-1: build the images first, then time `up` until the UI answers (90 s limit, 3 containers).
OPS1=build/reports/script-results/ops-up.json
ops1() { printf '[{"title":"[AC-OPS-1] compose up: three services running, postgres and backend healthy, UI 200 text/html within 90 s","status":"%s"}]\n' "$1" > "$OPS1"; }
ops1 failed
(env FF_VERSION="$(cat VERSION)" GIT_COMMIT="$(git rev-parse HEAD)" docker compose build --quiet) || exit 1
t0=$(date +%s)
stack_up "ff-e2e-$ID" 38080 33000 || exit 1
# 10.3: postgres, backend and frontend running, postgres and backend healthy, GET / 200 text/html.
ops1_ok() {
  local states type
  states=$(docker compose -p "ff-e2e-$ID" ps --format '{{.Service}}={{.State}}/{{.Health}}' | sort | tr '\n' ' ')
  type=$(curl -s -o /dev/null -w '%{http_code} %{content_type}' http://localhost:33000/)
  [[ "$states" == "backend=running/healthy frontend=running/"*" postgres=running/healthy " ]] \
    && [[ "$type" == "200 text/html"* ]]
}
up_secs=-1
for _ in $(seq 1 180); do
  if ops1_ok; then up_secs=$(( $(date +%s) - t0 )); break; fi
  sleep 0.5
done
echo "AC-OPS-1: stack ready after ${up_secs}s (limit 90 s)"
if [ "$up_secs" -ge 0 ] && [ "$up_secs" -le 90 ]; then ops1 passed; fi
ops_status=0
grep -q '"passed"' "$OPS1" || ops_status=1
bash scripts/ops-standalone.sh || ops_status=1
stack_up "ff-e2e-limits-$ID" 38180 33100 FF_REQUIRE_HTTPS=true || exit 1
stack_up "ff-e2e-shots-$ID" 38280 33200 || exit 1
for p in 38080 38180 38280; do wait_ready "http://localhost:$p/actuator/health/readiness" 90 || exit 1; done
bash scripts/lib/seed-shots.sh http://localhost:38280 || exit 1
# Warm the JVMs (JIT) so the first browser tests do not meet a cold backend on a busy machine.
for p in 38080 38280; do
  t=$(curl -s -X POST "http://localhost:$p/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin123"}' | jq -r .accessToken)
  for _ in $(seq 1 30); do
    curl -s -o /dev/null "http://localhost:$p/api/v1/admin/groups" -H "Authorization: Bearer $t"
    curl -s -o /dev/null "http://localhost:$p/api/v1/admin/audit" -H "Authorization: Bearer $t"
  done
done

mkdir -p build/reports
HOST=host.docker.internal
docker run --rm --ipc=host --add-host=host.docker.internal:host-gateway \
  -v "$PWD:/repo" -w /repo/frontend \
  -e CI=1 \
  -e UI_URL="http://$HOST:33000" -e API_URL="http://$HOST:38080" \
  -e LIMITS_UI_URL="http://$HOST:33100" -e LIMITS_API_URL="http://$HOST:38180" \
  -e SHOTS_UI_URL="http://$HOST:33200" -e SHOTS_API_URL="http://$HOST:38280" \
  "$PW_IMAGE" npx playwright test --repeat-each=2 ${EXTRA[@]+"${EXTRA[@]}"}
status=$?
# Keep the backend logs of every stack for the report (the stacks are removed on exit).
for s in "${STACKS[@]}"; do docker compose -p "$s" logs --no-color backend > "build/reports/$s-backend.log" 2>&1; done
[ "$status" = 0 ] && status=$ops_status
exit $status
