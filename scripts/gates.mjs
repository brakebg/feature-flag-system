// The 15 quality gates of spec 11.3, in order. Read by scripts/verify.mjs.
//
// activeFrom: the milestone from which the gate (or step) runs (spec 11.3 column
// "Active from"). Before that it passes trivially. Gate 15 checks these values against
// the spec. `fast: true` marks the gates of `make verify-fast` (1-6, 9, 10, 14, 15).
// A step with `fullOnly: true` runs only in `make verify` / `make verify-all`.

const mvn = './mvnw -B -ntp';

export const gates = [
  {
    id: 1,
    name: 'Format and lint',
    activeFrom: 1,
    fast: true,
    steps: [
      { name: 'spotless:check', cwd: 'backend', cmd: `${mvn} -q spotless:check` },
      { name: 'npm run lint', cwd: 'frontend', cmd: 'npm run lint' },
      { name: 'npm run format:check', cwd: 'frontend', cmd: 'npm run format:check' },
      {
        name: 'promtool check rules',
        cmd: 'build/tools/promtool check rules ops/prometheus/alerts.yml',
        tools: ['promtool'],
      },
    ],
  },
  {
    id: 2,
    name: 'Compile and types',
    activeFrom: 1,
    fast: true,
    steps: [
      { name: 'mvnw compile', cwd: 'backend', cmd: `${mvn} -q compile` },
      { name: 'npm run typecheck', cwd: 'frontend', cmd: 'npm run typecheck' },
    ],
  },
  {
    id: 3,
    name: 'Architecture rules',
    activeFrom: 2,
    fast: true,
    steps: [
      {
        name: 'ArchUnit ArchitectureTest',
        cwd: 'backend',
        cmd: `${mvn} test -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=true -Djacoco.skip=true`,
        junit: ['backend/target/surefire-reports'],
      },
    ],
  },
  {
    id: 4,
    name: 'Banned dependencies',
    activeFrom: 1,
    fast: true,
    steps: [
      { name: 'Maven Enforcer', cwd: 'backend', cmd: `${mvn} -q validate` },
      { name: 'check-npm-deps', cmd: 'node scripts/check-npm-deps.mjs' },
    ],
  },
  {
    id: 5,
    name: 'Backend tests',
    activeFrom: 2,
    fast: true,
    steps: [
      {
        name: 'mvnw verify',
        cwd: 'backend',
        cmd: `${mvn} verify`,
        before: 'resetTagLog',
        junit: ['backend/target/surefire-reports', 'backend/target/failsafe-reports'],
      },
    ],
  },
  {
    id: 6,
    name: 'Coverage',
    activeFrom: 2,
    fast: true,
    steps: [
      { name: 'JaCoCo check', cwd: 'backend', cmd: `${mvn} jacoco:check@coverage-check` },
      {
        name: 'Vitest coverage thresholds',
        cwd: 'frontend',
        cmd: 'npm test -- --coverage',
        activeFrom: 6,
      },
    ],
  },
  {
    id: 7,
    name: 'Mutation testing',
    activeFrom: 4,
    fast: false,
    steps: [
      {
        name: 'PIT',
        cwd: 'backend',
        cmd: `${mvn} test-compile org.pitest:pitest-maven:mutationCoverage`,
      },
    ],
  },
  {
    id: 8,
    name: 'API contract',
    activeFrom: 4,
    fast: false,
    steps: [{ name: 'check-api-contract', cmd: 'bash scripts/check-api-contract.sh' }],
  },
  {
    id: 9,
    name: 'Frontend tests',
    activeFrom: 6,
    fast: true,
    steps: [
      {
        name: 'npm test -- --coverage',
        cwd: 'frontend',
        cmd: 'npm test -- --coverage',
        junit: ['build/reports/vitest-junit.xml'],
      },
    ],
  },
  {
    id: 10,
    name: 'Secrets',
    activeFrom: 1,
    fast: true,
    steps: [
      {
        name: 'gitleaks',
        cmd: 'build/tools/gitleaks dir . --config .gitleaks.toml --no-banner --redact --exit-code 1',
        tools: ['gitleaks'],
      },
      {
        name: 'trivy fs (Maven, npm)',
        cmd:
          'build/tools/trivy fs --quiet --scanners vuln --severity HIGH,CRITICAL --exit-code 1 ' +
          '--ignorefile .trivyignore --skip-dirs frontend/node_modules --skip-dirs backend/target ' +
          '--skip-dirs build .',
        tools: ['trivy'],
      },
      {
        name: 'trivy images',
        cmd: 'bash scripts/scan-images.sh',
        tools: ['trivy'],
        fullOnly: true,
      },
    ],
  },
  {
    id: 11,
    name: 'Docker smoke test',
    activeFrom: 5,
    fast: false,
    steps: [{ name: 'smoke.sh', cmd: 'bash scripts/smoke.sh' }],
  },
  {
    id: 12,
    name: 'End-to-end',
    activeFrom: 8,
    fast: false,
    steps: [{ name: 'Playwright', cmd: 'bash scripts/e2e.sh' }],
  },
  {
    id: 13,
    name: 'Performance',
    activeFrom: 8,
    fast: false,
    steps: [{ name: 'k6', cmd: 'bash scripts/perf.sh', tools: ['k6'] }],
  },
  {
    id: 14,
    name: 'Traceability',
    activeFrom: 1,
    fast: true,
    steps: [{ name: 'check-traceability', cmd: 'node scripts/check-traceability.mjs' }],
  },
  {
    id: 15,
    name: 'Test integrity',
    activeFrom: 1,
    fast: true,
    steps: [{ name: 'check-integrity', cmd: 'node scripts/check-integrity.mjs' }],
  },
];
