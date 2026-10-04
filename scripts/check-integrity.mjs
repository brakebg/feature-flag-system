#!/usr/bin/env node
// Gate 15 (spec 11.3): test integrity.
// Fails on: skipped / focused tests; eslint-disable or @SuppressWarnings without a
// "// reason:" comment; thresholds that differ from the spec; coverage, mutation, lint or
// ArchUnit exclusions not listed in docs/DECISIONS.md; gate activation that differs from
// the spec; a changed screenshot baseline not listed in docs/DECISIONS.md; an
// acceptance-criteria registry that is not a verbatim copy of spec 11.2.
import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { gates } from './gates.mjs';

const milestone = Number(readFileSync('scripts/current-milestone', 'utf8').trim());
const problems = [];
const read = (f) => (existsSync(f) ? readFileSync(f, 'utf8') : null);
const decisions = read('docs/DECISIONS.md') ?? '';

function walk(dir, filter, out = []) {
  if (!existsSync(dir)) return out;
  for (const name of readdirSync(dir)) {
    if (['node_modules', 'target', 'dist', 'coverage', 'test-results', 'playwright-report'].includes(name)) continue;
    const p = path.join(dir, name);
    if (statSync(p).isDirectory()) walk(p, filter, out);
    else if (filter(p)) out.push(p);
  }
  return out;
}

// 1. Skipped or focused tests.
const testFiles = [
  ...walk('backend/src/test', (p) => p.endsWith('.java')),
  ...walk('frontend/src', (p) => /\.test\.tsx?$/.test(p)),
  ...walk('frontend/e2e', (p) => /\.tsx?$/.test(p)),
];
const forbidden = [/@Disabled\b/, /assumeTrue\(\s*false\s*\)/, /\.skip\(/, /\.only\(/, /\bxit\(/, /\bxdescribe\(/, /test\.fixme/, /\.todo\(/];
for (const f of testFiles) {
  readFileSync(f, 'utf8').split('\n').forEach((line, i) => {
    for (const re of forbidden) if (re.test(line)) problems.push(`${f}:${i + 1}: forbidden ${re} in a test`);
  });
}

// 2. Suppressions need a "// reason:" comment on the same line.
const codeFiles = [
  ...walk('backend/src', (p) => p.endsWith('.java')),
  ...walk('frontend/src', (p) => /\.(tsx?|css)$/.test(p) && !p.endsWith('schema.d.ts')),
  ...walk('frontend/e2e', (p) => /\.tsx?$/.test(p)),
];
for (const f of codeFiles) {
  readFileSync(f, 'utf8').split('\n').forEach((line, i) => {
    if (/eslint-disable|@SuppressWarnings|@ts-ignore|@ts-expect-error|@ts-nocheck/.test(line) && !/reason:/.test(line))
      problems.push(`${f}:${i + 1}: suppression without a "// reason:" comment`);
  });
}

// 3. Thresholds equal the spec values (spec 11, 11.3, 11.6).
const pom = read('backend/pom.xml') ?? '';
const vitestCfg = read('frontend/vitest.config.ts') ?? '';
const pwCfg = read('frontend/playwright.config.ts');
const e2eScript = read('scripts/e2e.sh');
const k6 = read('perf/evaluate.js');
function expect(cond, msg) {
  if (!cond) problems.push(msg);
}
if (milestone >= 2) {
  const mins = [...pom.matchAll(/<minimum>([^<]*)<\/minimum>/g)].map((m) => m[1]);
  expect(mins.length > 0 && mins.every((v) => v === '0.80'), `backend/pom.xml: JaCoCo minimum must be 0.80 (found ${mins.join(', ') || 'none'})`);
  expect(/<counter>LINE<\/counter>/.test(pom) && /<value>COVEREDRATIO<\/value>/.test(pom), 'backend/pom.xml: JaCoCo rule must check LINE COVEREDRATIO');
}
if (milestone >= 4) {
  const t = [...pom.matchAll(/<mutationThreshold>([^<]*)<\/mutationThreshold>/g)].map((m) => m[1]);
  expect(t.length === 1 && t[0] === '60', `backend/pom.xml: PIT mutationThreshold must be 60 (found ${t.join(', ') || 'none'})`);
}
{
  const lines = [...vitestCfg.matchAll(/\blines:\s*([0-9.]+)/g)].map((m) => m[1]);
  expect(lines.length === 1 && lines[0] === '70', `frontend/vitest.config.ts: coverage lines threshold must be 70 (found ${lines.join(', ') || 'none'})`);
  expect(!/autoUpdate/.test(vitestCfg), 'frontend/vitest.config.ts: thresholds.autoUpdate must not be set');
}
if (pwCfg !== null || milestone >= 7) {
  const cfg = pwCfg ?? '';
  const retries = [...cfg.matchAll(/\bretries:\s*([^,\n}]+)/g)].map((m) => m[1].trim());
  expect(retries.length > 0 && retries.every((r) => r === '0'), `frontend/playwright.config.ts: retries must be 0 (found ${retries.join(', ') || 'none'})`);
  const ratio = [...cfg.matchAll(/maxDiffPixelRatio:\s*([0-9.]+)/g)].map((m) => m[1]);
  expect(ratio.length > 0 && ratio.every((r) => r === '0.01'), `frontend/playwright.config.ts: maxDiffPixelRatio must be 0.01 (found ${ratio.join(', ') || 'none'})`);
  expect(!/maxDiffPixels\b/.test(cfg) && !/threshold:/.test(cfg), 'frontend/playwright.config.ts: no other screenshot tolerance allowed');
}
if (e2eScript !== null || milestone >= 8) {
  expect(/--repeat-each[= ]2\b/.test(e2eScript ?? ''), 'scripts/e2e.sh: Playwright must run with --repeat-each=2');
  expect(!/--retries/.test(e2eScript ?? ''), 'scripts/e2e.sh: no --retries override allowed');
}
if (k6 !== null || milestone >= 8) {
  expect(/p\(95\)\s*<\s*50\b/.test(k6 ?? ''), 'perf/evaluate.js: threshold must be p(95)<50');
  expect(/rate:\s*200\b/.test(k6 ?? ''), 'perf/evaluate.js: rate must be 200');
  expect(/duration:\s*'60s'/.test(k6 ?? ''), "perf/evaluate.js: duration must be '60s'");
}

// 4. Exclusions must be listed in docs/DECISIONS.md.
const exclusions = [];
for (const m of pom.matchAll(/<(exclude|excludedClass|excludedMethod|excludedTestClass|avoidCallsTo)>([^<]*)<\/\1>/g)) {
  const inEnforcer = pom.lastIndexOf('<bannedDependencies>', m.index) > pom.lastIndexOf('</bannedDependencies>', m.index);
  if (!inEnforcer) exclusions.push({ where: 'backend/pom.xml', what: m[2] });
}
for (const m of vitestCfg.matchAll(/exclude:\s*\[([^\]]*)\]/g)) exclusions.push({ where: 'frontend/vitest.config.ts', what: m[1].trim() });
for (const f of walk('backend/src/test', (p) => p.endsWith('.java'))) {
  const src = readFileSync(f, 'utf8');
  if (/@ArchIgnore|\.because\(\s*"ignore|FreezingArchRule|freeze\(/.test(src)) exclusions.push({ where: f, what: 'ArchUnit ignore/freeze' });
}
const eslintCfg = read('frontend/eslint.config.js') ?? '';
const allowedIgnores = ['dist', 'coverage', 'playwright-report', 'test-results', 'src/api/schema.d.ts'];
for (const m of eslintCfg.matchAll(/ignores:\s*\[([^\]]*)\]/g)) {
  for (const item of m[1].split(',').map((s) => s.trim().replace(/['"]/g, '')).filter(Boolean)) {
    if (!allowedIgnores.includes(item)) exclusions.push({ where: 'frontend/eslint.config.js', what: item });
  }
}
for (const e of exclusions) {
  if (!decisions.includes(e.what)) problems.push(`${e.where}: exclusion "${e.what}" is not listed in docs/DECISIONS.md`);
}

// 5. Gate activation and verify-fast set equal spec 11.3.
const specActive = { 1: 1, 2: 1, 3: 2, 4: 1, 5: 2, 6: 2, 7: 4, 8: 4, 9: 6, 10: 1, 11: 5, 12: 8, 13: 8, 14: 1, 15: 1 };
const specFast = [1, 2, 3, 4, 5, 6, 9, 10, 14, 15];
expect(gates.length === 15 && gates.every((g, i) => g.id === i + 1), 'scripts/gates.mjs: must define gates 1-15 in order');
for (const g of gates) {
  expect(g.activeFrom === specActive[g.id], `scripts/gates.mjs: gate ${g.id} activeFrom M${g.activeFrom}, spec says M${specActive[g.id]}`);
  expect(!!g.fast === specFast.includes(g.id), `scripts/gates.mjs: gate ${g.id} fast=${!!g.fast}, spec says ${specFast.includes(g.id)}`);
}

// 6. Changed screenshot baselines must be listed in docs/DECISIONS.md (spec 11.6).
const base = spawnSync('git', ['merge-base', 'HEAD', 'origin/main'], { encoding: 'utf8' }).stdout.trim();
if (base) {
  const changed = new Set([
    ...spawnSync('git', ['diff', '--name-only', '--diff-filter=MD', base, '--', '*-snapshots/*.png'], { encoding: 'utf8' }).stdout.split('\n').filter(Boolean),
  ]);
  // A baseline that was only added (first version) is not a change.
  for (const f of changed) {
    if (!decisions.includes(path.basename(f))) problems.push(`${f}: changed screenshot baseline not listed in docs/DECISIONS.md`);
  }
}

// 7. Registry is a verbatim copy of spec 11.2; versions agree.
{
  const spec = readFileSync('docs/SPEC.md', 'utf8');
  const sec = spec.slice(spec.indexOf('### 11.2'), spec.indexOf('### 11.3'));
  const want = [...sec.matchAll(/^\s*[-*] \[[ x]\] (.+)$/gm)].map((m) => m[1].trim());
  const have = read('docs/acceptance-criteria.md') ?? '';
  const missing = want.filter((w) => !have.includes(w));
  expect(missing.length === 0, `docs/acceptance-criteria.md: ${missing.length} criteria differ from spec 11.2`);
  const version = readFileSync('VERSION', 'utf8').trim();
  const pomVersion = /<artifactId>feature-flag-service<\/artifactId>\s*(?:<!--[\s\S]*?-->\s*)?<version>([^<]+)<\/version>/.exec(pom)?.[1];
  const pkgVersion = JSON.parse(readFileSync('frontend/package.json', 'utf8')).version;
  expect(pomVersion === version, `backend/pom.xml version ${pomVersion} differs from VERSION ${version}`);
  expect(pkgVersion === version, `frontend/package.json version ${pkgVersion} differs from VERSION ${version}`);
}

if (problems.length) {
  console.error(problems.map((p) => `- ${p}`).join('\n'));
  console.error(`\ncheck-integrity: ${problems.length} violation(s)`);
  process.exit(1);
}
console.log(`check-integrity: ${testFiles.length} test files, ${codeFiles.length} code files, no violations`);
