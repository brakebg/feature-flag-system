#!/usr/bin/env bash
# Gate 8 (spec 11.3): springdoc writes backend/openapi.json, openapi-typescript generates
# frontend/src/api/schema.d.ts; both must be committed and up to date, and the UI must
# compile against them.
set -euo pipefail
cd "$(dirname "$0")/.."
(cd backend && ./mvnw -B -ntp -q verify -Dit.test=OpenApiExportIT -Dtest=NoUnitTests \
  -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=true -Djacoco.skip=true)
(cd frontend && npx openapi-typescript ../backend/openapi.json -o src/api/schema.d.ts)
status=0
git diff --exit-code HEAD -- backend/openapi.json frontend/src/api/schema.d.ts || status=1
untracked=$(git ls-files --others --exclude-standard -- backend/openapi.json frontend/src/api/schema.d.ts)
if [ -n "$untracked" ]; then echo "not committed: $untracked"; status=1; fi
(cd frontend && npm run typecheck) || status=1
if [ "$status" != 0 ]; then echo "API contract out of date: commit backend/openapi.json and frontend/src/api/schema.d.ts"; fi
exit "$status"
