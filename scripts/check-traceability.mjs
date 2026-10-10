#!/usr/bin/env node
// Gate 14 (spec 11.4): every acceptance criterion and every error case has at least one
// test, and all its tests pass; no test is tagged with an unknown ID.
//
// Inputs (written by the earlier gates):
//   backend/target/junit-tags.jsonl          JUnit 5 tags per test (TagRecordingListener)
//   build/reports/vitest-junit.xml           Vitest; IDs in the test title: [AC-FLAG-3]
//   build/reports/playwright.json            Playwright; IDs in the test title
//   build/reports/script-results/*.json      smoke / perf scripts: [{ title, status }]
// Output: build/traceability.md (ID -> tests -> pass/fail), included in the verify report.
import { existsSync, readFileSync, readdirSync, writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { acDue, acIds, errIds, errorTableRows, specAcs } from './lib/trace-registry.mjs';
import { isDue, milestoneState } from './lib/active-spec.mjs';

const state = milestoneState();
const milestone = state.n;
const ID_RE = /\b(?:AC|ERR)-[A-Za-z0-9{}/._-]*[A-Za-z0-9}]/g;

/** @type {{ source: string, name: string, ids: string[], passed: boolean }[]} */
const tests = [];

const tagLog = 'backend/target/junit-tags.jsonl';
if (existsSync(tagLog)) {
  for (const line of readFileSync(tagLog, 'utf8').split('\n').filter(Boolean)) {
    const r = JSON.parse(line);
    tests.push({
      source: 'junit',
      name: `${r.className.split('.').pop()}.${r.method}`,
      ids: r.tags.filter((t) => /^(AC|ERR)-/.test(t)),
      passed: r.status === 'SUCCESSFUL',
    });
  }
}

const vitest = 'build/reports/vitest-junit.xml';
if (existsSync(vitest)) {
  const xml = readFileSync(vitest, 'utf8');
  for (const m of xml.matchAll(/<testcase\b([^>]*?)(\/>|>([\s\S]*?)<\/testcase>)/g)) {
    const name = decode(/\bname="([^"]*)"/.exec(m[1])?.[1] ?? '');
    const cls = decode(/\bclassname="([^"]*)"/.exec(m[1])?.[1] ?? '');
    const body = m[3] ?? '';
    if (/<skipped/.test(body)) continue;
    tests.push({ source: 'vitest', name: `${cls} › ${name}`, ids: [...name.matchAll(ID_RE)].map((x) => x[0]), passed: !/<(failure|error)\b/.test(body) });
  }
}

const pw = 'build/reports/playwright.json';
if (existsSync(pw)) {
  const walk = (suite, prefix) => {
    for (const spec of suite.specs ?? []) {
      for (const t of spec.tests ?? []) {
        const passed = (t.results ?? []).length > 0 && t.results.every((r) => r.status === 'passed');
        const title = `${prefix}${spec.title}`;
        tests.push({ source: `playwright:${t.projectName}`, name: title, ids: [...title.matchAll(ID_RE)].map((x) => x[0]), passed });
      }
    }
    for (const s of suite.suites ?? []) walk(s, `${prefix}${s.title} › `);
  };
  for (const s of JSON.parse(readFileSync(pw, 'utf8')).suites ?? []) walk(s, '');
}

const scriptDir = 'build/reports/script-results';
if (existsSync(scriptDir)) {
  for (const f of readdirSync(scriptDir).filter((x) => x.endsWith('.json'))) {
    for (const r of JSON.parse(readFileSync(path.join(scriptDir, f), 'utf8'))) {
      tests.push({ source: f.replace('.json', ''), name: r.title, ids: [...r.title.matchAll(ID_RE)].map((x) => x[0]), passed: r.status === 'passed' });
    }
  }
}

function decode(s) {
  return s.replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
}

const registry = new Map();
for (const { id, removed } of acIds()) registry.set(id, { due: removed ? null : acDue[id], removed });
for (const [id, due] of Object.entries(errIds)) registry.set(id, { due, removed: false });
for (const { id, removed, due } of specAcs()) registry.set(id, { due: removed ? null : due, removed });

const problems = [];
const pending = [];
const unknown = new Map();
const byId = new Map([...registry.keys()].map((id) => [id, []]));
for (const t of tests) {
  for (const id of t.ids) {
    if (!registry.has(id)) unknown.set(id, [...(unknown.get(id) ?? []), t.name]);
    else byId.get(id).push(t);
  }
}
for (const [id, names] of unknown) problems.push(`unknown ID ${id} in: ${names.join('; ')}`);

const rows = [];
for (const [id, info] of registry) {
  const ts = byId.get(id);
  const failed = ts.filter((t) => !t.passed);
  let status;
  if (info.removed) status = 'removed (no test needed)';
  else if (failed.length) status = 'FAIL';
  else if (ts.length) status = 'pass';
  else if (info.due === undefined || isDue(info.due, state)) status = 'MISSING'; // unknown due: always missing
  else status = `pending (due M${info.due})`;
  if (status === 'FAIL') problems.push(`${id}: failing tests: ${failed.map((t) => t.name).join('; ')}`);
  if (status === 'MISSING') problems.push(`${id}: no test (due M${info.due})`);
  if (status.startsWith('pending')) pending.push(id);
  rows.push(`| ${id} | ${status} | ${ts.map((t) => `${t.passed ? '✓' : '✗'} ${t.source}: ${t.name}`).join('<br>') || '-'} |`);
}

const tableRows = errorTableRows.map((r) => {
  const ts = byId.get(r.id) ?? [];
  return `| ${r.row} | ${r.id} | ${ts.length ? (ts.every((t) => t.passed) ? 'pass' : 'FAIL') : 'no test yet'} |`;
});

const md = [
  '## Traceability (gate 14)',
  '',
  `Milestone M${milestone}. Tests read: ${tests.length}. Problems: ${problems.length}. Pending (not yet due): ${pending.length}.`,
  '',
  ...(problems.length ? ['Problems:', ...problems.map((p) => `- ${p}`), ''] : []),
  '| ID | Status | Tests |',
  '| --- | --- | --- |',
  ...rows,
  '',
  '### Spec 9.1 error rows',
  '',
  '| Row | Covered by | Status |',
  '| --- | --- | --- |',
  ...tableRows,
];
mkdirSync('build', { recursive: true });
writeFileSync('build/traceability.md', md.join('\n') + '\n');

if (problems.length) {
  console.error(problems.join('\n'));
  console.error(`\ntraceability: ${problems.length} problem(s), see build/traceability.md`);
  process.exit(1);
}
console.log(`traceability: ${registry.size} IDs, ${tests.length} tests read, ${pending.length} pending, no problems`);
