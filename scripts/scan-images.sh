#!/usr/bin/env bash
# Gate 10, full verify only (spec 11.3): build both Docker images, then Trivy-scan them.
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION=$(cat VERSION)
export FF_VERSION="$VERSION" GIT_COMMIT="$(git rev-parse HEAD)"
docker compose build --pull --quiet backend frontend
for img in "feature-flag-backend:$VERSION" "feature-flag-ui:$VERSION"; do
  echo "== trivy image $img"
  build/tools/trivy image --quiet --scanners vuln --severity HIGH,CRITICAL --exit-code 1 \
    --ignorefile .trivyignore "$img"
done
