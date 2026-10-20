#!/usr/bin/env node
// Self-test of `spec-tool.mjs check` (.claude/skills/write-spec): a new or changed criteria line
// may name sections of docs/product/ only (issues #57, #58). Unchanged lines are not checked.
// Run: node scripts/test/spec-tool.test.mjs
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { cpSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const TOOL = path.join(ROOT, '.claude/skills/write-spec/spec-tool.mjs');
const { foreignSections, headingNumbers } = await import(TOOL);

const UI = '# Admin UI behaviour\n\n## 8. Frontend UI\n\n### 8.4 Flags workspace\n\n### 8.6 Audit log page\n';
const sectionsOf = (f) => (f === 'docs/product/ui.md' ? headingNumbers(UI) : null);
const check = (text) => foreignSections(text, sectionsOf);

test('headingNumbers reads the numbered headings', () => {
  assert.deepEqual([...headingNumbers(UI)], ['8', '8.4', '8.6']);
});

test('fail: a section outside docs/product/', () => {
  assert.equal(check('Every UI response carries the security headers listed in 10.2.').length, 1);
  assert.equal(check('Under the 9.2 load test the cache hit rate metric is ≥ 99 %.').length, 1);
  assert.equal(check('The columns follow spec 001 section 9.5.').length, 1);
  assert.equal(check('The column rules of spec 001 sections 8.4 and 8.6 hold.').length, 2);
  assert.equal(check('A no-op write keeps the ETag (7.2).').length, 1);
  assert.equal(check('See § 3 of the spec.').length, 1);
  assert.match(check('listed in 10.2')[0], /^names section 10\.2 outside docs\/product\//);
});

test('pass: a section of a docs/product/ file, or no section at all', () => {
  assert.deepEqual(check('The columns follow `docs/product/ui.md` 8.4.'), []);
  assert.deepEqual(check('The column rules of `docs/product/ui.md` sections 8.4 and 8.6 hold.'), []);
  assert.deepEqual(check('The rules of docs/product/ui.md section 8 hold.'), []);
  assert.deepEqual(check('Given `admin` / `admin123`, when I sign in, then I land on `/groups`.'), []);
});

test('pass: values that are not section numbers', () => {
  assert.deepEqual(check('Spring Boot 4.1.x, Spring Framework 7.0.9 or later and Jackson 3.x.'), []);
  assert.deepEqual(check('The p95 latency is below 1.5 s and the hit rate ≥ 99.5 %.'), []);
  assert.deepEqual(check('The response says `HTTP/1.1` and the version is `3.1`.'), []);
  assert.deepEqual(check('A weight of 0.5 is kept.'), []);
  assert.deepEqual(check('Test AC-UI-1 and ERR-GET-admin-groups-401 stay.'), []);
});

test('fail: a docs/product/ section that does not exist', () => {
  assert.match(check('The columns follow `docs/product/ui.md` 8.9.')[0], /section 8\.9 of docs\/product\/ui\.md, which has no such heading/);
  assert.match(check('The rules of `docs/product/nope.md` 1.1 hold.')[0], /docs\/product\/nope\.md, which is not a markdown file/);
});

// End to end: the check reports a new bad line and ignores an old bad line.
const REG = 'docs/project-acceptance-criteria.md';
const line = (id, text, due = 'due M1') => `- [ ] **${id}** v1 · ${text} · spec ${id === 'AC-X-1' ? '001' : '099'} · ${due}`;

function run(newText) {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'spec-tool-'));
  try {
    const git = (...a) => execFileSync('git', a, { cwd: dir, stdio: 'ignore' });
    const put = (rel, text) => {
      mkdirSync(path.dirname(path.join(dir, rel)), { recursive: true });
      writeFileSync(path.join(dir, rel), text);
    };
    git('init', '-q', '-b', 'main');
    git('config', 'user.email', 't@example.com');
    git('config', 'user.name', 't');
    cpSync(path.join(ROOT, 'scripts/lib'), path.join(dir, 'scripts/lib'), { recursive: true });
    put('docs/product/ui.md', UI);
    put(REG, `# Criteria\n\n${line('AC-X-1', 'Every UI response carries the security headers listed in 10.2.')}\n`);
    git('add', '-A');
    git('commit', '-q', '-m', 'main');
    git('checkout', '-q', '-b', 'feature/demo');
    put(REG, `# Criteria\n\n${line('AC-X-1', 'Every UI response carries the security headers listed in 10.2.')}\n${line('AC-X-2', newText, 'due M99')}\n`);
    put('docs/specs/099-demo/spec.json', JSON.stringify({ id: '099', title: 'Demo', owner: 'o', status: 'draft', milestones: [99], branch: 'feature/demo', issue: null, release: null }));
    const r = spawnSync('node', [TOOL, 'check', 'docs/specs/099-demo', '--base', 'main'], { cwd: dir, encoding: 'utf8' });
    return r.stdout.split('\n').filter((l) => /names /.test(l));
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

test('check fails a new line that names a section outside docs/product/, not an old one', () => {
  const found = run('Under the 9.2 load test the cache hit rate metric is ≥ 99 %.');
  assert.equal(found.length, 1);
  assert.match(found[0], /^FAIL {2}docs\/project-acceptance-criteria\.md:4: AC-X-2 names section 9\.2 outside docs\/product\//);
});

test('check passes a new line that names a docs/product/ section', () => {
  assert.deepEqual(run('The columns follow `docs/product/ui.md` 8.4.'), []);
});
