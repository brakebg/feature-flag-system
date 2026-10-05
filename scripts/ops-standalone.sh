#!/usr/bin/env bash
# AC-OPS-2 (spec 10.2 standalone check): the backend image runs with only FF_DB_URL, FF_DB_USER
# and FF_DB_PASSWORD next to a separate postgres:16-alpine container; the UI image runs with only
# BACKEND_URL pointing at that backend. Images must be built already (same tags as compose).
# Writes build/reports/script-results/ops-standalone.json for gate 14.
set -uo pipefail
cd "$(dirname "$0")/.."
VERSION=$(cat VERSION)
ID="ff-sa-$(date +%s)-$$"
OUT=build/reports/script-results/ops-standalone.json
mkdir -p "$(dirname "$OUT")"
result() { printf '[{"title":"[AC-OPS-2] backend image standalone with only DB env vars; UI image standalone with only BACKEND_URL","status":"%s"}]\n' "$1" > "$OUT"; }
result failed
cleanup() {
  docker rm -f "$ID-ui" "$ID-backend" "$ID-db" >/dev/null 2>&1
  docker network rm "$ID" >/dev/null 2>&1
}
trap cleanup EXIT
fail() { echo "ops-standalone: $1" >&2; docker logs "$ID-backend" 2>&1 | tail -20 >&2; exit 1; }

docker network create "$ID" >/dev/null || fail "network"
docker run -d --name "$ID-db" --network "$ID" -e POSTGRES_USER=ff -e POSTGRES_PASSWORD=ffpass \
  -e POSTGRES_DB=ff postgres:16-alpine >/dev/null || fail "postgres"
docker run -d --name "$ID-backend" --network "$ID" -p 127.0.0.1::8080 \
  -e FF_DB_URL="jdbc:postgresql://$ID-db:5432/ff" -e FF_DB_USER=ff -e FF_DB_PASSWORD=ffpass \
  "feature-flag-backend:$VERSION" >/dev/null || fail "backend"
BPORT=$(docker port "$ID-backend" 8080 | head -1 | sed 's/.*://')
ok=0
for _ in $(seq 1 180); do
  body=$(curl -s -w ' %{http_code}' "http://127.0.0.1:$BPORT/actuator/health/readiness")
  if [ "$body" = '{"status":"UP"} 200' ]; then ok=1; break; fi
  sleep 0.5
done
[ "$ok" = 1 ] || fail "backend readiness not 200 {\"status\":\"UP\"} within 90 s (last: $body)"

docker run -d --name "$ID-ui" --network "$ID" -p 127.0.0.1::80 \
  -e BACKEND_URL="http://$ID-backend:8080" "feature-flag-ui:$VERSION" >/dev/null || fail "ui"
UPORT=$(docker port "$ID-ui" 80 | head -1 | sed 's/.*://')
for _ in $(seq 1 60); do curl -s -o /dev/null "http://127.0.0.1:$UPORT/" && break; sleep 0.5; done
read -r code type < <(curl -s -o /dev/null -w '%{http_code} %{content_type}\n' "http://127.0.0.1:$UPORT/")
[ "$code" = 200 ] && [[ "$type" == text/html* ]] || fail "UI GET / gave $code $type"
code=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$UPORT/api/v1/evaluate/flags")
[ "$code" = 401 ] || fail "UI GET /api/v1/evaluate/flags without a token gave $code, expected 401"
echo "ops-standalone: backend UP, UI 200 text/html, proxied 401"
result passed
