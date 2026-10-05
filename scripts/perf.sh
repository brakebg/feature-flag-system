#!/usr/bin/env bash
# Gate 13 (spec 9.2, 11.3, AC-CACHE-9): k6 load against a fresh compose stack.
# Creates its own group and flags, reads cache_gets_total just before and just after the
# 60 s load, and requires hit / (hit + miss) >= 0.99 over flagCache, groupCache and
# allFlagsCache. Writes build/reports/script-results/perf.json for gate 14.
set -uo pipefail
cd "$(dirname "$0")/.."
source scripts/lib/stack.sh

PROJECT="ff-perf-$(run_id)"
BPORT=${PERF_BACKEND_PORT:-28080}
UPORT=${PERF_UI_PORT:-23000}
API="http://localhost:$BPORT"
OUT=build/reports/script-results/perf.json
mkdir -p "$(dirname "$OUT")"
result() { printf '[{"title":"[AC-CACHE-9] cache hit rate >= 99 %% and p95 < 50 ms under 200 req/s","status":"%s"}]\n' "$1" > "$OUT"; }
result failed

stack_up "$PROJECT" "$BPORT" "$UPORT" COMPOSE_FILE=docker-compose.yml:perf/compose.limits.yml || exit 1
wait_ready "$API/actuator/health/readiness" 90 || exit 1

admin=$(curl -s -X POST "$API/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.accessToken')
client=$(curl -s -X POST "$API/api/v1/auth/token" -u 'order-service:order-service-dev-secret' \
  -d 'grant_type=client_credentials' | jq -r '.access_token')
group="perf-$(date +%s)"
gid=$(curl -s -X POST "$API/api/v1/admin/groups" -H "Authorization: Bearer $admin" \
  -H 'Content-Type: application/json' -d "{\"key\":\"$group\",\"name\":\"Perf\"}" | jq -r '.id')
keys=()
for i in $(seq 1 10); do
  curl -s -o /dev/null -X POST "$API/api/v1/admin/groups/$gid/flags" -H "Authorization: Bearer $admin" \
    -H 'Content-Type: application/json' -d "{\"key\":\"flag-$i\",\"enabled\":$([ $((i % 2)) = 0 ] && echo true || echo false)}"
  keys+=("flag-$i")
done

counters() { # prints "hit miss" summed over the three caches
  curl -s "$API/actuator/prometheus" -H "Authorization: Bearer $admin" | awk '
    /^cache_gets_total\{/ && /cache="(flagCache|groupCache|allFlagsCache)"/ {
      if ($0 ~ /result="hit"/) h += $NF; else if ($0 ~ /result="miss"/) m += $NF }
    END { printf "%d %d\n", h, m }'
}
# The JVM keeps compiling startup code for a while after readiness. On 1 vCPU that work would
# compete with the measured load, so wait until the backend is idle (CPU < 5 % in 3 samples in a
# row, at most 60 s). No requests are sent in this time and none are left out of the measurement.
idle=0
for _ in $(seq 1 60); do
  cpu=$(docker stats --no-stream --format '{{.CPUPerc}}' "$PROJECT-backend-1" | tr -d '%')
  if awk -v c="$cpu" 'BEGIN { exit !(c < 5) }'; then idle=$((idle + 1)); else idle=0; fi
  [ "$idle" -ge 3 ] && break
done
echo "backend idle before the load (last CPU ${cpu}%)"
read -r h0 m0 < <(counters)
build/tools/k6 run --quiet -e API_URL="$API" -e CLIENT_TOKEN="$client" -e GROUP_KEY="$group" \
  -e FLAG_KEYS="$(IFS=,; echo "${keys[*]}")" perf/evaluate.js
k6_status=$?
read -r h1 m1 < <(counters)
dh=$((h1 - h0)); dm=$((m1 - m0))
rate=$(awk -v h="$dh" -v m="$dm" 'BEGIN { if (h + m == 0) print 0; else printf "%.4f", h / (h + m) }')
echo "cache hits: $dh, misses: $dm, hit rate: $rate"
ok=$(awk -v r="$rate" 'BEGIN { print (r >= 0.99) ? 1 : 0 }')
if [ "$k6_status" = 0 ] && [ "$ok" = 1 ]; then result passed; exit 0; fi
echo "perf failed: k6 exit $k6_status, hit rate $rate (need >= 0.99)"
exit 1
