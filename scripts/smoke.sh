#!/usr/bin/env bash
# Gate 11 (spec 11.3): Docker smoke test against a fresh compose stack.
# health UP within 90 s -> admin login -> client token -> evaluate seeded
# orders.new-checkout = true -> toggle via Admin API -> evaluate returns false ->
# readiness was DOWN before warm-up -> every UI security header from 10.2 present.
# Writes build/reports/script-results/smoke.json for gate 14.
set -uo pipefail
cd "$(dirname "$0")/.."
source scripts/lib/stack.sh

PROJECT="ff-smoke-$(run_id)"
BPORT=${SMOKE_BACKEND_PORT:-18080}
UPORT=${SMOKE_UI_PORT:-13000}
API="http://localhost:$BPORT"
UI="http://localhost:$UPORT"
OUT=build/reports/script-results/smoke.json
mkdir -p "$(dirname "$OUT")"
RESULTS=()
FAILED=0
record() { # status title
  RESULTS+=("{\"title\":$(jq -Rn --arg t "$2" '$t'),\"status\":\"$1\"}")
  echo "$1: $2"
  [ "$1" = passed ] || FAILED=1
}
finish() {
  printf '[%s]\n' "$(IFS=,; echo "${RESULTS[*]}")" > "$OUT"
  exit "$FAILED"
}

stack_up "$PROJECT" "$BPORT" "$UPORT" || { record failed "[AC-OPS-1] stack starts"; finish; }
started=$(date +%s)

# Poll readiness from the start; a 503 seen before the first 200 means DOWN before warm-up.
saw_down=0
ready=0
while [ $(( $(date +%s) - started )) -lt 90 ]; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "$API/actuator/health/readiness")
  [ "$code" = "503" ] && saw_down=1
  if [ "$code" = "200" ]; then ready=1; break; fi
  sleep 0.05
done
health=$(curl -s "$API/actuator/health")
if [ "$ready" = 1 ] && [ "$(jq -r '.status + "/" + .components.db.status' <<<"$health")" = "UP/UP" ]; then
  record passed "[AC-OPS-3] health UP with db UP within 90 s"
else
  record failed "[AC-OPS-3] health UP with db UP within 90 s"; finish
fi

# Readiness DOWN before warm-up (AC-CACHE-6). The DOWN window is short (spec 11.4), so a 503 seen
# by the poller is the direct proof. Otherwise the backend's own readiness transitions must be in
# this order: REFUSING_TRAFFIC, then warm-up finished, then ACCEPTING_TRAFFIC, with no
# ACCEPTING_TRAFFIC before the warm-up has finished, and the probe must now answer 200 UP.
logs=$(cd "$STACK_ROOT" && docker compose -p "$PROJECT" logs --no-log-prefix backend 2>/dev/null)
line_of() { grep -n "$1" <<<"$logs" | head -1 | cut -d: -f1; }
refusing=$(line_of 'Readiness state: REFUSING_TRAFFIC')
finished=$(line_of 'cache warm-up finished')
accepting=$(line_of 'Readiness state: ACCEPTING_TRAFFIC')
order_ok=0
if [ -n "$refusing" ] && [ -n "$finished" ] && [ -n "$accepting" ] \
  && [ "$refusing" -lt "$finished" ] && [ "$finished" -lt "$accepting" ]; then order_ok=1; fi
if [ "$order_ok" = 1 ] && { [ "$saw_down" = 1 ] || [ "$ready" = 1 ]; }; then
  [ "$saw_down" = 1 ] && note="503 observed" || note="503 window not observed; state order from the backend"
  echo "readiness: $note"
  record passed "[AC-CACHE-6] readiness DOWN before warm-up, UP after"
else
  record failed "[AC-CACHE-6] readiness DOWN before warm-up, UP after"
fi

admin=$(curl -s -X POST "$API/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.accessToken // empty')
[ -n "$admin" ] && record passed "admin login" || { record failed "admin login"; finish; }

client=$(curl -s -X POST "$API/api/v1/auth/token" -u 'order-service:order-service-dev-secret' \
  -H 'Content-Type: application/x-www-form-urlencoded' -d 'grant_type=client_credentials&scope=flags:read' \
  | jq -r '.access_token // empty')
[ -n "$client" ] && record passed "[AC-EVAL-1] client token" || { record failed "[AC-EVAL-1] client token"; finish; }

eval_flag() { curl -s "$API/api/v1/evaluate/flags/orders/new-checkout" -H "Authorization: Bearer $client" | jq -r '.enabled'; }
[ "$(eval_flag)" = "true" ] && record passed "evaluate seeded orders.new-checkout = true" \
  || record failed "evaluate seeded orders.new-checkout = true"

gid=$(curl -s "$API/api/v1/admin/groups?q=orders" -H "Authorization: Bearer $admin" | jq -r '.[] | select(.key=="orders") | .id')
fid=$(curl -s "$API/api/v1/admin/groups/$gid" -H "Authorization: Bearer $admin" | jq -r '.flags[] | select(.key=="new-checkout") | .id')
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/api/v1/admin/flags/$fid/toggle" \
  -H "Authorization: Bearer $admin" -H 'Content-Type: application/json' -d '{"enabled":false}')
[ "$code" = "200" ] && record passed "toggle via Admin API" || record failed "toggle via Admin API"
[ "$(eval_flag)" = "false" ] && record passed "[AC-EVAL-5] evaluate returns false after toggle" \
  || record failed "[AC-EVAL-5] evaluate returns false after toggle"

# Security headers on every kind of UI response (10.2).
expect_csp="default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
headers_ok=1
for path in / /groups/some-id /assets/missing.js /api/v1/evaluate/flags; do
  for proto in "" http https; do
    h=$(curl -s -D - -o /dev/null ${proto:+-H "X-Forwarded-Proto: $proto"} "$UI$path" | tr -d '\r')
    check() { [ "$(grep -ic "^$1:" <<<"$h")" = "1" ] && grep -iq "^$1: $2\$" <<<"$h"; }
    check Content-Security-Policy "$expect_csp" || headers_ok=0
    check X-Content-Type-Options nosniff || headers_ok=0
    check X-Frame-Options DENY || headers_ok=0
    check Referrer-Policy no-referrer || headers_ok=0
    check Permissions-Policy 'camera=(), microphone=(), geolocation=()' || headers_ok=0
    hsts=$(grep -ic '^Strict-Transport-Security:' <<<"$h")
    if [ "$proto" = https ]; then
      check Strict-Transport-Security 'max-age=31536000; includeSubDomains' || headers_ok=0
    else
      [ "$hsts" = "0" ] || headers_ok=0
    fi
    [ "$headers_ok" = 1 ] || { echo "header check failed: $path proto=$proto"; echo "$h"; break 2; }
  done
done
[ "$headers_ok" = 1 ] && record passed "[AC-OPS-4] UI security headers (HSTS only with X-Forwarded-Proto: https)" \
  || record failed "[AC-OPS-4] UI security headers (HSTS only with X-Forwarded-Proto: https)"

finish
