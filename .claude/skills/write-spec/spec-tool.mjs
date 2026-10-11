#!/usr/bin/env node
// Helper for the /write-spec skill (owner only). Node, ESM, no dependencies.
//
// Usage (from anywhere inside the repo):
//   node .claude/skills/write-spec/spec-tool.mjs next [--no-fetch]
//   node .claude/skills/write-spec/spec-tool.mjs tests [--md] <ID ...>
//   node .claude/skills/write-spec/spec-tool.mjs check <spec-folder> [--allow-open] [--base <ref>]
//
// next   Next free spec id, milestone and decision number. Scans the working tree, origin/main
//        and every owner/* branch (local and origin), so numbers taken by an open spec PR are skipped.
// tests  Every test that carries the IDs in a string: JUnit @Tag("ID"), a Vitest / Playwright /
//        script test title (same ID pattern as gate 14). --md prints the list for test-changes.md.
//        Warns on an ID with no test.
// check  Validates a new spec folder against the template and the index files.
//        Exit 0 = pass. Exit 1 = problems (listed). Exit 2 = only open owner questions left
//        (pass --allow-open for a draft). Exit 3 = usage error (all subcommands).
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const ROOT = repoRoot();
const { acIds, errIds } = await import(pathToFileURL(path.join(ROOT, 'scripts/lib/trace-registry.mjs')).href);

/** The plumbing clause of test-changes.md. Identical in every spec (issue #21). */
export const PLUMBING = [
  'Imports, annotations, helper signatures, type renames inside an unchanged assertion.',
  'No assertion method, expected value, @Tag or [AC-…] title may change in any other test.',
].join('\n');

/** The "##" headings of SPEC.md, in this order (TEMPLATE.md). The header block comes first. */
export const SECTIONS = [
  'Relation to `docs/SPEC.md`',
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

function git(args) {
  try {
    return execFileSync('git', args, { cwd: ROOT, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'], maxBuffer: 64 << 20 });
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

/** AC lines of an acceptance-criteria.md text (same regex as acIds in trace-registry.mjs). */
function parseAcs(text) {
  return [...text.matchAll(/^- \[[ x]\] \*\*(AC-[A-Z]+-\d+)\*\* · (.*)$/gm)].map((m) => ({ id: m[1], removed: m[2].startsWith('[Removed') }));
}

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
      return { name: d.name, id: d.name.slice(0, 3), branch: meta?.branch, milestones: meta?.milestones ?? [], acceptance: meta?.acceptance, ref: 'working tree' };
    });
}

function localDecisions() {
  return readdirSync(path.join(ROOT, 'decisions'))
    .filter((f) => /^\d{4}-.*\.md$/.test(f))
    .map((f) => ({ n: f.slice(0, 4), file: f, ref: 'working tree' }));
}

/** origin/main (if present) and every owner/* branch, local and origin. */
function scanRefs() {
  const main = git(['rev-parse', '--verify', '--quiet', 'refs/remotes/origin/main']) ? ['origin/main'] : [];
  return [...main, ...lines(git(['for-each-ref', '--format=%(refname:short)', 'refs/heads/owner', 'refs/remotes/origin/owner']))];
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
  return lines(git(['ls-tree', '--name-only', `${ref}:decisions`]))
    .filter((f) => /^\d{4}-.*\.md$/.test(f))
    .map((f) => ({ n: f.slice(0, 4), file: f, ref }));
}

/** IDs known before this spec: docs/acceptance-criteria.md and every other spec's file. */
function knownAcs(exceptFolder) {
  const known = new Map();
  for (const a of acIds(path.join(ROOT, 'docs/acceptance-criteria.md'))) known.set(a.id, { ...a, source: 'docs/acceptance-criteria.md' });
  for (const s of localSpecs()) {
    if (s.name === exceptFolder || !s.acceptance || s.acceptance === 'docs/acceptance-criteria.md' || !exists(s.acceptance)) continue;
    for (const a of acIds(path.join(ROOT, s.acceptance))) if (!known.has(a.id)) known.set(a.id, { ...a, source: s.acceptance });
  }
  for (const s of scanRefs().flatMap(specsAt)) {
    if (s.name === exceptFolder || !s.acceptance || s.acceptance === 'docs/acceptance-criteria.md') continue;
    const text = git(['show', `${s.ref}:${s.acceptance}`]);
    if (text) for (const a of parseAcs(text)) if (!known.has(a.id)) known.set(a.id, { ...a, source: `${s.acceptance} on ${s.ref}` });
  }
  return known;
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
  // Numbers above the working tree's highest are held by an open owner branch; older ones are history.
  const local = localSpecs();
  const topId = Math.max(0, ...local.map((s) => Number(s.id)));
  const topM = Math.max(0, ...local.flatMap((s) => s.milestones.map(Number)));
  const topD = Math.max(0, ...localDecisions().map((d) => Number(d.n)));
  console.log(`next spec:      ${String(maxId + 1).padStart(3, '0')}`);
  console.log(`next milestone: M${maxM + 1}`);
  console.log(`next decision:  ${String(maxD + 1).padStart(4, '0')}`);
  console.log(`scanned: working tree + ${refs.filter((r) => r === 'origin/main').length ? 'origin/main + ' : ''}${refs.filter((r) => r !== 'origin/main').length} owner/* branch(es)`);
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

/** The markdown table (contiguous "|" lines) that starts with the header line matching `head`. */
function tableRows(text, head) {
  const ls = text.split('\n');
  const start = ls.findIndex((l) => head.test(l));
  if (start < 0) return null;
  const rows = [];
  for (let i = start + 2; i < ls.length && ls[i].startsWith('|'); i++) rows.push(ls[i]);
  return rows;
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

  // 1. Folder and spec.json
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
  for (const k of ['id', 'title', 'spec', 'acceptance', 'branch', 'milestones']) if (meta[k] === undefined) err(`spec.json: missing "${k}"`);
  if (fm && meta.id !== fm[1]) err(`spec.json: id "${meta.id}" does not match the folder number ${fm[1]}`);
  if (meta.spec !== `${dir}/SPEC.md`) err(`spec.json: "spec" must be "${dir}/SPEC.md"`);
  if (meta.acceptance !== `${dir}/acceptance-criteria.md`) err(`spec.json: "acceptance" must be "${dir}/acceptance-criteria.md"`);
  for (const k of ['spec', 'acceptance']) if (meta[k] && !exists(meta[k])) err(`spec.json: ${k} file ${meta[k]} does not exist`);
  if (!exists(`${dir}/test-changes.md`)) err(`${dir}/test-changes.md does not exist`);
  const milestones = Array.isArray(meta.milestones) ? meta.milestones : [];
  if (!milestones.length || !milestones.every((m) => Number.isInteger(m) && m > 0)) err('spec.json: "milestones" must be a non-empty list of positive integers');
  if (typeof meta.branch === 'string' && (/^(main|owner\/|wip\/)/.test(meta.branch))) err(`spec.json: branch "${meta.branch}" must be a builder branch (not main, owner/*, wip/*)`);
  const agentFiles = ['STATE.md', 'PROGRESS.md', 'DECISIONS.md', 'BLOCKERS.md', 'MILESTONE', 'escalations', 'reviews', 'verify-report.md'];
  for (const f of agentFiles) if (exists(`${dir}/${f}`)) err(`${dir}/${f}: builder memory file; a new spec must not create it`);

  // 2. Unique across all specs (working tree and owner/* branches)
  const others = [...localSpecs(), ...scanRefs().flatMap(specsAt)].filter((s) => s.name !== folder);
  for (const s of others) {
    if (s.id === meta.id) err(`spec id ${meta.id} is also used by ${s.name} (${s.ref})`);
    if (s.branch && s.branch === meta.branch) err(`branch ${meta.branch} is also used by ${s.name} (${s.ref})`);
    for (const m of milestones) if (s.milestones.includes(m)) err(`milestone M${m} is also used by ${s.name} (${s.ref})`);
  }

  // 3. acceptance-criteria.md
  const specAcIds = [];
  if (meta.acceptance && exists(meta.acceptance)) {
    const text = read(meta.acceptance);
    const due = Number(/^Due milestone: (\d+)\s*$/m.exec(text)?.[1]);
    if (!due) err(`${meta.acceptance}: missing "Due milestone: <n>"`);
    else if (!milestones.includes(due)) err(`${meta.acceptance}: Due milestone ${due} is not a milestone of this spec (${milestones.join(', ')})`);
    for (const l of text.split('\n')) {
      if (/^\s*- \[/.test(l) && !/^- \[[ x]\] \*\*(AC-[A-Z]+-\d+)\*\* · (.*)$/.test(l)) err(`${meta.acceptance}: line does not parse as an AC line: ${l.slice(0, 80)}`);
    }
    specAcIds.push(...acIds(path.join(ROOT, meta.acceptance)).map((a) => a.id));
    if (uniq(specAcIds).length !== specAcIds.length) err(`${meta.acceptance}: an AC ID is listed twice`);
    if (!specAcIds.length) warn(`${meta.acceptance}: no AC IDs (a spec with no new criterion?)`);
  }
  const known = knownAcs(folder);
  for (const id of specAcIds) if (known.has(id)) err(`${id}: new ID of this spec already exists in ${known.get(id).source}`);

  // 4. SPEC.md: header and sections
  let sec = {};
  let reExpressed = [];
  let dec = null;
  let s10 = '';
  if (meta.spec && exists(meta.spec)) {
    const text = read(meta.spec);
    const head = text.split(/^## /m)[0];
    if (!new RegExp(`^# Spec ${meta.id} — \\S`).test(head)) err(`SPEC.md: first line must be "# Spec ${meta.id} — <title>"`);
    if (!/Owner-authored, locked\./.test(head)) err('SPEC.md header: missing "Owner-authored, locked."');
    dec = /Decision: `decisions\/(\d{4})-([a-z0-9-]+)\.md`/.exec(head);
    if (!dec) err('SPEC.md header: missing "Decision: `decisions/NNNN-<name>.md`"');
    for (const m of milestones) if (!head.includes(`**M${m}**`)) err(`SPEC.md header: milestone **M${m}** not named`);
    if (meta.branch && !head.includes(`**\`${meta.branch}\`**`)) err(`SPEC.md header: working branch **\`${meta.branch}\`** not named`);

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

    // 4a. Section 1 names the owner follow-up
    if (sec[SECTIONS[0]] !== undefined && !/owner updates `docs\/SPEC\.md`/.test(sec[SECTIONS[0]])) err('SPEC.md section 1: missing "the owner updates `docs/SPEC.md`" after merge');
    // 4b. Goal has "Not in scope"
    if (sec.Goal !== undefined && !/Not in scope/.test(sec.Goal)) err('SPEC.md section 2: missing "Not in scope"');

    // 4c. Section 5: every new ID named; re-expressed IDs exist
    const s5 = sec['Acceptance criteria'];
    if (s5 !== undefined) {
      const named = idsIn(s5);
      for (const id of specAcIds) if (!named.includes(id)) err(`SPEC.md section 5: ${id} (from acceptance-criteria.md) is not named`);
      reExpressed = uniq(s5.split('\n').map((l) => /^\|\s*(AC-[A-Z]+-\d+)\s*\|/.exec(l)?.[1]).filter(Boolean));
      for (const id of reExpressed) {
        if (specAcIds.includes(id)) err(`SPEC.md section 5: ${id} is a new ID; the re-expressed table holds existing IDs only`);
        else if (!known.has(id)) err(`SPEC.md section 5: re-expressed ${id} does not exist in docs/acceptance-criteria.md (a re-expressed AC keeps its ID and position)`);
        else if (known.get(id).removed) err(`SPEC.md section 5: re-expressed ${id} is a removed ID`);
      }
      for (const id of named) if (!specAcIds.includes(id) && !known.has(id)) err(`SPEC.md section 5: ${id} is neither new (acceptance-criteria.md) nor existing`);
      const noChange = /^No behaviour change\.\s*$/m.test(s5);
      if (!specAcIds.length && !reExpressed.length && !noChange) err('SPEC.md section 5: no new and no re-expressed AC ID; every behaviour change needs one (or write the line "No behaviour change.")');
      if (noChange && (specAcIds.length || reExpressed.length)) err('SPEC.md section 5: says "No behaviour change." but names new or re-expressed IDs');
    }

    // 4d. Section 7: acceptance delta
    const s7 = sec['Acceptance delta'];
    const delta = {};
    if (s7 !== undefined) {
      for (const k of ['Added', 'Changed', 'Retired']) {
        const l = new RegExp(`^- ${k}:(.*)$`, 'm').exec(s7);
        if (!l) err(`SPEC.md section 7: missing line "- ${k}: ..." (write "none" if empty)`);
        delta[k] = l ? idsIn(l[1]) : [];
      }
      const same = (a, b) => a.length === b.length && a.every((x) => b.includes(x));
      if (!same(delta.Added, specAcIds)) err(`SPEC.md section 7: Added (${delta.Added.join(', ') || 'none'}) must equal the IDs of acceptance-criteria.md (${specAcIds.join(', ') || 'none'})`);
      if (!same(delta.Changed, reExpressed)) err(`SPEC.md section 7: Changed (${delta.Changed.join(', ') || 'none'}) must equal the re-expressed IDs of section 5 (${reExpressed.join(', ') || 'none'})`);
      for (const id of delta.Retired) if (!known.has(id)) err(`SPEC.md section 7: retired ${id} does not exist`);
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
          if (!(id in errIds)) err(`test-changes.md: ${id} is not a known ERR ID (scripts/lib/trace-registry.mjs errIds)`);
        } else {
          if (!known.has(id) && !specAcIds.includes(id)) err(`test-changes.md: ${id} is not a known AC ID`);
          if (delta.Changed && !delta.Changed.includes(id) && !(delta.Retired ?? []).includes(id)) err(`test-changes.md: ${id} may change but is not listed as Changed (or Retired) in the acceptance delta`);
        }
        if (!findTests(id).length) warn(`test-changes.md: ${id} has no tagged test in the repo today`);
      }
      for (const k of ['Changed', 'Retired']) for (const id of delta[k] ?? []) if (!allowed.includes(id)) warn(`acceptance delta: ${id} is ${k} but its tests are not allowed to change (test-changes.md); fine only if no test asserts the old behaviour`);
      const s6 = sec['Test changes'];
      if (s6 !== undefined) {
        if (!s6.includes('test-changes.md')) err('SPEC.md section 6: must point to `<spec>/test-changes.md`');
        const ids6 = [...idsIn(s6), ...errsIn(s6)];
        for (const id of allowed) if (!ids6.includes(id)) err(`SPEC.md section 6: allowed ID ${id} (test-changes.md) is not named`);
        for (const id of ids6) if (!allowed.includes(id)) err(`SPEC.md section 6: ${id} is named but not in test-changes.md`);
      }
    }

    // 4f. Section 10: open owner questions
    s10 = sec['Owner decisions'];
    if (s10 !== undefined) {
      const items = s10.split('\n').filter((l) => /^\d+\.\s/.test(l));
      if (!items.length) warn('SPEC.md section 10: no owner decision listed');
      open = items.filter((l) => /\*\*Open\b/.test(l));
      for (const l of items) if (!/\*\*(Open|Decided)\b/.test(l)) err(`SPEC.md section 10: item without "**Decided:" or "**Open:": ${l.slice(0, 70)}`);
    }

    if (open.length && /Accepted by the owner on/.test(s10)) err('SPEC.md section 10: "Accepted by the owner on" while items are Open');

    // 4g. Leftover template text: comments and placeholders outside code
    const files = [meta.spec, meta.acceptance, `${dir}/test-changes.md`, dec && `decisions/${dec[1]}-${dec[2]}.md`].filter((f) => f && exists(f));
    for (const f of files) {
      const raw = read(f);
      const p = prose(raw);
      if (/<!--/.test(p)) err(`${f}: template comment "<!--" left`);
      for (const ph of uniq(p.match(/<[A-Za-z][^>]*>/g) ?? [])) if (!ALLOWED_PLACEHOLDERS.has(ph)) err(`${f}: placeholder ${ph} left`);
    }

    // 5. Decision file and both index rows
    if (dec) {
      const dfile = `decisions/${dec[1]}-${dec[2]}.md`;
      if (!exists(dfile)) err(`${dfile} does not exist`);
      else if (!new RegExp(`^# ${dec[1]} · `).test(read(dfile))) err(`${dfile}: first line must be "# ${dec[1]} · <title>"`);
      else if (open.length && /^Status: \*\*Accepted\*\*/m.test(read(dfile))) err(`${dfile}: Status is Accepted while section 10 has Open items`);
      for (const d of [...localDecisions(), ...scanRefs().flatMap(decisionsAt)]) {
        if (d.n === dec[1] && d.file !== `${dec[1]}-${dec[2]}.md`) err(`decision number ${dec[1]} is also used by ${d.file} (${d.ref})`);
      }
      const rows = tableRows(read('decisions/README.md'), /^\| # \| Decision \| Status \|/);
      if (!rows) err('decisions/README.md: decision table not found');
      else {
        const row = rows.find((r) => r.startsWith(`| ${dec[1]} |`) && r.includes(`(${dec[1]}-${dec[2]}.md)`));
        if (!row) err(`decisions/README.md: no row "| ${dec[1]} | [...](${dec[1]}-${dec[2]}.md) | ... |" inside the table`);
        else if (open.length && /\|\s*Accepted\s*\|\s*$/.test(row)) err('decisions/README.md: row says Accepted while section 10 has Open items (use Proposed)');
      }
    }
  }
  const idx = tableRows(read('docs/specs/README.md'), /^\| # \| Folder \|/);
  if (!idx) err('docs/specs/README.md: index table not found');
  else if (open.length && idx.some((r) => r.startsWith(`| ${meta.id} |`) && /\|\s*Ready\s*\|\s*$/.test(r))) err('docs/specs/README.md: index row says Ready while section 10 has Open items (use Draft)');
  if (idx && !idx.some((r) => r.startsWith(`| ${meta.id} |`) && r.includes(`(${folder}/)`) && (!meta.branch || r.includes(`\`${meta.branch}\``)))) err(`docs/specs/README.md: no index row "| ${meta.id} | [${folder}](${folder}/) | ... | \`${meta.branch}\` | ... |" inside the table`);

  // 6. Scope: a spec PR changes only spec files (never code, never a builder file)
  const changed = uniq([...lines(git(['diff', '--name-only', `${base}...HEAD`])), ...lines(git(['diff', '--name-only', 'HEAD'])), ...lines(git(['ls-files', '--others', '--exclude-standard']))]);
  if (git(['rev-parse', '--verify', '--quiet', base]) === null) warn(`scope not checked: ref ${base} not found (use --base <ref>)`);
  else {
    const decFile = dec ? `decisions/${dec[1]}-${dec[2]}.md` : null;
    const ok = (f) => f.startsWith(`${dir}/`) || f === decFile || f === 'decisions/README.md' || f === 'docs/specs/README.md';
    for (const f of changed.filter((f) => !ok(f))) err(`scope: ${f} changed; a spec PR changes only its folder, its decision file and the two index files`);
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
  console.log(`\n${dir}: OK${open.length ? ` (draft, ${open.length} open question(s))` : ''}.`);
}

// ---------------------------------------------------------------------------------- main

// Only when run as a command: gate 16 (scripts/check-test-changes.mjs) imports PLUMBING and idsIn.
if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  const [cmd, ...rest] = process.argv.slice(2);
  if (cmd === 'next') cmdNext(rest);
  else if (cmd === 'tests') cmdTests(rest);
  else if (cmd === 'check') cmdCheck(rest);
  else {
    console.error(readFileSync(new URL(import.meta.url), 'utf8').split('\n').slice(1, 17).join('\n'));
    process.exit(USAGE);
  }
}
