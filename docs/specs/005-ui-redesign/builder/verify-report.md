# Verify report — spec 005, M12

Mode: `all` (`make verify-all`) · Commit: 16f86ae (release 1.2.0, MILESTONE `12 complete`) ·
2026-10-20 · **Result: PASS**

Source: CI job `verify` of the PR run 39435735722 (job 118672126843,
https://github.com/brakebg/feature-flag-system/actions/runs/39435735722), artifact
`verify-reports` 12271762218. The cloud container cannot build the Docker images or download the
artifact (D-004, D-012), so these are the gate lines of the CI log, copied without change.

| # | Gate | Step | Status | Duration |
| --- | --- | --- | --- | --- |
| 1 | Format and lint | spotless:check | ok | 12.6 s |
| 1 | Format and lint | npm run lint | ok | 3.9 s |
| 1 | Format and lint | npm run format:check | ok | 2.2 s |
| 1 | Format and lint | promtool check rules | ok | 0.0 s |
| 2 | Compile and types | mvnw compile | ok | 20.6 s |
| 2 | Compile and types | npm run typecheck | ok | 4.2 s |
| 3 | Architecture rules | ArchUnit ArchitectureTest | ok | 12.0 s |
| 4 | Banned dependencies | Maven Enforcer | ok | 2.3 s |
| 4 | Banned dependencies | check-npm-deps | ok | 0.0 s |
| 5 | Backend tests | mvnw verify | ok | 80.5 s |
| 6 | Coverage | JaCoCo check | ok | 1.8 s |
| 6 | Coverage | Vitest coverage thresholds | ok | 17.1 s |
| 7 | Mutation testing | PIT | ok | 100.8 s |
| 8 | API contract | check-api-contract | ok | 21.1 s |
| 9 | Frontend tests | npm test -- --coverage | ok | 16.3 s |
| 10 | Secrets | gitleaks | ok | 1.0 s |
| 10 | Secrets | trivy fs (Maven, npm) | ok | 5.4 s |
| 10 | Secrets | trivy images | ok | 111.3 s |
| 11 | Docker smoke test | smoke.sh | ok | 14.2 s |
| 12 | End-to-end | Playwright (`--repeat-each=2`) | ok | 211.3 s |
| 13 | Performance | k6 | ok | 89.9 s |
| 14 | Traceability | check-test-changes self-test | ok | 1.6 s |
| 14 | Traceability | check-traceability (M12 complete: AC-UI-1 to 9, AC-STYLE-1 due) | ok | 0.1 s |
| 15 | Test integrity | check-integrity | ok | 0.1 s |
| 16 | Test changes | check-test-changes | ok | 0.2 s |

The second CI run on the same commit (push event, job 118672094705) also passed.
