#!/usr/bin/env node
// Self-test of gate 16 (scripts/check-test-changes.mjs), spec 004 AC-TC-1 and AC-TC-2.
// Runs in gate 14 before check-traceability and writes
// build/reports/script-results/check-test-changes.json ([{ title, status }]) for it.
//
// Each case builds a fixture repository in a temp folder: on main, a spec whose
// test-changes.md allows AC-X-2, a JUnit test tagged AC-X-1 (not allowed), one tagged AC-X-2
// (allowed), a Vitest test tagged AC-X-1 and docs/project-acceptance-criteria.md. The case then plants
// a change on a branch and runs the gate against main.
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const GATE = path.join(ROOT, 'scripts/check-test-changes.mjs');
const OUT = path.join(ROOT, 'build/reports/script-results/check-test-changes.json');

// The fixed plumbing clause, as in .claude/skills/write-spec/TEMPLATE.md (File 4).
const CLAUSE = `Imports, annotations, helper signatures, type renames inside an unchanged assertion.
No assertion method, expected value, @Tag or [AC-…] title may change in any other test.`;

const FOO = 'backend/src/test/java/demo/FooTest.java';
const BAR = 'backend/src/test/java/demo/BarTest.java';
const SUM = 'frontend/src/sum.test.ts';
const REG = 'docs/project-acceptance-criteria.md';
const TC = 'docs/specs/900-fixture/test-changes.md';

const fixture = {
  'docs/specs/900-fixture/spec.json': JSON.stringify({
    id: '900',
    title: 'Fixture',
    owner: 'owner',
    status: 'ready',
    branch: 'feature/fixture',
    milestones: [99],
  }),
  [TC]: `# Test changes — spec 900

## Allowed behaviour changes

AC-X-2
(every test tagged with one of these IDs may change freely)

## Plumbing allowed everywhere

${CLAUSE}
`,
  // Line numbers matter: the cases below name them.
  [FOO]: `package demo;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FooTest {
  private List<String> names = List.of("a");

  @Test
  @Tag("AC-X-1")
  void totalIsFive() {
    Order order = order(2, 3);
    assertThat(order.total()).isEqualTo(5);
  }

  @Deprecated
  private static Order order(int a, int b) {
    return new Order(a, b);
  }
}
`,
  [BAR]: `package demo;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BarTest {
  @Test
  @Tag("AC-X-2")
  void limitIsTen() {
    assertThat(Limits.max()).isEqualTo(10);
  }
}
`,
  [SUM]: `import { describe, expect, it } from 'vitest';
import { sum } from './sum';

describe('sum', () => {
  it('[AC-X-1] adds two numbers', () => {
    const total: number = sum(2, 3);
    expect(total).toBe(5);
  });
});
`,
  [REG]: `# Project acceptance criteria

- [ ] **AC-X-1** v1 · The total of 2 and 3 is 5. · spec 800 · due M98
- [ ] **AC-X-2** v1 · The limit is 10. · spec 800 · due M98
`,
};

function git(dir, ...args) {
  return execFileSync('git', ['-C', dir, '-c', 'user.name=fixture', '-c', 'user.email=fixture@example.com', '-c', 'commit.gpgsign=false', ...args], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  });
}

function repo() {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'ff-gate16-'));
  git(dir, 'init', '-q', '-b', 'main');
  for (const [f, text] of Object.entries(fixture)) {
    mkdirSync(path.dirname(path.join(dir, f)), { recursive: true });
    writeFileSync(path.join(dir, f), text);
  }
  git(dir, 'add', '-A');
  git(dir, 'commit', '-q', '-m', 'base');
  git(dir, 'switch', '-q', '-c', 'feature/fixture');
  return dir;
}

/** Replace text in a fixture file (must match exactly once). */
function edit(dir, file, from, to) {
  const p = path.join(dir, file);
  const text = readFileSync(p, 'utf8');
  if (text.split(from).length !== 2) throw new Error(`fixture: "${from}" not found once in ${file}`);
  writeFileSync(p, text.replace(from, to));
}

function gate(dir, extra = []) {
  const r = spawnSync('node', [GATE, '--root', dir, '--base', 'main', '--spec', '900', ...extra], { cwd: ROOT, encoding: 'utf8' });
  return { code: r.status, out: `${r.stdout}${r.stderr}` };
}

const cases = [
  // AC-TC-1: a planted change in a test not in the allowed list fails with the file and line.
  {
    title: '[AC-TC-1] changed expected value in an unlisted JUnit test fails with file and line',
    plant: (d) => edit(d, FOO, 'isEqualTo(5)', 'isEqualTo(6)'),
    fails: [`${FOO}:15`],
  },
  {
    title: '[AC-TC-1] changed assertion method in an unlisted JUnit test fails with file and line',
    plant: (d) => edit(d, FOO, '.isEqualTo(5)', '.isNotEqualTo(5)'),
    fails: [`${FOO}:15`],
  },
  {
    title: '[AC-TC-1] changed @Tag in an unlisted JUnit test fails with file and line',
    plant: (d) => edit(d, FOO, '@Tag("AC-X-1")', '@Tag("AC-X-3")'),
    fails: [`${FOO}:12`],
  },
  {
    title: '[AC-TC-1] changed expected value in an unlisted Vitest test fails with file and line',
    plant: (d) => edit(d, SUM, 'toBe(5)', 'toBe(6)'),
    fails: [`${SUM}:7`],
  },
  {
    title: '[AC-TC-1] changed [ID] title in an unlisted Vitest test fails with file and line',
    plant: (d) => edit(d, SUM, '[AC-X-1] adds', '[AC-X-3] adds'),
    fails: [`${SUM}:5`],
  },
  {
    title: '[AC-TC-1] removed assertion line in an unlisted JUnit test fails with file and line',
    plant: (d) => edit(d, FOO, '    assertThat(order.total()).isEqualTo(5);\n', ''),
    fails: [`${FOO}:15`],
  },
  {
    title: '[AC-TC-1] an allowed tag added on the branch does not free an unlisted test',
    plant: (d) => {
      edit(d, FOO, '  @Tag("AC-X-1")\n', '  @Tag("AC-X-1")\n  @Tag("AC-X-2")\n');
      edit(d, FOO, 'isEqualTo(5)', 'isEqualTo(6)');
    },
    fails: [`${FOO}:16`],
  },
  {
    title: '[AC-TC-1] committed change checked with --head fails with file and line',
    plant: (d) => {
      edit(d, FOO, 'isEqualTo(5)', 'isEqualTo(6)');
      git(d, 'commit', '-q', '-am', 'planted');
    },
    args: ['--head', 'HEAD'],
    fails: [`${FOO}:15`],
  },
  {
    title: '[AC-TC-1] deleted unlisted test file fails with the file',
    plant: (d) => rmSync(path.join(d, FOO)),
    fails: [`${FOO}: test file deleted`],
  },
  {
    title: '[AC-TC-1] changed registry line of an unlisted AC ID fails with file and line',
    plant: (d) => edit(d, REG, 'is 5.', 'is 6.'),
    fails: [`${REG}:3`],
  },
  {
    title: '[AC-TC-1] added registry line of an AC ID that is not the spec\'s own fails',
    plant: (d) => edit(d, REG, 'The limit is 10. · spec 800 · due M98\n', 'The limit is 10. · spec 800 · due M98\n- [ ] **AC-Y-1** v1 · Something else. · spec 800 · due M98\n'),
    fails: [`${REG}:5`],
  },
  {
    title: '[AC-TC-1] added note line without the spec\'s own new criterion fails',
    plant: (d) => edit(d, REG, '# Project acceptance criteria\n', '# Project acceptance criteria\n\nA new note.\n'),
    fails: [`${REG}:3`],
  },
  {
    title: 'a test-changes.md with a changed plumbing clause fails',
    plant: (d) => edit(d, TC, 'helper signatures, ', ''),
    fails: [`${TC}: the plumbing clause differs`],
  },
  // AC-TC-2: plumbing in an unlisted test passes; a listed test may change its expected value.
  {
    title: '[AC-TC-2] import, annotation, signature and type-only changes in an unlisted JUnit test pass',
    plant: (d) => {
      edit(d, FOO, 'import java.util.List;', 'import java.util.Collection;\nimport java.util.List;');
      edit(d, FOO, 'private List<String> names', 'private Collection<String> names');
      edit(d, FOO, '  @Deprecated\n', '');
      edit(d, FOO, 'order(int a, int b)', 'order(long a, long b)');
      edit(d, FOO, 'Order order = order(2, 3);', 'Purchase order = order(2, 3);');
      edit(d, FOO, 'assertThat(order.total())', 'assertThat(order.sum())');
    },
    passes: true,
  },
  {
    title: '[AC-TC-2] import and type-only changes in an unlisted Vitest test pass',
    plant: (d) => {
      edit(d, SUM, "import { sum } from './sum';", "import { sum, type Total } from './sum';");
      edit(d, SUM, 'const total: number =', 'const total: Total =');
    },
    passes: true,
  },
  {
    title: '[AC-TC-2] changed expected value in a listed test passes',
    plant: (d) => edit(d, BAR, 'isEqualTo(10)', 'isEqualTo(20)'),
    passes: true,
  },
  {
    title: '[AC-TC-2] changed registry line of a listed AC ID passes',
    plant: (d) => edit(d, REG, 'limit is 10.', 'limit is 20.'),
    passes: true,
  },
  {
    title: '[AC-TC-2] the spec PR appending its own registry section passes',
    plant: (d) => edit(d, REG, 'spec 800 · due M98\n- [ ] **AC-X-2** v1 · The limit is 10. · spec 800 · due M98\n', 'spec 800 · due M98\n- [ ] **AC-X-2** v1 · The limit is 10. · spec 800 · due M98\n\n## Fixture\n\n- [ ] **AC-Z-1** v1 · Fixture one. · spec 900 · due M99\n'),
    passes: true,
  },
];

const results = [];
for (const c of cases) {
  const dir = repo();
  let ok;
  let detail = '';
  try {
    c.plant(dir);
    const r = gate(dir, c.args);
    if (c.passes) ok = r.code === 0;
    else ok = r.code === 1 && c.fails.every((f) => r.out.includes(`FAIL ${f}`));
    if (!ok) detail = `exit ${r.code}\n${r.out}`;
  } catch (e) {
    ok = false;
    detail = e.stack;
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
  results.push({ title: c.title, status: ok ? 'passed' : 'failed' });
  console.log(`${ok ? 'passed' : 'FAILED'}: ${c.title}`);
  if (!ok) console.log(detail.replace(/^/gm, '    '));
}

mkdirSync(path.dirname(OUT), { recursive: true });
writeFileSync(OUT, JSON.stringify(results, null, 2) + '\n');
const failed = results.filter((r) => r.status !== 'passed').length;
console.log(`\ncheck-test-changes self-test: ${results.length - failed}/${results.length} passed`);
process.exit(failed ? 1 : 0);
