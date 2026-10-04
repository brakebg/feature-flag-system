# Shared helpers for gates 11-13 (spec 11.5): each run starts its own compose stack under a
# unique project name and removes it with `docker compose down -v` in a trap.
# Usage: source scripts/lib/stack.sh; stack_up <project> <backend-port> <ui-port> [ENV=VALUE ...]

STACK_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
STACKS=()

stack_cleanup() {
  for p in "${STACKS[@]}"; do
    (cd "$STACK_ROOT" && docker compose -p "$p" down -v --remove-orphans >/dev/null 2>&1) || echo "warn: could not remove stack $p" >&2
  done
}
trap stack_cleanup EXIT

stack_up() { # project backendPort uiPort [ENV=VALUE ...]
  local project=$1 bport=$2 uport=$3
  shift 3
  STACKS+=("$project")
  (cd "$STACK_ROOT" && env FF_BACKEND_PORT="$bport" FF_UI_PORT="$uport" FF_VERSION="$(cat VERSION)" \
    GIT_COMMIT="$(git rev-parse HEAD)" "$@" docker compose -p "$project" up -d --build --quiet-pull)
}

wait_ready() { # url seconds
  local url=$1 secs=$2
  for _ in $(seq 1 $((secs * 2))); do
    if [ "$(curl -s -o /dev/null -w '%{http_code}' "$url")" = "200" ]; then return 0; fi
    sleep 0.5
  done
  echo "not ready: $url after ${secs}s" >&2
  return 1
}

run_id() { echo "$(date +%s)-$$"; }
