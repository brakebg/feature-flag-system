#!/usr/bin/env node
// Runs the quality gates of spec 11.3 and writes build/verify-report.{json,md}.
//
//   node scripts/verify.mjs verify      all gates, stop at the first failure (make verify)
//   node scripts/verify.mjs all         all gates, also after a failure    (make verify-all)
//   node scripts/verify.mjs fast        gates 1-6, 9, 10, 14, 15           (make verify-fast)
//   node scripts/verify.mjs only 5 14   only the listed gates (inner loop; not a gate result)
//
// A gate that is not yet active (scripts/current-milestone < activeFrom) passes trivially.
import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { gates } from './gates.mjs';
import { javaEnv } from './lib/java-env.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const BUILD = path.join(ROOT, 'build');
const LOGS = path.join(BUILD, 'logs');
mkdirSync(LOGS, { recursive: true });

const [mode = 'verify', ...rest] = process.argv.slice(2);
if (!['verify', 'all', 'fast', 'only'].includes(mode)) {
  console.error(`unknown mode ${mode}`);
  process.exit(2);
}
const onlyIds = mode === 'only' ? rest.map(Number) : null;
const milestone = Number(readFileSync(path.join(ROOT, 'scripts/current-milestone'), 'utf8').trim());
const full = mode === 'verify' || mode === 'all';
const env = { ...process.env, ...javaEnv() };

const selected = gates.filter((g) => {
  if (onlyIds) return onlyIds.includes(g.id);
  if (mode === 'fast') return g.fast;
  return true;
});

const hooks = {
  resetTagLog() {
    rmSync(path.join(ROOT, 'backend/target/junit-tags.jsonl'), { force: true });
  },
};

function run(cmd, cwd, logFile) {
  const started = Date.now();
  const res = spawnSync('bash', ['-o', 'pipefail', '-c', cmd], {
    cwd: path.join(ROOT, cwd ?? '.'),
    env,
    encoding: 'utf8',
    maxBuffer: 512 * 1024 * 1024,
  });
  const out = `${res.stdout ?? ''}${res.stderr ?? ''}`;
  writeFileSync(logFile, `$ (cd ${cwd ?? '.'} && ${cmd})\n${out}`);
  return { code: res.status ?? 1, out, ms: Date.now() - started };
}

function errorExcerpt(out) {
  const lines = out.split('\n');
  const idx = lines.findIndex((l) => /\b(ERROR|FAIL|FAILED|Error|error|✗|×)\b|BUILD FAILURE/.test(l));
  const from = idx >= 0 ? idx : Math.max(0, lines.length - 30);
  return lines.slice(from, from + 30).join('\n');
}

function listXml(p) {
  const abs = path.join(ROOT, p);
  if (!existsSync(abs)) return [];
  if (statSync(abs).isFile()) return [abs];
  return readdirSync(abs)
    .filter((f) => f.endsWith('.xml'))
    .map((f) => path.join(abs, f));
}

function failingTests(dirs) {
  const failed = [];
  for (const file of dirs.flatMap(listXml)) {
    const xml = readFileSync(file, 'utf8');
    const re = /<testcase\b([^>]*?)(\/>|>([\s\S]*?)<\/testcase>)/g;
    let m;
    while ((m = re.exec(xml))) {
      const body = m[3] ?? '';
      if (!/<(failure|error)\b/.test(body)) continue;
      const name = /\bname="([^"]*)"/.exec(m[1])?.[1] ?? '?';
      const cls = /\bclassname="([^"]*)"/.exec(m[1])?.[1] ?? '';
      failed.push(cls ? `${cls}.${name}` : name);
    }
  }
  return failed;
}

function idsOf(testNames) {
  const ids = new Set();
  for (const t of testNames) for (const id of t.match(/(AC|ERR)-[A-Za-z0-9{}/._-]+/g) ?? []) ids.add(id);
  const tagLog = path.join(ROOT, 'backend/target/junit-tags.jsonl');
  if (existsSync(tagLog)) {
    for (const line of readFileSync(tagLog, 'utf8').split('\n').filter(Boolean)) {
      const rec = JSON.parse(line);
      if (rec.status === 'SUCCESSFUL') continue;
      if (testNames.some((t) => t.includes(rec.method) && t.includes(rec.className.split('.').pop())))
        for (const tag of rec.tags) ids.add(tag);
    }
  }
  return [...ids].sort();
}

const results = [];
let stop = false;
for (const gate of selected) {
  const result = { id: gate.id, name: gate.name, status: 'pass', ms: 0, steps: [] };
  results.push(result);
  if (stop) {
    result.status = 'not-run';
    continue;
  }
  if (milestone < gate.activeFrom) {
    result.status = 'inactive';
    console.log(`gate ${gate.id} ${gate.name}: inactive until M${gate.activeFrom} (pass)`);
    continue;
  }
  for (const step of gate.steps) {
    const s = { name: step.name, status: 'pass', ms: 0 };
    result.steps.push(s);
    if (step.activeFrom && milestone < step.activeFrom) {
      s.status = 'inactive';
      continue;
    }
    if (step.fullOnly && !full) {
      s.status = 'skipped (full verify only)';
      continue;
    }
    if (step.tools) {
      const t = run(`bash scripts/install-tools.sh ${step.tools.join(' ')}`, '.', path.join(LOGS, `tools-${gate.id}.log`));
      if (t.code !== 0) {
        Object.assign(s, { status: 'fail', error: errorExcerpt(t.out) });
        result.status = 'fail';
        break;
      }
    }
    if (step.before) hooks[step.before]();
    process.stdout.write(`gate ${gate.id} ${gate.name} › ${step.name} ... `);
    const logFile = path.join(LOGS, `gate-${gate.id}-${step.name.replace(/[^a-z0-9]+/gi, '_')}.log`);
    const r = run(step.cmd, step.cwd, logFile);
    s.ms = r.ms;
    result.ms += r.ms;
    s.log = path.relative(ROOT, logFile);
    if (r.code !== 0) {
      s.status = 'fail';
      s.error = errorExcerpt(r.out);
      s.failingTests = failingTests(step.junit ?? []);
      s.acIds = idsOf(s.failingTests);
      result.status = 'fail';
      console.log(`FAIL (${(r.ms / 1000).toFixed(1)} s)`);
      break;
    }
    console.log(`ok (${(r.ms / 1000).toFixed(1)} s)`);
  }
  if (result.status === 'fail' && mode === 'verify') stop = true;
}

const ok = results.every((r) => r.status !== 'fail');
const report = {
  mode,
  milestone: `M${milestone}`,
  date: new Date().toISOString(),
  commit: spawnSync('git', ['rev-parse', '--short', 'HEAD'], { cwd: ROOT, encoding: 'utf8' }).stdout.trim(),
  dirty: spawnSync('git', ['status', '--porcelain'], { cwd: ROOT, encoding: 'utf8' }).stdout.trim() !== '',
  result: ok ? 'pass' : 'fail',
  gates: results,
};
writeFileSync(path.join(BUILD, 'verify-report.json'), JSON.stringify(report, null, 2));

const md = [];
md.push(`# Verify report`, '');
md.push(`Mode: \`${mode}\` · Milestone: M${milestone} · Commit: ${report.commit}${report.dirty ? ' (uncommitted changes)' : ''} · ${report.date}`, '');
md.push(`**Result: ${ok ? 'PASS' : 'FAIL'}**`, '');
md.push('| # | Gate | Status | Duration |', '| --- | --- | --- | --- |');
for (const r of results) md.push(`| ${r.id} | ${r.name} | ${r.status} | ${(r.ms / 1000).toFixed(1)} s |`);
for (const r of results.filter((x) => x.status === 'fail')) {
  md.push('', `## Gate ${r.id} ${r.name}: FAIL`, '');
  for (const s of r.steps.filter((x) => x.status === 'fail')) {
    md.push(`Step: ${s.name} (log: \`${s.log ?? '-'}\`)`, '');
    if (s.failingTests?.length) md.push('Failing tests:', ...s.failingTests.map((t) => `- ${t}`), '');
    if (s.acIds?.length) md.push(`Affected IDs: ${s.acIds.join(', ')}`, '');
    md.push('```', s.error ?? '', '```');
  }
}
// Gate 13 writes its measurements; the p95 is reported here, not blocking (decision 0007).
const perf = path.join(BUILD, 'reports', 'perf.md');
if (existsSync(perf) && results.some((r) => r.id === 13 && (r.status === 'pass' || r.status === 'fail'))) {
  md.push('', readFileSync(perf, 'utf8'));
}
const trace = path.join(BUILD, 'traceability.md');
if (existsSync(trace) && results.some((r) => r.id === 14 && r.status !== 'inactive' && r.status !== 'not-run')) {
  md.push('', readFileSync(trace, 'utf8'));
}
writeFileSync(path.join(BUILD, 'verify-report.md'), md.join('\n') + '\n');
console.log(`\n${ok ? 'PASS' : 'FAIL'} — build/verify-report.md`);
process.exit(ok ? 0 : 1);
