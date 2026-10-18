#!/usr/bin/env node
// Helper for the /write-spec skill (owner only). Node, ESM, no dependencies.
//
// Usage (from anywhere inside the repo):
//   node .claude/skills/write-spec/spec-tool.mjs next [--no-fetch]
//   node .claude/skills/write-spec/spec-tool.mjs tests [--md] <ID ...>
//   node .claude/skills/write-spec/spec-tool.mjs check <spec-folder> [--allow-open] [--base <ref>]
//   node .claude/skills/write-spec/spec-tool.mjs list
//
// next   Next free spec id, milestone and decision number. Scans the working tree, origin/main
//        and every other branch (local and origin), so numbers taken by an open spec PR are skipped.
// tests  Every test that carries the IDs in a string: JUnit @Tag("ID"), a Vitest / Playwright /
//        script test title (same ID pattern as gate 14). --md prints the list for test-changes.md.
//        Warns on an ID with no test.
// check  Validates a spec folder on its branch against the template, the criteria file and the
//        scope rules of docs/process/spec-flow.md (compared with the merge base of --base).
//        Exit 0 = pass. Exit 1 = problems (listed). Exit 2 = only open owner questions left
//        (pass --allow-open for a draft). Exit 3 = usage error (all subcommands).
// list   The table of all specs, read from their spec.json files (docs/specs/README.md has no
//        index table: spec.json is the only place for a spec's facts).
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const ROOT = repoRoot();
const { REGISTRY, acIds, errIds, parseRegistry } = await import(pathToFileURL(path.join(ROOT, 'scripts/lib/trace-registry.mjs')).href);

/** The plumbing clause of test-changes.md. Identical in every spec (issue #21). */
export const PLUMBING = [
  'Imports, annotations, helper signatures, type renames inside an unchanged assertion.',
  'No assertion method, expected value, @Tag or [AC-…] title may change in any other test.',
].join('\n');

/** The "##" headings of SPEC.md, in this order (TEMPLATE.md). The header block comes first. */
export const SECTIONS = [
  'Relation to the current docs',
  'Goal',
  'MUST stay the same',
  'Design',
  'Acceptance criteria',
  'Test changes',
  'Acceptance delta',
  'Process',
  'Definition of done',
  'Owner decisions',
];

const AC_RE = /\bAC-[A-Z]+-\d+\b/g;
/** Same ID pattern as gate 14 (scripts/check-traceability.mjs, ID_RE). */
const ID_RE = /\b(?:AC|ERR)-[A-Za-z0-9{}/._-]*[A-Za-z0-9}]/g;
/** Allowed outside code spans in a filled spec (builder placeholders). */
const ALLOWED_PLACEHOLDERS = new Set(['<spec>', '<n>', '<area>', '<what>']);
const USAGE = 3;

// ------------------------------------------------------------------------------- helpers

function repoRoot() {
  try {
    return execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  } catch {
    return process.cwd();
  }
}

// core.quotepath=false: file names with non-ASCII characters ("1 · Sign in.html") come back as written.
function git(args) {
  try {
    return execFileSync('git', ['-c', 'core.quotepath=false', ...args], { cwd: ROOT, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'], maxBuffer: 64 << 20 });
  } catch {
    return null;
  }
}

const read = (rel) => readFileSync(path.join(ROOT, rel), 'utf8');
const exists = (rel) => existsSync(path.join(ROOT, rel));
const lines = (s) => (s ?? '').split('\n').filter(Boolean);
const uniq = (a) => [...new Set(a)];

/** Expand "AC-HA-1 … AC-HA-10" / "AC-HA-1 to AC-HA-10" / "AC-HA-1..AC-HA-10", then list all IDs. */
export function idsIn(text) {
  const expanded = text.replace(/\b(AC-[A-Z]+)-(\d+)\s*(?:…|\.\.\.?|to)\s*\1-(\d+)\b/g, (_, p, a, b) => {
    const out = [];
    for (let i = Number(a); i <= Number(b); i++) out.push(`${p}-${i}`);
    return out.join(', ');
  });
  return uniq(expanded.match(AC_RE) ?? []);
}

/** ERR-<METHOD>-<path>-<status> IDs in a text (gate 14 pattern). */
const errsIn = (text) => uniq((text.match(ID_RE) ?? []).filter((x) => x.startsWith('ERR-')));

/** Text without fenced code blocks and inline code spans. */
function prose(text) {
  let fence = false;
  return text
    .split('\n')
    .map((l) => {
      if (/^\s*(```|~~~)/.test(l)) {
        fence = !fence;
        return '';
      }
      return fence ? '' : l.replace(/`[^`]*`/g, '');
    })
    .join('\n');
}

/** Specs in the working tree: { name, id, branch, milestones, acceptance, meta }. Old folders are fine. */
function localSpecs() {
  const base = path.join(ROOT, 'docs/specs');
  if (!existsSync(base)) return [];
  return readdirSync(base, { withFileTypes: true })
    .filter((d) => d.isDirectory() && /^\d{3}-/.test(d.name))
    .map((d) => {
      let meta = null;
      try {
        meta = JSON.parse(read(`docs/specs/${d.name}/spec.json`));
      } catch {
        /* folder without a readable spec.json: counted by its name only */
      }
      return { name: d.name, id: d.name.slice(0, 3), branch: meta?.branch, milestones: meta?.milestones ?? [], acceptance: meta?.acceptance, meta, ref: 'working tree' };
    });
}

// Decision records by kind (docs/README.md); numbers are global across these folders.
const DECISION_DIRS = ['decisions', 'docs/architecture/decisions', 'docs/process/decisions'];
const isDecision = (f) => /^\d{4}-.*\.md$/.test(f);

function localDecisions() {
  const dirs = [...DECISION_DIRS, ...localSpecs().map((s) => `docs/specs/${s.name}/history`)];
  return dirs
    .filter((d) => exists(d))
    .flatMap((d) => readdirSync(path.join(ROOT, d)).filter(isDecision).map((f) => ({ n: f.slice(0, 4), file: f, dir: d, ref: 'working tree' })));
}

/** origin/main (if present) first, then every other branch, local and origin. */
function scanRefs() {
  const main = git(['rev-parse', '--verify', '--quiet', 'refs/remotes/origin/main']) ? ['origin/main'] : [];
  const others = lines(git(['for-each-ref', '--format=%(refname:short)', 'refs/heads', 'refs/remotes/origin']))
    .filter((r) => r !== 'origin/main' && r !== 'origin' && !r.endsWith('/HEAD'));
  return [...main, ...others];
}

function specsAt(ref) {
  return lines(git(['ls-tree', `${ref}:docs/specs`]))
    .map((l) => /^\d+ tree \S+\t(.+)$/.exec(l)?.[1])
    .filter((n) => n && /^\d{3}-/.test(n))
    .map((name) => {
      let meta = null;
      try {
        meta = JSON.parse(git(['show', `${ref}:docs/specs/${name}/spec.json`]) ?? 'null');
      } catch {
        /* unreadable spec.json on that branch */
      }
      return { name, id: name.slice(0, 3), branch: meta?.branch, milestones: meta?.milestones ?? [], acceptance: meta?.acceptance, ref };
    });
}

function decisionsAt(ref) {
  const dirs = [...DECISION_DIRS, ...specsAt(ref).map((s) => `docs/specs/${s.name}/history`)];
  return dirs.flatMap((d) =>
    lines(git(['ls-tree', '--name-only', `${ref}:${d}`]))
      .map((f) => f.split('/').pop())
      .filter(isDecision)
      .map((f) => ({ n: f.slice(0, 4), file: f, dir: d, ref })),
  );
}

// ---------------------------------------------------------------------------------- next

function cmdNext(args) {
  if (!args.includes('--no-fetch')) {
    if (git(['fetch', '--quiet', 'origin']) === null) console.error('warning: git fetch failed; using local refs only');
  }
  const refs = scanRefs();
  const specs = [...localSpecs(), ...refs.flatMap(specsAt)];
  const decisions = [...localDecisions(), ...refs.flatMap(decisionsAt)];
  const maxId = Math.max(0, ...specs.map((s) => Number(s.id)));
  const maxM = Math.max(0, ...specs.flatMap((s) => s.milestones.map(Number)));
  const maxD = Math.max(0, ...decisions.map((d) => Number(d.n)));
  // Numbers above the working tree's highest are held by another branch; older ones are history.
  const local = localSpecs();
  const topId = Math.max(0, ...local.map((s) => Number(s.id)));
  const topM = Math.max(0, ...local.flatMap((s) => s.milestones.map(Number)));
  const topD = Math.max(0, ...localDecisions().map((d) => Number(d.n)));
  console.log(`next spec:      ${String(maxId + 1).padStart(3, '0')}`);
  console.log(`next milestone: M${maxM + 1}`);
  console.log(`next decision:  ${String(maxD + 1).padStart(4, '0')}`);
  console.log(`scanned: working tree + ${refs.filter((r) => r === 'origin/main').length ? 'origin/main + ' : ''}${refs.filter((r) => r !== 'origin/main').length} other branch(es)`);
  const elsewhere = uniq([
    ...specs.filter((s) => s.ref !== 'working tree' && (Number(s.id) > topId || s.milestones.some((m) => m > topM))).map((s) => `spec ${s.name} (M${s.milestones.join(', M')}) on ${s.ref}`),
    ...decisions.filter((d) => d.ref !== 'working tree' && Number(d.n) > topD).map((d) => `decision ${d.file} on ${d.ref}`),
  ]);
  if (elsewhere.length) {
    console.log('taken on other refs only (not in this working tree):');
    for (const e of elsewhere) console.log(`  ${e}`);
  }
}

// --------------------------------------------------------------------------------- tests

/** True when `id` appears as a whole gate-14 ID inside a string literal of the line (tag or title). */
function carriesId(line, id) {
  for (const lit of line.match(/'(?:[^'\\]|\\.)*'|"(?:[^"\\]|\\.)*"|`(?:[^`\\]|\\.)*`/g) ?? []) {
    if ((lit.match(ID_RE) ?? []).includes(id)) return true;
  }
  return false;
}

export function findTests(id) {
  const out = git(['grep', '-n', '-F', id, '--', '.', ':!docs', ':!decisions', ':!.claude', ':!*.md', ':!scripts/lib/trace-registry.mjs', ':!scripts/check-traceability.mjs']);
  return lines(out)
    .map((l) => /^([^:]+):(\d+):(.*)$/.exec(l))
    .filter((m) => m && carriesId(m[3], id))
    .map((m) => {
      let text = m[3].trim();
      if (text.startsWith('@Tag')) {
        // JUnit: the declaration after the tag is a test method or a whole class
        const after = read(m[1]).split('\n').slice(Number(m[2]));
        const decl = after.find((x) => x.trim() && !x.trim().startsWith('@')) ?? '';
        const cls = /\b(?:class|record|interface)\s+(\w+)/.exec(decl)?.[1];
        const fn = /\b(\w+)\s*\(/.exec(decl)?.[1];
        text += cls ? ` whole class ${cls}` : fn ? ` ${fn}` : '';
      }
      return { file: m[1], line: Number(m[2]), text };
    });
}

function cmdList() {
  const rows = ['| # | Folder | Title | Status | Milestones | Branch | Issue | Release |', '| --- | --- | --- | --- | --- | --- | --- | --- |'];
  for (const s of localSpecs()) {
    const m = s.meta ?? {};
    rows.push(`| ${s.id} | ${s.name} | ${m.title ?? ''} | ${m.status ?? ''} | ${s.milestones.map((n) => `M${n}`).join(', ')} | ${m.branch ?? ''} | ${m.issue ? `#${m.issue}` : ''} | ${m.release ?? ''} |`);
  }
  console.log(rows.join('\n'));
}

function cmdTests(args) {
  const md = args.includes('--md');
  const ids = args.filter((a) => !a.startsWith('--'));
  if (!ids.length) {
    console.error('usage: spec-tool.mjs tests [--md] <ID ...>');
    process.exit(USAGE);
  }
  for (const id of ids) {
    const found = findTests(id);
    if (!found.length) console.error(`warning: ${id}: no test is tagged with this ID`);
    if (md) {
      if (found.length) console.log(`- ${id}: ${found.map((t) => `\`${t.file}:${t.line}\``).join(', ')}`);
      else console.log(`- ${id}: no tagged test yet`);
    } else {
      for (const t of found) console.log(`${id}\t${t.file}:${t.line}\t${t.text.slice(0, 100)}`);
    }
  }
}

// --------------------------------------------------------------------------------- check

/** "##" sections of a markdown file: [{ n, title, body }]. */
function sections(text) {
  const out = [];
  let cur = null;
  let fence = false;
  for (const line of text.split('\n')) {
    if (/^\s*(```|~~~)/.test(line)) fence = !fence;
    const m = !fence && /^## (?:(\d+)\.\s+)?(.+?)\s*$/.exec(line);
    if (m) {
      cur = { n: m[1] ? Number(m[1]) : null, title: m[2], body: '' };
      out.push(cur);
    } else if (cur) cur.body += `${line}\n`;
  }
  return out;
}


function cmdCheck(args) {
  const allowOpen = args.includes('--allow-open');
  let base = 'origin/main';
  const positional = [];
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--base') base = args[++i];
    else if (!args[i].startsWith('--')) positional.push(args[i]);
  }
  const target = positional[0];
  if (!target) {
    console.error('usage: spec-tool.mjs check <docs/specs/NNN-name> [--allow-open] [--base <ref>]');
    process.exit(USAGE);
  }
  const folder = path.basename(path.resolve(ROOT, target));
  const dir = `docs/specs/${folder}`;
  const errors = [];
  const warnings = [];
  const err = (m) => errors.push(m);
  const warn = (m) => warnings.push(m);
  let open = [];

  // 1. Folder and spec.json (docs/specs/README.md, folder layout)
  const fm = /^(\d{3})-([a-z0-9]+(?:-[a-z0-9]+)*)$/.exec(folder);
  if (!fm) err(`${dir}: folder name must be NNN-<kebab-case>`);
  if (!exists(dir)) {
    console.error(`${dir}: no such folder`);
    process.exit(USAGE);
  }
  let meta = {};
  try {
    meta = JSON.parse(read(`${dir}/spec.json`));
  } catch (e) {
    err(`${dir}/spec.json: missing or not JSON (${e.message})`);
  }
  for (const k of ['id', 'title', 'owner', 'status', 'milestones', 'branch']) if (meta[k] === undefined) err(`spec.json: missing "${k}"`);
  for (const k of ['issue', 'release']) if (!(k in meta)) err(`spec.json: missing "${k}" (null when not known)`);
  for (const k of ['spec', 'acceptance']) if (k in meta) err(`spec.json: "${k}" is no longer used (SPEC.md is always in the folder; criteria are in ${REGISTRY})`);
  if (fm && meta.id !== fm[1]) err(`spec.json: id "${meta.id}" does not match the folder number ${fm[1]}`);
  if (!['draft', 'ready', 'done'].includes(meta.status)) err(`spec.json: "status" must be draft, ready or done (found ${JSON.stringify(meta.status)})`);
  const milestones = Array.isArray(meta.milestones) ? meta.milestones : [];
  if (!milestones.length || !milestones.every((m) => Number.isInteger(m) && m > 0)) err('spec.json: "milestones" must be a non-empty list of positive integers');
  if (typeof meta.branch !== 'string' || !/^feature\/[a-z0-9]+(?:-[a-z0-9]+)*$/.test(meta.branch)) err(`spec.json: branch must be feature/<kebab-case> (found ${JSON.stringify(meta.branch)})`);
  if (meta.issue !== null && meta.issue !== undefined && !Number.isInteger(meta.issue)) err('spec.json: "issue" must be a number or null');
  if ('scope' in meta && !(Array.isArray(meta.scope) && meta.scope.every((x) => typeof x === 'string' && x))) err('spec.json: "scope" must be a list of path globs');
  if (!exists(`${dir}/SPEC.md`)) err(`${dir}/SPEC.md does not exist`);
  if (!exists(`${dir}/test-changes.md`)) err(`${dir}/test-changes.md does not exist`);
  if (exists(`${dir}/acceptance-criteria.md`)) err(`${dir}/acceptance-criteria.md: a spec has no criteria file of its own; its criteria go into ${REGISTRY}`);
  for (const f of ['builder', 'STATE.md', 'PROGRESS.md', 'DECISIONS.md', 'BLOCKERS.md', 'MILESTONE', 'escalations', 'reviews', 'verify-report.md']) if (exists(`${dir}/${f}`)) err(`${dir}/${f}: builder memory; the builder creates it in its first commit, never the spec`);

  // 2. Unique across all specs (working tree and all branches)
  const others = [...localSpecs(), ...scanRefs().flatMap(specsAt)].filter((s) => s.name !== folder);
  for (const s of others) {
    if (s.id === meta.id) err(`spec id ${meta.id} is also used by ${s.name} (${s.ref})`);
    if (s.branch && s.branch === meta.branch) err(`branch ${meta.branch} is also used by ${s.name} (${s.ref})`);
    for (const m of milestones) if (s.milestones.includes(m)) err(`milestone M${m} is also used by ${s.name} (${s.ref})`);
  }

  // 3. Criteria (docs/process/traceability.md): compare the list here with the branch base.
  const baseOk = git(['rev-parse', '--verify', '--quiet', base]) !== null;
  if (!baseOk) warn(`criteria and scope not checked against ${base}: ref not found (use --base <ref>)`);
  const mergeBase = baseOk ? (git(['merge-base', base, 'HEAD']) ?? '').trim() : '';
  const now = exists(REGISTRY) ? parseRegistry(read(REGISTRY)) : { acs: [], bad: [] };
  for (const b of now.bad) err(`${REGISTRY}:${b.n}: line does not parse: ${b.line.slice(0, 80)}`);
  const was = new Map((mergeBase ? parseRegistry(git(['show', `${mergeBase}:${REGISTRY}`]) ?? '').acs : []).map((a) => [a.id, a]));
  const seen = new Set();
  for (const a of now.acs) {
    if (seen.has(a.id)) err(`${REGISTRY}:${a.n}: ${a.id} is listed twice`);
    seen.add(a.id);
  }
  const newIds = [];
  const changedIds = [];
  const retiredIds = [];
  for (const a of now.acs) {
    const old = was.get(a.id);
    const differs = !old || old.text !== a.text || old.version !== a.version || old.retiredBy !== a.retiredBy || old.due !== a.due || old.changedBy !== a.changedBy;
    if (!differs) continue;
    if (!old) {
      if (a.spec !== meta.id) err(`${REGISTRY}:${a.n}: new ${a.id} must name "spec ${meta.id}"`);
      else if (a.version !== 1 || a.changedBy) err(`${REGISTRY}:${a.n}: new ${a.id} starts at v1 without "changed by"`);
      else if (!milestones.includes(a.due)) err(`${REGISTRY}:${a.n}: ${a.id} due M${a.due} is not a milestone of this spec (${milestones.join(', ')})`);
      newIds.push(a.id);
    } else if (a.removed && !old.removed) {
      if (a.retiredBy !== `spec ${meta.id}`) err(`${REGISTRY}:${a.n}: retired ${a.id} must say "retired by spec ${meta.id}"`);
      retiredIds.push(a.id);
    } else if (baseOk && old.spec === meta.id && !old.changedBy && a.spec === meta.id && !a.changedBy) {
      newIds.push(a.id); // this spec's own new criterion, edited again on its branch
    } else {
      if (a.changedBy !== meta.id) err(`${REGISTRY}:${a.n}: changed ${a.id} must say "changed by spec ${meta.id}"`);
      if (a.version !== old.version + 1) err(`${REGISTRY}:${a.n}: changed ${a.id} must be v${old.version + 1} (was v${old.version})`);
      if (!milestones.includes(a.due)) err(`${REGISTRY}:${a.n}: ${a.id} due M${a.due} is not a milestone of this spec`);
      changedIds.push(a.id);
    }
  }
  for (const id of was.keys()) if (!seen.has(id)) err(`${REGISTRY}: ${id} was removed; retire it instead ("· retired by spec ${meta.id}")`);

  // 4. SPEC.md: header and sections
  let sec = {};
  const specFile = `${dir}/SPEC.md`;
  if (exists(specFile)) {
    const text = read(specFile);
    const head = text.split(/^## /m)[0];
    if (!new RegExp(`^# Spec ${meta.id} — \\S`).test(head)) err(`SPEC.md: first line must be "# Spec ${meta.id} — <title>"`);
    if (!/Owner-authored, locked\./.test(head)) err('SPEC.md header: missing "Owner-authored, locked."');
    if (/Decision: `/.test(head)) warn('SPEC.md header: names a decision record; a spec keeps its decisions in section 10');

    const all = sections(text);
    const titles = all.map((s) => s.title);
    SECTIONS.forEach((want, i) => {
      const got = all[i];
      if (!got || !got.title.startsWith(want) || got.n !== i + 1) err(`SPEC.md: section ${i + 1} must be "## ${i + 1}. ${want}" (found: ${got ? `"## ${got.n ?? ''}${got.n ? '. ' : ''}${got.title}"` : 'nothing'})`);
    });
    if (all.length > SECTIONS.length) err(`SPEC.md: extra "##" sections after section ${SECTIONS.length}: ${titles.slice(SECTIONS.length).join(', ')}`);
    SECTIONS.forEach((want, i) => {
      if (all[i]?.title.startsWith(want)) sec[want] = all[i].body;
    });

    // 4a. Section 1 names every product and architecture file this branch changes
    if (sec[SECTIONS[0]] !== undefined && baseOk) {
      const docs = lines(git(['diff', '--name-only', `${mergeBase}`, '--', 'docs/product', 'docs/architecture'])).concat(lines(git(['ls-files', '--others', '--exclude-standard', 'docs/product', 'docs/architecture'])));
      for (const f of uniq(docs)) if (!sec[SECTIONS[0]].includes(f)) err(`SPEC.md section 1: ${f} changes on this branch but is not named`);
    }
    if (sec.Goal !== undefined && !/Not in scope/.test(sec.Goal)) err('SPEC.md section 2: missing "Not in scope"');

    // 4c. Section 5 names exactly the criteria this spec adds, changes or retires
    const s5 = sec['Acceptance criteria'];
    const mine = [...newIds, ...changedIds, ...retiredIds];
    if (s5 !== undefined) {
      const named = idsIn(s5);
      for (const id of mine) if (!named.includes(id)) err(`SPEC.md section 5: ${id} (changed in ${REGISTRY}) is not named`);
      for (const id of named) if (!mine.includes(id) && !seen.has(id)) err(`SPEC.md section 5: ${id} does not exist in ${REGISTRY}`);
      const noChange = /^No behaviour change\.\s*$/m.test(s5);
      if (!mine.length && !noChange) err('SPEC.md section 5: no new, changed or retired criterion; every behaviour change needs one (or write the line "No behaviour change.")');
      if (noChange && mine.length) err('SPEC.md section 5: says "No behaviour change." but criteria change');
    }

    // 4d. Section 7: acceptance delta equals the criteria diff
    const s7 = sec['Acceptance delta'];
    const delta = {};
    if (s7 !== undefined) {
      for (const k of ['Added', 'Changed', 'Retired']) {
        const l = new RegExp(`^- ${k}:(.*)$`, 'm').exec(s7);
        if (!l) err(`SPEC.md section 7: missing line "- ${k}: ..." (write "none" if empty)`);
        delta[k] = l ? idsIn(l[1]) : [];
      }
      const same = (a, b) => a.length === b.length && a.every((x) => b.includes(x));
      for (const [k, ids] of [['Added', newIds], ['Changed', changedIds], ['Retired', retiredIds]]) {
        if (!same(delta[k], ids)) err(`SPEC.md section 7: ${k} (${delta[k].join(', ') || 'none'}) must equal the ${k.toLowerCase()} criteria in ${REGISTRY} (${ids.join(', ') || 'none'})`);
      }
    }

    // 4e. test-changes.md and section 6
    if (exists(`${dir}/test-changes.md`)) {
      const tc = read(`${dir}/test-changes.md`);
      const ts = sections(tc);
      const ia = ts.findIndex((s) => s.title === 'Allowed behaviour changes');
      const ip = ts.findIndex((s) => s.title === 'Plumbing allowed everywhere');
      if (ia < 0) err('test-changes.md: missing "## Allowed behaviour changes"');
      if (ip < 0) err('test-changes.md: missing "## Plumbing allowed everywhere"');
      if (ia >= 0 && ip >= 0 && ip < ia) err('test-changes.md: "Allowed behaviour changes" must come before "Plumbing allowed everywhere"');
      if (ip >= 0 && ts[ip].body.trim() !== PLUMBING) err('test-changes.md: the plumbing clause must be exactly the text in TEMPLATE.md');
      const body = ia >= 0 ? ts[ia].body : '';
      const allowedErr = errsIn(body);
      const allowed = [...idsIn(body), ...allowedErr];
      if (ia >= 0 && !allowed.length && !/^none\b/im.test(body.trim())) err('test-changes.md: list the allowed AC / ERR IDs or write "none"');
      for (const id of allowed) {
        if (allowedErr.includes(id)) {
          err(`test-changes.md: ${id}: gate 16 frees tests only through a changed or retired criterion (spec 006); change the criterion the error case belongs to and list that`);
        } else if (!changedIds.includes(id) && !retiredIds.includes(id)) {
          err(`test-changes.md: ${id} may change but this spec does not change or retire it`);
        }
        if (!findTests(id).length) warn(`test-changes.md: ${id} has no tagged test in the repo today`);
      }
      for (const id of [...changedIds, ...retiredIds]) if (!allowed.includes(id) && findTests(id).length) warn(`${id} changes, so gate 16 lets its tests change; list it in test-changes.md`);
      const s6 = sec['Test changes'];
      if (s6 !== undefined) {
        if (!s6.includes('test-changes.md')) err('SPEC.md section 6: must point to `<spec>/test-changes.md`');
        const ids6 = [...idsIn(s6), ...errsIn(s6)];
        for (const id of allowed) if (!ids6.includes(id)) err(`SPEC.md section 6: allowed ID ${id} (test-changes.md) is not named`);
        for (const id of ids6) if (!allowed.includes(id)) err(`SPEC.md section 6: ${id} is named but not in test-changes.md`);
      }
    }

    // 4f. Section 10: the spec's decision log
    const s10 = sec['Owner decisions'];
    if (s10 !== undefined) {
      const items = s10.split('\n').filter((l) => /^\d+\.\s/.test(l));
      if (!items.length) warn('SPEC.md section 10: no owner decision listed');
      open = items.filter((l) => /\*\*Open\b/.test(l));
      for (const l of items) if (!/\*\*(Open|Decided)\b/.test(l)) err(`SPEC.md section 10: item without "**Decided:" or "**Open:": ${l.slice(0, 70)}`);
    }
    if (open.length && meta.status !== 'draft') err(`spec.json: status "${meta.status}" while section 10 has Open items (use draft)`);

    // 4g. Leftover template text: comments and placeholders outside code
    for (const f of [specFile, `${dir}/test-changes.md`].filter((f) => exists(f))) {
      const p = prose(read(f));
      if (/<!--/.test(p)) err(`${f}: template comment "<!--" left`);
      for (const ph of uniq(p.match(/<[A-Za-z][^>]*>/g) ?? [])) if (!ALLOWED_PLACEHOLDERS.has(ph)) err(`${f}: placeholder ${ph} left`);
    }
  }

  // 5. Scope: the spec's commits change only spec files (never code, never a builder file)
  if (baseOk) {
    const changed = uniq([...lines(git(['diff', '--name-only', `${mergeBase}`])), ...lines(git(['ls-files', '--others', '--exclude-standard']))]);
    const ok = (f) => (f.startsWith(`${dir}/`) && !f.startsWith(`${dir}/builder/`)) || f === REGISTRY || f.startsWith('docs/product/') || f.startsWith('docs/architecture/');
    for (const f of changed.filter((f) => !ok(f))) err(`scope: ${f} changed; a spec changes only its folder, ${REGISTRY}, docs/product/ and docs/architecture/ (docs/process/spec-flow.md)`);
  }

  // Report
  for (const w of warnings) console.log(`WARN  ${w}`);
  for (const e of errors) console.log(`FAIL  ${e}`);
  for (const o of open) console.log(`OPEN  ${o.trim().slice(0, 100)}`);
  if (errors.length) {
    console.log(`\n${dir}: ${errors.length} problem(s).`);
    process.exit(1);
  }
  if (open.length && !allowOpen) {
    console.log(`\n${dir}: structure OK, ${open.length} open owner question(s) in section 10. Not ready.`);
    process.exit(2);
  }
  console.log(`\n${dir}: OK${open.length ? ` (draft, ${open.length} open question(s))` : ''}. New: ${newIds.join(', ') || 'none'}; changed: ${changedIds.join(', ') || 'none'}; retired: ${retiredIds.join(', ') || 'none'}.`);
}

// ---------------------------------------------------------------------------------- main

// Only when run as a command; other scripts may import PLUMBING and idsIn.
if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  const [cmd, ...rest] = process.argv.slice(2);
  if (cmd === 'next') cmdNext(rest);
  else if (cmd === 'tests') cmdTests(rest);
  else if (cmd === 'check') cmdCheck(rest);
  else if (cmd === 'list') cmdList();
  else {
    console.error(readFileSync(new URL(import.meta.url), 'utf8').split('\n').slice(1, 20).join('\n'));
    process.exit(USAGE);
  }
}
