#!/usr/bin/env bash
# Owner review: checks the builder's branch against main.
#
# Run from a CLEAN checkout of main, so this script and scripts/locked-paths.txt are the
# owner's copies (the builder cannot change what runs here).
#
# Usage: scripts/owner-review.sh <TARGET> [--since REF] [--no-suite]
#   TARGET     required. The script does not fetch: run `git fetch origin <branch>` first.
#                docs/specs/<NNN-name>   spec folder: branch = "branch" in its spec.json
#                <NNN>                   spec id: the one docs/specs/<NNN>-* folder, as above
#                origin/<branch>         that remote branch
#                <branch>                origin/<branch> if it exists, else the local branch
#                <commit>                any commit (e.g. an old state to re-check)
#              A spec that is not merged yet is found through its spec-NNN-ready tag.
#   --since    check weakened tests (section 3) and test changes (3b) only after this ref
#              (default: section 3 from the spec-NNN-ready tag in the branch, 3b like CI from
#              where the branch left main)
#   --no-suite skip the black-box acceptance suite (no Docker needed)
#   -h, --help print this header
#   Ports used by the suite stacks: 8080/3000 (default), 8280/3200 (https), 8380/3300 (limits);
#   stop any other local stack first.
# Env: ACCEPTANCE_DIR (default: ../feature-flag-acceptance)
#
# Exit 0 = no FAIL. Exit 1 = at least one FAIL. WARN = look at it yourself.
#      Exit 2 = wrong usage or TARGET does not resolve; nothing was checked.
set -uo pipefail

usage()     { sed -n '2,22p' "$0"; }
die()       { echo "owner-review.sh: $*" >&2; exit 2; }
usage_die() { echo "owner-review.sh: $*" >&2; usage >&2; exit 2; }
spec_field() { python3 -c 'import json,sys; v=json.load(sys.stdin).get(sys.argv[1]); print("" if v is None else v)' "$2" 2>/dev/null < <(spec_json "$1"); }
# spec.json from main's tree, or (spec not merged yet) from its spec-NNN-ready tag.
spec_json() { if [ -f "$1" ]; then cat "$1"; else git show "$SPEC_TAG:$1" 2>/dev/null; fi; }

# ---------------------------------------------------------------- Arguments
TARGET=""
SINCE=""
RUN_SUITE=1
while [ $# -gt 0 ]; do
  case "$1" in
    --since)
      [ $# -ge 2 ] && [ -n "$2" ] || usage_die "--since needs a REF"
      SINCE="$2"; shift 2 ;;
    --no-suite) RUN_SUITE=0; shift ;;
    -h|--help) usage; exit 0 ;;
    -*) usage_die "unknown option: $1" ;;
    *)
      [ -z "$TARGET" ] || usage_die "more than one TARGET ('$TARGET' and '$1')"
      TARGET="$1"; shift ;;
  esac
done
[ -n "$TARGET" ] || usage_die "missing TARGET (spec folder, spec id or branch)"

# ---------------------------------------------------------------- Resolve TARGET (before any check)
ROOT=$(git rev-parse --show-toplevel) || exit 2
cd "$ROOT"
T="${TARGET%/}"
SPEC_DIR=""
SPEC_ID=""
SPEC_TAG=""
case "$T" in
  docs/specs/[0-9][0-9][0-9]-*)
    SPEC_DIR="$T"
    [ -d "$T" ] || SPEC_TAG="spec-$(basename "$T" | cut -c1-3)-ready" ;;
  [0-9][0-9][0-9])
    shopt -s nullglob
    matches=(docs/specs/"$T"-*/)
    shopt -u nullglob
    if [ "${#matches[@]}" -eq 1 ]; then SPEC_DIR="${matches[0]%/}"
    else
      SPEC_TAG="spec-$T-ready"
      SPEC_DIR=$(git ls-tree --name-only "$SPEC_TAG" docs/specs/ 2>/dev/null | grep "^docs/specs/$T-" | head -1)
      [ -n "$SPEC_DIR" ] || die "spec id $T: no folder docs/specs/$T-* on main and no tag $SPEC_TAG. run: git fetch origin --tags"
    fi ;;
  docs/specs/*) die "no spec folder $T" ;;
esac
if [ -n "$SPEC_DIR" ]; then
  spec_json "$SPEC_DIR/spec.json" >/dev/null || die "spec folder $SPEC_DIR has no spec.json (on main or at ${SPEC_TAG:-its tag}). run: git fetch origin --tags"
  BRANCH=$(spec_field "$SPEC_DIR/spec.json" branch)
  [ -n "$BRANCH" ] || die "$SPEC_DIR/spec.json has no \"branch\" field"
  SPEC_ID=$(spec_field "$SPEC_DIR/spec.json" id)
  SPEC_TITLE=$(spec_field "$SPEC_DIR/spec.json" title)
  REF="origin/$BRANCH"
  git rev-parse --verify --quiet "$REF^{commit}" >/dev/null \
    || die "'$TARGET' does not resolve: $REF not found. run: git fetch origin $BRANCH"
else
  if git rev-parse --verify --quiet "origin/$T^{commit}" >/dev/null; then REF="origin/$T"
  elif git rev-parse --verify --quiet "$T^{commit}" >/dev/null; then REF="$T"
  else die "'$T' does not resolve to a commit. run: git fetch origin ${T#origin/}"
  fi
fi
BASE=$(git merge-base main "$REF" 2>/dev/null) || BASE=""
RANGE="$BASE..$REF"

# Spec flow split (docs/process/spec-flow.md section 3): commits up to the newest spec-NNN-ready
# tag in the branch are the approved spec; a commit tagged spec-NNN-amend-<k> is an owner
# amendment; every other commit is the builder's.
# Only the reviewed spec's ready tag counts (without a spec target: the specs this branch changes).
READY=""; TAG_PROBLEMS=""
if [ -n "$SPEC_ID" ]; then nums="$SPEC_ID"
else nums=$(git diff --name-only "$BASE" "$REF" -- docs/specs | sed -nE 's#^docs/specs/([0-9]{3})-.*#\1#p' | sort -u); fi
for n in $nums; do
  t="spec-${n}-ready"
  git rev-parse -q --verify "refs/tags/$t" >/dev/null || continue
  if ! c=$(git rev-parse -q --verify "$t^{commit}"); then TAG_PROBLEMS="$TAG_PROBLEMS $t(not a commit)"; continue; fi
  if ! git merge-base --is-ancestor "$c" "$REF"; then TAG_PROBLEMS="$TAG_PROBLEMS $t(not in the branch)"; continue; fi
  git merge-base --is-ancestor "$c" main && continue
  if [ -n "$READY" ]; then TAG_PROBLEMS="$TAG_PROBLEMS $t(a second spec)"; continue; fi
  READY="$t"
done
SPEC_COMMITS=""; AMEND_COMMITS=""
if [ -n "$READY" ]; then
  SPEC_COMMITS=$(git rev-list "$BASE..$READY^{commit}")
  for t in $(git tag -l "${READY%-ready}-amend-*"); do
    [[ "$t" =~ ^spec-[0-9]{3}-amend-[0-9]+$ ]] || continue
    if ! c=$(git rev-parse -q --verify "$t^{commit}"); then TAG_PROBLEMS="$TAG_PROBLEMS $t(not a commit)"; continue; fi
    git merge-base --is-ancestor "$READY" "$c" && git merge-base --is-ancestor "$c" "$REF" && AMEND_COMMITS="$AMEND_COMMITS $c"
  done
fi
# The newest owner version: the last amendment, else the ready commit, else main's base. A locked
# file that no builder commit explains must hold it, or it merged with the main commit the branch
# last took in (the allowed main merge).
TIP="$BASE"
if [ -n "$READY" ]; then
  TIP=$(git rev-parse "$READY^{commit}")
  for c in $AMEND_COMMITS; do
    if git merge-base --is-ancestor "$TIP" "$c"; then TIP="$c"
    elif ! git merge-base --is-ancestor "$c" "$TIP"; then TAG_PROBLEMS="$TAG_PROBLEMS amend-tags(not in one line)"; fi
  done
fi
MERGED=$(git merge-tree --write-tree "$TIP" "$BASE" 2>/dev/null) || true; MERGED="${MERGED%%$'\n'*}"
# Text from the branch (names, subjects) is printed without control characters.
clean() { tr -d '\000-\010\013-\037\177'; }
TC_BASE="${SINCE:-$BASE}" # gate 16 compares like CI: from where the branch left main
[ -z "$SINCE" ] && SINCE="${READY:-$BASE}"

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
log "- Target: $T → $REF ($(git rev-parse --short "$REF")), branched from main at $(git rev-parse --short "$BASE" 2>/dev/null)"
[ -n "$SPEC_DIR" ] && log "- Spec: $SPEC_ID $SPEC_TITLE ($SPEC_DIR${SPEC_TAG:+, read from $SPEC_TAG})"
log "- Approved spec: ${READY:-none (no spec-NNN-ready tag in the branch: every change counts as a builder change)}"
log ""
log "## 0. Preconditions"
if [ "$(git rev-parse --abbrev-ref HEAD)" != "main" ]; then
  fail "not on main. Run from a main checkout so the script and locked list are the owner's copy."
fi
if [ -n "$(git status --porcelain)" ]; then
  fail "working tree is not clean. Commit or stash first."
fi
[ "$FAILS" -gt 0 ] && { log ""; log "Result: $FAILS FAIL, $WARNS WARN — fix preconditions first"; exit 1; }

# ---------------------------------------------------------------- 1. Locked files
log ""
log "## 1. Locked files (scripts/locked-paths.txt from main)"
while IFS= read -r line; do
  case "$line" in ''|'#'*) continue ;; esac
  path="${line%% *}"
  if [ "${line##* }" = "create-once" ] && [ "$line" != "$path" ]; then
    commits=$(git log -M --format='%H' --diff-filter=MDR "$RANGE" -- ":(glob)$path")
    label="$path (create-once)"
  else
    commits=$(git log -M --format='%H' "$RANGE" -- ":(glob)$path")
    label="$path"
  fi
  owner=""; builder=""
  for c in $commits; do
    if printf '%s\n' "$SPEC_COMMITS" $AMEND_COMMITS | grep -qx "$c"; then owner="$owner $(git rev-parse --short "$c")"
    else builder="$builder;$(git log -1 --format='%h %s' "$c")"; fi
  done
  # A merge can change a file without any commit touching it, or bring back an older owner
  # version (git log then shows only the old owner commit): compare with the newest owner version.
  if [ -z "$builder" ] && ! git diff --quiet "$TIP" "$REF" -- ":(glob)$path" \
    && ! { [ -n "$MERGED" ] && git diff --quiet "$MERGED" "$REF" -- ":(glob)$path"; } \
    && ! { [ "$label" != "$path" ] && [ -z "$(git ls-tree -r --name-only "$TIP" -- ":(glob)$path")" ]; }; then
    builder=";(content differs from the newest owner version: changed through a merge)"
  fi
  if [ -n "$builder" ]; then fail "$label changed outside the approved spec and its amendments in: $(printf '%s' "${builder#;}" | clean)"
  elif [ -n "$owner" ]; then pass "$label changed only in the approved spec or an amendment:$owner"
  else pass "$label unchanged"; fi
done < scripts/locked-paths.txt

[ -n "$TAG_PROBLEMS" ] && fail "spec tag problems:$TAG_PROBLEMS"
tags=$(for t in $(git tag -l 'spec-*'); do
  if ! c=$(git rev-parse -q --verify "$t^{commit}"); then echo "$t → (not a commit)"; continue; fi
  git merge-base --is-ancestor "$c" "$REF" && ! git merge-base --is-ancestor "$c" main && \
    git for-each-ref --format="%(refname:short) → $(git rev-parse --short "$c") · %(taggername)%(authorname) · %(creatordate:short)" "refs/tags/$t"
done | clean)
if [ -n "$tags" ]; then
  warn "spec tags in this branch — confirm each one is yours (spec-flow.md section 3):"
  printf '%s\n' "$tags" | sed 's/^/    /' | tee -a "$REPORT"
fi

if show docs/project-acceptance-criteria.md >/dev/null; then
  missing=$(show docs/project-acceptance-criteria.md | python3 -c '
import re, sys
spec = open("docs/specs/001-feature-flag-service/SPEC.md", encoding="utf-8").read()
start = spec.index("### 11.2")
nxt = spec.find("\n#", start + 1)
sec = spec[start:] if nxt < 0 else spec[start:nxt]
want = [m.group(1).strip() for m in re.finditer(r"^\s*[-*] \[[ x]\] (.+)$", sec, re.M)]
line = re.compile(r"^- \[[ x]\] \*\*(AC-[A-Z]+-\d+)\*\* v(\d+) · (.*) · spec (\d{3})(?: · changed by spec (\d{3}))?(?: · due M\d+| · retired by .+)$")
base = [m for m in (line.match(l) for l in sys.stdin.read().split("\n")) if m and m.group(4) == "001"]
bad = sum(1 for m in base if not m.group(5) and (m.group(2) != "1" or m.group(3).strip() not in want))
print(bad + abs(len(base) - len(want)))')
  if [ "$missing" = "0" ]; then pass "docs/project-acceptance-criteria.md: every unchanged spec 001 criterion is word for word its 11.2 text"
  else fail "docs/project-acceptance-criteria.md: $missing spec 001 criteria differ from 11.2 without a version and \"changed by\""; fi
else
  warn "docs/project-acceptance-criteria.md not found"
fi

# A criterion whose text changed after the approved spec must have a higher version (Q4).
if [ -n "$READY" ]; then
  unversioned=$(python3 -c '
import re, subprocess, sys
line = re.compile(r"^- \[[ x]\] \*\*(AC-[A-Z]+-\d+)\*\* v(\d+) · (.*) · spec \d{3}")
def read(ref):
    out = subprocess.run(["git", "show", f"{ref}:docs/project-acceptance-criteria.md"], capture_output=True, text=True).stdout
    return {m.group(1): (int(m.group(2)), m.group(3)) for m in map(line.match, out.split("\n")) if m}
before, after = read(sys.argv[1]), read(sys.argv[2])
for i, (v, t) in after.items():
    if i in before and t != before[i][1] and v <= before[i][0]:
        print(f"{i} text changed after {sys.argv[1]} but version stays v{v}")
' "$READY" "$REF")
  if [ -n "$unversioned" ]; then fail "criteria changed without a higher version (spec-flow.md section 4):"; printf '%s\n' "$unversioned" | sed 's/^/    /' | tee -a "$REPORT"
  else pass "every criterion changed after $READY has a higher version"; fi
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

# ---------------------------------------------------------------- 3b. Test changes (gate 16, main's copy)
# Every changed test whose criteria did not change must be plumbing only (spec 004, spec 006).
log ""
log "## 3b. Test changes (gate 16, scripts/check-test-changes.mjs from main)"
# main's script, run on a copy of the branch: the raised criterion versions exist only there.
TC_WT=$(mktemp -d)/tc
git worktree add --quiet --detach "$TC_WT" "$REF"
tc_args=(--base "$TC_BASE" --head "$REF" --root "$TC_WT")
tc_ok=1; tc_out=$(node scripts/check-test-changes.mjs "${tc_args[@]}" 2>&1) || tc_ok=0
git worktree remove --force "$TC_WT" >/dev/null 2>&1
if [ "$tc_ok" = "1" ]; then
  pass "only allowed tests changed beyond plumbing ($(printf '%s\n' "$tc_out" | tail -1))"
else
  fail "tests changed beyond plumbing whose criteria did not change:"
  printf '%s\n' "$tc_out" | sed 's/^/    /' | tee -a "$REPORT"
fi

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
  sbranch=$(git -C "$ACCEPTANCE_DIR" rev-parse --abbrev-ref HEAD 2>/dev/null)
  if [ -n "$SPEC_ID" ] && [ "$sbranch" != "spec-$SPEC_ID" ]; then warn "suite is on '$sbranch', expected branch spec-$SPEC_ID (spec-flow.md section 1)"
  else pass "suite on $sbranch"; fi
  [ -n "$(git -C "$ACCEPTANCE_DIR" status --porcelain)" ] && warn "suite has uncommitted changes"

  # Criteria versions (spec-flow.md section 4): every criterion that counts for this branch needs
  # a suite test titled "[<ID> v<N>]" at its current version; "[<ID>]" alone counts as v1.
  # Skipped: retired criteria, and criteria the suite's docs/coverage.md marks "White-box only" or
  # "Removed" (the builder's own tests cover them). Same rule as the suite's npm run spec:versions
  # (issue #62); the skipped IDs are listed so the owner sees them.
  live=$(for d in $(git ls-tree --name-only "$REF" docs/specs/); do
    git cat-file -e "$REF:$d/builder/MILESTONE" 2>/dev/null && basename "$d" | cut -c1-3
  done | tr '\n' ' ')
  stale=$(show docs/project-acceptance-criteria.md | python3 -c '
import re, sys, pathlib
live = set(sys.argv[1].split()) | {"001", sys.argv[2]}
line = re.compile(r"^- \[[ x]\] \*\*(AC-[A-Z]+-\d+)\*\* v(\d+) · .* · spec (\d{3})(?: · changed by spec (\d{3}))?(?: · due M\d+| · retired by (.+))$")
want = {}
for l in sys.stdin.read().split("\n"):
    m = line.match(l)
    if not m or m.group(5): continue
    spec, by = m.group(3), m.group(4)
    if by and by in live: want[m.group(1)] = int(m.group(2))
    elif by: want[m.group(1)] = 1 if spec in live else None
    elif spec in live: want[m.group(1)] = int(m.group(2))
skip = {}
cov = pathlib.Path(sys.argv[3], "docs", "coverage.md")
for l in (cov.read_text(errors="ignore").split("\n") if cov.is_file() else []):
    m = re.match(r"^\| (AC-[A-Z]+-\d+)(?: v\d+)? \| ([^|]+) \|", l)
    if m and re.match(r"(White-box only|Removed)", m.group(2).strip()): skip[m.group(1)] = m.group(2).strip()
have = {}
for f in pathlib.Path(sys.argv[3]).rglob("*"):
    if "node_modules" in f.parts or not f.is_file() or f.suffix not in (".ts", ".js", ".mjs"): continue
    for m in re.finditer(r"\[(AC-[A-Z]+-\d+)(?: v(\d+))?\]", f.read_text(errors="ignore")):
        have.setdefault(m.group(1), set()).add(int(m.group(2) or 1))
for i, v in sorted(want.items()):
    if v is None: continue
    if v not in have.get(i, set()):
        if i in skip:
            print(f"SKIP {i}  ({skip[i]})")
            continue
        got = ", ".join(f"v{x}" for x in sorted(have.get(i, set())))
        print(f"STALE {i}  criteria v{v} · " + (f"tests {got}" if got else "no test"))
' "$live" "${SPEC_ID:-000}" "$ACCEPTANCE_DIR")
  skipped=$(printf '%s\n' "$stale" | sed -n 's/^SKIP //p')
  stale=$(printf '%s\n' "$stale" | sed -n 's/^STALE //p')
  if [ -n "$skipped" ]; then
    pass "criteria without a suite test, skipped because the suite's docs/coverage.md marks them White-box only or Removed (check this list):"
    printf '%s\n' "$skipped" | sed 's/^/    /' | tee -a "$REPORT"
  fi
  if [ -n "$stale" ]; then
    fail "suite is stale for these criteria — update spec-$SPEC_ID first (spec-flow.md section 4):"
    printf '%s\n' "$stale" | sed 's/^/    /' | tee -a "$REPORT"
    RUN_SUITE=0
  else
    pass "suite has a test at the current version of every criterion that counts"
  fi
fi
if [ "$RUN_SUITE" = "1" ] && [ -f "$ACCEPTANCE_DIR/package.json" ]; then

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
