#!/usr/bin/env bash
# Owner review: checks the builder's branch against main.
#
# Run from a CLEAN checkout of main, so this script and scripts/locked-paths.txt are the
# owner's copies (the builder cannot change what runs here).
#
# Usage: scripts/owner-review.sh [REF] [--since REF] [--no-suite]
#   REF        branch to review (default: origin/feature/spring-boot-4)
#   --since    only check test changes after this ref (default: where the branch left main)
#   --no-suite skip the black-box acceptance suite (no Docker needed)
#   Ports used by the suite stacks: 8080/3000 (default), 8280/3200 (https), 8380/3300 (limits);
#   stop any other local stack first.
# Env: ACCEPTANCE_DIR (default: ../feature-flag-acceptance)
#
# Exit 0 = no FAIL. Exit 1 = at least one FAIL. WARN = look at it yourself.
set -uo pipefail

REF="origin/feature/spring-boot-4"
SINCE=""
RUN_SUITE=1
while [ $# -gt 0 ]; do
  case "$1" in
    --since) SINCE="$2"; shift 2 ;;
    --no-suite) RUN_SUITE=0; shift ;;
    -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
    *) REF="$1"; shift ;;
  esac
done

ROOT=$(git rev-parse --show-toplevel) || exit 2
cd "$ROOT"
ACCEPTANCE_DIR="${ACCEPTANCE_DIR:-$ROOT/../feature-flag-acceptance}"
HEALTH_TIMEOUT_S=90
mkdir -p build
REPORT="build/owner-review-$(date +%Y%m%d-%H%M%S).md"
FAILS=0
WARNS=0

log()  { printf '%s\n' "$*" | tee -a "$REPORT"; }
pass() { log "- PASS: $*"; }
warn() { WARNS=$((WARNS + 1)); log "- WARN: $*"; }
fail() { FAILS=$((FAILS + 1)); log "- FAIL: $*"; }
show() { git show "$REF:$1" 2>/dev/null; }

TEST_PATHS=(":(glob)backend/src/test/**" ":(glob)frontend/src/**/*.test.ts"
            ":(glob)frontend/src/**/*.test.tsx" ":(glob)frontend/e2e/**")
CODE_PATHS=(":(glob)backend/src/**" ":(glob)frontend/src/**" ":(glob)frontend/e2e/**")

# ---------------------------------------------------------------- 0. Preconditions
log "# Owner review — $(date '+%F %T')"
log ""
log "## 0. Preconditions"
if [ "$(git rev-parse --abbrev-ref HEAD)" != "main" ]; then
  fail "not on main. Run from a main checkout so the script and locked list are the owner's copy."
fi
if [ -n "$(git status --porcelain)" ]; then
  fail "working tree is not clean. Commit or stash first."
fi
if git remote get-url origin >/dev/null 2>&1; then
  git fetch --quiet origin || warn "git fetch failed; using local refs"
fi
if ! git rev-parse --verify --quiet "$REF^{commit}" >/dev/null; then
  fail "branch '$REF' not found"; log ""; log "Result: $FAILS FAIL, $WARNS WARN"; exit 1
fi
BASE=$(git merge-base main "$REF")
[ -z "$SINCE" ] && SINCE="$BASE"
RANGE="$BASE..$REF"
log "- Reviewing \`$REF\` ($(git rev-parse --short "$REF")), branched from main at $(git rev-parse --short "$BASE")"
[ "$FAILS" -gt 0 ] && { log ""; log "Result: $FAILS FAIL, $WARNS WARN — fix preconditions first"; exit 1; }

# ---------------------------------------------------------------- 1. Locked files
log ""
log "## 1. Locked files (scripts/locked-paths.txt from main)"
while IFS= read -r line; do
  case "$line" in ''|'#'*) continue ;; esac
  path="${line%% *}"
  if [ "${line##* }" = "create-once" ] && [ "$line" != "$path" ]; then
    bad=$(git log -M --format='%h %s' --diff-filter=MDR "$RANGE" -- ":(glob)$path")
    label="$path (create-once)"
  else
    bad=$(git log -M --format='%h %s' "$RANGE" -- ":(glob)$path")
    label="$path"
  fi
  if [ -n "$bad" ]; then
    fail "$label changed in: $(printf '%s' "$bad" | tr '\n' ';' | sed 's/;$//')"
  else
    pass "$label unchanged"
  fi
done < scripts/locked-paths.txt

if show docs/acceptance-criteria.md >/dev/null; then
  missing=$(show docs/acceptance-criteria.md | python3 -c '
import re, sys
spec = open("docs/SPEC.md", encoding="utf-8").read()
sec = spec[spec.index("### 11.2"):spec.index("### 11.3")]
want = [m.group(1).strip() for m in re.finditer(r"^\s*[-*] \[[ x]\] (.+)$", sec, re.M)]
have = sys.stdin.read()
print(sum(1 for w in want if w not in have))')
  if [ "$missing" = "0" ]; then pass "docs/acceptance-criteria.md contains all spec 11.2 criteria word for word"
  else fail "docs/acceptance-criteria.md is missing or changed $missing criteria from spec 11.2"; fi
else
  warn "docs/acceptance-criteria.md not created yet (expected in M1)"
fi

# ---------------------------------------------------------------- 2. Locked values
log ""
log "## 2. Locked values (spec 11, 11.3) — pattern checks, confirm any FAIL by eye"
check_value() { # label file extended-regex
  local content
  if ! content=$(show "$2"); then warn "$1: $2 not found (not built yet?)"; return; fi
  if printf '%s' "$content" | grep -Eq "$3"; then pass "$1"
  else fail "$1: expected /$3/ in $2"; fi
}
check_value "JaCoCo line coverage 80%"        backend/pom.xml '<minimum>0?\.80?</minimum>'
check_value "PIT mutation threshold 60%"      backend/pom.xml '<mutationThreshold>60</mutationThreshold>'
for dep in redis jedis lettuce hazelcast kafka rabbitmq jjwt; do
  check_value "Banned backend dependency listed: $dep" backend/pom.xml "$dep"
done
if show frontend/vitest.config.ts >/dev/null; then VCFG=frontend/vitest.config.ts; else VCFG=frontend/vite.config.ts; fi
check_value "Frontend coverage 70%"           "$VCFG" 'lines:[[:space:]]*70'
check_value "Playwright retries 0"            frontend/playwright.config.ts 'retries:[[:space:]]*0'
check_value "Screenshot maxDiffPixelRatio 0.01" frontend/playwright.config.ts 'maxDiffPixelRatio:[[:space:]]*0\.01'
check_value "Playwright repeat-each 2"        Makefile 'repeat-each[= ]2'
check_value "k6 p95 < 50 ms"                  perf/evaluate.js 'p\(95\)[[:space:]]*<[[:space:]]*50'
check_value "k6 200 req/s"                    perf/evaluate.js 'rate:[[:space:]]*200'
for lib in mui antd chakra bootstrap; do
  check_value "Banned UI library listed: $lib" scripts/check-npm-deps.mjs "$lib"
done

# ---------------------------------------------------------------- 3. Weakened tests
log ""
log "## 3. Weakened tests (since $(git rev-parse --short "$SINCE"))"
skips=$(git grep -nE '@Disabled|assumeTrue\(false\)|\.skip\(|\.only\(|\bxit\(|test\.fixme' "$REF" -- "${TEST_PATHS[@]}" 2>/dev/null)
if [ -n "$skips" ]; then fail "skipped or focused tests:"; printf '%s\n' "$skips" | sed 's/^/    /' | tee -a "$REPORT"
else pass "no skipped or focused tests"; fi

noreason=$(git grep -nE 'eslint-disable|@SuppressWarnings' "$REF" -- "${CODE_PATHS[@]}" 2>/dev/null | grep -v 'reason:')
if [ -n "$noreason" ]; then fail "suppressions without a '// reason:' comment:"; printf '%s\n' "$noreason" | sed 's/^/    /' | tee -a "$REPORT"
else pass "every eslint-disable / @SuppressWarnings has a reason"; fi

deleted=$(git log -M --diff-filter=D --name-only --format='' "$SINCE..$REF" -- "${TEST_PATHS[@]}" | sort -u)
if [ -n "$deleted" ]; then warn "test files deleted (check the DECISIONS.md of the spec folder for a reason):"; printf '%s\n' "$deleted" | sed 's/^/    /' | tee -a "$REPORT"
else pass "no test files deleted"; fi

removed=$(git log -M -p --format='@@COMMIT %h %s' "$SINCE..$REF" -- "${TEST_PATHS[@]}" | awk '
  /^@@COMMIT / { c = substr($0, 10); next }
  /^---/ { next }
  /^-/ && /assert|expect\(|@Test|verify\(|toHave|toBe|toEqual/ { n[c]++ }
  END { for (k in n) printf "%3d  %s\n", n[k], k }' | sort -rn)
if [ -n "$removed" ]; then warn "commits that removed assertion lines (count, commit) — read these diffs:"; printf '%s\n' "$removed" | sed 's/^/    /' | tee -a "$REPORT"
else pass "no assertion lines removed"; fi

# ---------------------------------------------------------------- 4. Black-box acceptance suite
# Starts three stacks from the build under review (spec 10.3: compose passes 9.4 variables,
# host ports from FF_BACKEND_PORT / FF_UI_PORT), then runs the suite's 5 phases
# (feature-flag-acceptance/docs/black-box-testing.md section 6a).
log ""
log "## 4. Black-box acceptance suite"
PROJECT_DEFAULT=ff-review
PROJECT_HTTPS=ff-review-https
PROJECT_LIMITS=ff-review-limits
if [ "$RUN_SUITE" = "0" ]; then
  warn "skipped (--no-suite)"
elif [ ! -f "$ACCEPTANCE_DIR/package.json" ]; then
  fail "suite not found at $ACCEPTANCE_DIR (set ACCEPTANCE_DIR)"
else
  tag=$(git -C "$ACCEPTANCE_DIR" describe --tags --exact-match HEAD 2>/dev/null)
  if [ -z "$tag" ]; then warn "suite HEAD is not on a tag (expected frozen v1)"; else pass "suite at tag $tag"; fi
  [ -n "$(git -C "$ACCEPTANCE_DIR" status --porcelain)" ] && warn "suite has uncommitted changes"

  WT=$(mktemp -d)/build
  cleanup() {
    for p in "$PROJECT_DEFAULT" "$PROJECT_HTTPS" "$PROJECT_LIMITS"; do
      (cd "$WT" 2>/dev/null && docker compose -p "$p" down -v >/dev/null 2>&1)
    done
    git worktree remove --force "$WT" >/dev/null 2>&1
  }
  trap cleanup EXIT

  wait_healthy() { # name, port
    local url="http://localhost:$2/actuator/health"
    for _ in $(seq 1 "$HEALTH_TIMEOUT_S"); do
      if curl -fs "$url" 2>/dev/null | grep -q '"UP"'; then pass "$1 stack UP at :$2"; return 0; fi
      sleep 1
    done
    fail "$1 stack not UP at $url within ${HEALTH_TIMEOUT_S}s"; return 1
  }

  stacks_ok=1
  if ! git worktree add --quiet --detach "$WT" "$REF"; then
    fail "could not check out $REF into a temporary worktree"; stacks_ok=0
  else
    # https and limits stacks first, so their image builds do not use the AC-OPS-1 90 s window
    (cd "$WT" && FF_REQUIRE_HTTPS=true FF_BACKEND_PORT=8280 FF_UI_PORT=3200 \
        docker compose -p "$PROJECT_HTTPS" up -d --build) || { fail "https stack failed to start"; stacks_ok=0; }
    (cd "$WT" && FF_BACKEND_PORT=8380 FF_UI_PORT=3300 \
        docker compose -p "$PROJECT_LIMITS" up -d --build) || { fail "limits stack failed to start"; stacks_ok=0; }
    # default stack through `make up` (AC-OPS-1 names it); project name fixed for the ops tests
    (cd "$WT" && COMPOSE_PROJECT_NAME="$PROJECT_DEFAULT" make up) || { fail "make up failed on $REF"; stacks_ok=0; }
    # AC-OPS-1 (spec 10.3, Q-074): the 90 s window starts right after `make up` returns
    STACK_STARTED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  fi
  if [ "$stacks_ok" = "1" ]; then
    wait_healthy default 8080 || stacks_ok=0
    wait_healthy https 8280 || stacks_ok=0
    wait_healthy limits 8380 || stacks_ok=0
  fi
  if [ "$stacks_ok" = "1" ]; then
    if (cd "$ACCEPTANCE_DIR" && \
        UI_URL=http://localhost:3000 API_URL=http://localhost:8080 \
        HTTPS_API_URL=http://localhost:8280 LIMITS_API_URL=http://localhost:8380 \
        FF_COMPOSE_DIR="$WT" FF_COMPOSE_PROJECT="$PROJECT_DEFAULT" FF_STACK_STARTED_AT="$STACK_STARTED_AT" \
        npm test); then
      pass "black-box acceptance suite passed (5 phases)"
    else
      fail "black-box acceptance suite failed — see $ACCEPTANCE_DIR/reports/"
    fi
  fi
fi

# ---------------------------------------------------------------- Summary
log ""
log "## Result: $FAILS FAIL, $WARNS WARN"
log "Report: $REPORT"
[ "$FAILS" -eq 0 ]
