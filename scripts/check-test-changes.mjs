#!/usr/bin/env node
// Gate 16 (spec 004, changed by spec 006): test changes. A test may change beyond plumbing only
// when a criterion it proves has changed (issue #22, decision 0012, spec 006).
//
// Usage: node scripts/check-test-changes.mjs [--base <ref>] [--head <ref>] [--root <dir>]
//   --base  compare the tests from the merge base of this ref and the head (default:
//           origin/main, else main)
//   --head  the commit to check (default: the working tree, committed and uncommitted changes)
//   --root  the git repository to check (default: this one; the self-test uses a fixture repo)
//
// Paths: backend/src/test/**, frontend/src/**/*.test.*, frontend/e2e/**, docs/project-acceptance-criteria.md.
//
// Allowed IDs come from docs/project-acceptance-criteria.md alone: every criterion whose version
// went up, or that was retired, between the point where the head left main (merge base with
// origin/main, else main) and the head. No branch name, no active spec, no test-changes.md.
//
// 1. A changed test file passes when its tags on the base include an allowed ID. Tags are the
//    gate-14 IDs in string literals: JUnit @Tag("ID"), "[ID]" in Vitest / Playwright titles.
//    Tags added on the branch do not count.
// 2. Any other changed test file: every changed or removed line must be plumbing (the clause in
//    every spec's test-changes.md): an import, an annotation without arguments (not @Tag, not a
//    test annotation), a comment, a helper signature, a field or variable type, or an
//    identifier-only change that keeps every literal, operator and assertion method. Added lines
//    are free.
// 3. A deleted test file, or a tag that disappears from a file: FAIL.
// 4. docs/project-acceptance-criteria.md follows the version rules (docs/process/traceability.md):
//    a changed criterion has its version + 1, a retired one keeps its version, a new one starts
//    at v1, no criterion line is removed. Checkbox ticks, headings and notes are free. Only the
//    owner edits the file (locked; locked-files-guard reports every other change).
// Tuned towards false blocks: a blocked plumbing change is a DECISIONS.md note and an
// escalation, never an edit of this script.
//
// Exit 0 = pass. Exit 1 = violations, one "FAIL <file>:<line>: <reason>" line each. Exit 2 = usage.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const USAGE = 2;
const ID_RE = /\b(?:AC|ERR)-[A-Za-z0-9{}/._-]*[A-Za-z0-9}]/g; // gate 14
const REGISTRY = 'docs/project-acceptance-criteria.md';
const PATHS = [':(glob)backend/src/test/**', ':(glob)frontend/src/**/*.test.*', ':(glob)frontend/e2e/**', REGISTRY];
/** A criterion line: id, version, the text after the version (checkbox not included). */
const CRITERION = /^- \[[ x]\] \*\*(AC-[A-Z]+-\d+)\*\* v(\d+) · (.*)$/;

// ------------------------------------------------------------------------------ arguments

const opts = {};
const argv = process.argv.slice(2);
for (let i = 0; i < argv.length; i++) {
  const k = argv[i].replace(/^--/, '');
  if (!['base', 'head', 'root'].includes(k) || !argv[i].startsWith('--') || !argv[i + 1]) {
    console.error('usage: check-test-changes.mjs [--base <ref>] [--head <ref>] [--root <dir>]');
    process.exit(USAGE);
  }
  opts[k] = argv[++i];
}
const ROOT = path.resolve(opts.root ?? HERE);

function git(args) {
  try {
    return execFileSync('git', ['-C', ROOT, ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'], maxBuffer: 256 << 20 });
  } catch {
    return null;
  }
}
const commit = (ref) => git(['rev-parse', '--verify', '--quiet', `${ref}^{commit}`])?.trim() || null;

const problems = [];
const fail = (file, line, why) => problems.push(`FAIL ${file}${line ? `:${line}` : ''}: ${why}`);

// ------------------------------------------------------------------------------ base, head

const head = opts.head ? commit(opts.head) : null;
if (opts.head && !head) {
  console.error(`check-test-changes: --head ${opts.head} is not a commit`);
  process.exit(USAGE);
}
const baseRef = opts.base ?? (commit('origin/main') ? 'origin/main' : 'main');
if (!commit(baseRef)) {
  console.log(`FAIL base ${baseRef} not found: nothing can be compared (git fetch origin main)`);
  process.exit(1);
}
const from = git(['merge-base', baseRef, head ?? 'HEAD'])?.trim();
if (!from) {
  console.log(`FAIL no merge base of ${baseRef} and ${head ?? 'HEAD'}`);
  process.exit(1);
}

// ------------------------------------------------------------------------------ allowed IDs

// The criteria are compared from where the head left main, whatever --base says: the spec's own
// commits before its ready tag are the ones that raise the versions.
const mainRef = commit('origin/main') ? 'origin/main' : commit('main') ? 'main' : baseRef;
const mainBase = git(['merge-base', mainRef, head ?? 'HEAD'])?.trim() ?? from;

/** Criteria of one version of the registry: Map id -> { version, rest, retired, n }. */
function criteria(text) {
  const out = new Map();
  (text ?? '').split('\n').forEach((line, i) => {
    const m = CRITERION.exec(line);
    if (m) out.set(m[1], { version: Number(m[2]), rest: m[3], retired: / · retired by /.test(m[3]), n: i + 1 });
  });
  return out;
}

const show = (ref, file) => git(['show', `${ref}:${file}`]);
const headText = (file) => (head ? show(head, file) : existsSync(path.join(ROOT, file)) ? readFileSync(path.join(ROOT, file), 'utf8') : null);

const oldCriteria = criteria(show(mainBase, REGISTRY));
const newCriteria = criteria(headText(REGISTRY));
const allowed = new Set();
for (const [id, now] of newCriteria) {
  const was = oldCriteria.get(id);
  if (was && (now.version > was.version || (now.retired && !was.retired))) allowed.add(id);
}
const source = `${REGISTRY} against ${mainRef} (merge base ${mainBase.slice(0, 7)})`;

// ------------------------------------------------------------------------------ diff

const range = head ? [from, head] : [from];

/** Changed and removed lines of one file, grouped by hunk: [{ del: [{n, text}], add: [{n, text}] }]. */
function hunks(oldFile, newFile) {
  const out = [];
  let cur;
  let o = 0;
  let n = 0;
  const diff = git(['diff', '-U0', '-M', '--no-color', '--no-ext-diff', ...range, '--', ...new Set([oldFile, newFile])]) ?? '';
  for (const line of diff.split('\n')) {
    const h = /^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@/.exec(line);
    if (h) {
      cur = { del: [], add: [] };
      out.push(cur);
      o = Number(h[1]);
      n = Number(h[2]);
    } else if (!cur || line.startsWith('---') || line.startsWith('+++')) continue;
    else if (line.startsWith('-')) cur.del.push({ n: o++, text: line.slice(1) });
    else if (line.startsWith('+')) cur.add.push({ n: n++, text: line.slice(1) });
  }
  return out;
}

const LIT_RE = /'(?:[^'\\\n]|\\.)*'|"(?:[^"\\\n]|\\.)*"|`(?:[^`\\]|\\.)*`/g;
/** Gate-14 IDs inside string literals: @Tag("ID"), "[ID] ..." titles. */
const tagsIn = (text) => new Set((text.match(LIT_RE) ?? []).flatMap((lit) => lit.match(ID_RE) ?? []));

const TOKEN_RE = /\/\/.*$|\/\*.*?\*\/|'(?:[^'\\]|\\.)*'|"(?:[^"\\]|\\.)*"|`(?:[^`\\]|\\.)*`|\d[\w.]*|[A-Za-z_$][\w$]*|\S/g;
const KEYWORDS = new Set(['true', 'false', 'null', 'undefined', 'new', 'return', 'throw', 'throws', 'typeof', 'instanceof', 'if', 'else', 'for', 'while', 'do', 'switch', 'case', 'default', 'break', 'continue', 'try', 'catch', 'finally', 'await', 'async']);
const ASSERT_RE = /^(assert|expect|verify|fail$|should|to[A-Z]|is[A-Z]|has[A-Z]|contains|doesNot|startsWith|endsWith|matches|satisf|extracting|then[A-Z]|times$|never$|atLeast|atMost|equalTo|andExpect|exactly|resolves$|rejects$|not$)/;
const isIdent = (t) => /^[A-Za-z_$][\w$]*$/.test(t);
/** Generic type arguments (List<Flag>, new Foo<>()) are types: dropped. "a < b" keeps its spaces. */
const noGenerics = (text) => text.replace(/([\w$])<[\w$?,.\s<>[\]]*>/g, '$1');
const tokens = (text) => (noGenerics(text).match(TOKEN_RE) ?? []).filter((t) => !t.startsWith('//') && !t.startsWith('/*'));
/**
 * Identifiers become "ID"; literals, operators, keywords and assertion methods stay. A run of
 * identifiers joined by "," or "." (or side by side, "Type name") becomes one "ID", so a renamed
 * type or an identifier argument added or dropped (a helper signature change) is plumbing.
 */
function skeleton(text) {
  const out = [];
  for (const t of tokens(text)) {
    const id = isIdent(t) && !KEYWORDS.has(t) && !ASSERT_RE.test(t);
    const n = out.length;
    if (id && out[n - 1] === 'ID') continue;
    if (id && (out[n - 1] === ',' || out[n - 1] === '.') && out[n - 2] === 'ID') {
      out.pop();
      continue;
    }
    out.push(id ? 'ID' : t);
  }
  return out.join(' ');
}
const asserts = (text) => tokens(text).filter((t) => isIdent(t) && ASSERT_RE.test(t)).join(' ');
const literals = (text) => tokens(text).filter((t) => !isIdent(t) && /^['"`\d]/.test(t)).join(' ');

const TEST_ANNOTATION = /^@(Tag|Tags|Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate|Nested)\b/;

/** Line numbers (1-based) of import statements, also the inner lines of a multi-line TS import. */
function importLines(text) {
  const out = new Set();
  let open = false;
  (text ?? '').split('\n').forEach((l, i) => {
    const t = l.trim();
    if (open || /^(import|package)\b/.test(t)) {
      out.add(i + 1);
      open = !/;\s*$|\bfrom\s+['"]|^import\s+['"]/.test(t) && !/^package\b/.test(t);
    }
  });
  return out;
}

/** Changes that are plumbing on their own: blank, comment, import, annotation without arguments. */
function neutral(line, imports) {
  const t = line.text.trim();
  if (!t || /^(\/\/|\/\*|\*)/.test(t) || imports.has(line.n)) return true;
  return /^@[\w.]+\s*(\(\s*\))?$/.test(t) && !TEST_ANNOTATION.test(t);
}

/** A removed field without a value ("@Autowired ObjectMapper json;") or its constructor assignment. */
function unusedField(text) {
  const t = text.trim().replace(/^(@[\w.]+\s+)+/, '');
  const d = DECL.exec(t);
  return (d && (d[3] === undefined || d[3] === ';') && !NOT_DECL.test(t)) || /^(this\.)?[\w$]+\s*=\s*[\w$]+;$/.test(t);
}

const SIGNATURE = /^(?:(?:public|protected|private|static|final|abstract|synchronized|export|async|default)\s+)*(?:function\s*\*?\s*[\w$]+|(?:<[^>]*>\s*)?[\w$.]+(?:<.*>)?(?:\[\])*\??\s+[\w$]+|[A-Z][\w$]*)\s*\(/;
const NOT_DECL = /^(return|new|throw|await|else|case)\b/;
/** A method or function header: the parameters may change, literals and assertions may not. */
const signatureOk = (a, b) =>
  [a, b].every((x) => SIGNATURE.test(x.trim()) && !NOT_DECL.test(x.trim()) && !asserts(x)) && literals(a) === literals(b);

const DECL = /^((?:(?:private|protected|public|static|final|readonly)\s+)*)(?:var|let|const|[\w$.]+(?:<.*>)?(?:\[\])*\??)\s+([\w$]+)\s*(?::\s*[^=;]+?)?\s*(=.*|;)?$/;
/** A field or variable declaration: the type may change, the name and value may not (beyond identifiers). */
function declOk(a, b) {
  const x = DECL.exec(a.trim());
  const y = DECL.exec(b.trim());
  if (!x || !y || NOT_DECL.test(a.trim()) || NOT_DECL.test(b.trim())) return false;
  return skeleton(`${x[1]}${x[2]}${x[3] ?? ''}`) === skeleton(`${y[1]}${y[2]}${y[3] ?? ''}`);
}

function why(a, b) {
  if (asserts(a) !== asserts(b)) return 'assertion method changed';
  if ([...tagsIn(a)].join() !== [...tagsIn(b)].join()) return '@Tag or [ID] title changed';
  if (literals(a) !== literals(b)) return 'expected value or literal changed';
  return 'code changed beyond plumbing';
}

const short = (s) => (s.trim().length > 90 ? `${s.trim().slice(0, 87)}...` : s.trim());

function checkTestFile(file, oldFile, status) {
  const before = show(from, oldFile) ?? '';
  const after = headText(file) ?? '';
  const baseTags = tagsIn(before);
  const hit = [...baseTags].filter((id) => allowed.has(id));
  if (hit.length) return `allowed by ${hit.join(', ')}`;
  if (status === 'D') {
    fail(oldFile, 0, `test file deleted${baseTags.size ? ` (tags: ${[...baseTags].join(', ')})` : ''}`);
    return null;
  }
  const afterTags = tagsIn(after);
  for (const id of baseTags) if (!afterTags.has(id)) fail(file, 0, `tag ${id} disappeared from the file`);

  const oldImports = importLines(before);
  const newImports = importLines(after);
  const hs = hunks(oldFile, file).map((h) => ({
    del: h.del.filter((l) => !neutral(l, oldImports)),
    add: h.add.filter((l) => !neutral(l, newImports)),
  }));
  // A line removed in one place and added unchanged in another is a move.
  const added = new Map();
  for (const l of hs.flatMap((h) => h.add)) added.set(l.text.trim(), [...(added.get(l.text.trim()) ?? []), l]);
  for (const h of hs) {
    h.del = h.del.filter((l) => {
      const same = added.get(l.text.trim());
      if (!same?.length) return true;
      same.pop().moved = true;
      return false;
    });
  }
  for (const h of hs) {
    const add = h.add.filter((l) => !l.moved);
    if (!h.del.length) continue; // only added lines
    // The whole hunk differs only in identifiers (also when the formatter re-wraps it).
    if (skeleton(h.del.map((l) => l.text).join(' ')) === skeleton(add.map((l) => l.text).join(' '))) continue;
    h.del.forEach((d, i) => {
      const a = add[i];
      if (!a) return unusedField(d.text) ? undefined : fail(oldFile, d.n, `line removed (line number on the base): ${short(d.text)}`);
      if (skeleton(d.text) === skeleton(a.text) || signatureOk(d.text, a.text) || declOk(d.text, a.text)) return;
      fail(file, a.n, `${why(d.text, a.text)}: "${short(d.text)}" -> "${short(a.text)}"`);
    });
  }
  return 'plumbing only';
}

function checkRegistry(status) {
  if (status === 'D') return fail(REGISTRY, 0, 'deleted');
  for (const [id, was] of oldCriteria) {
    if (!newCriteria.has(id)) fail(REGISTRY, was.n, `${id} removed (line number on the base): retire a criterion, never delete it`);
  }
  for (const [id, now] of newCriteria) {
    const was = oldCriteria.get(id);
    if (!was) {
      if (now.version !== 1) fail(REGISTRY, now.n, `new ${id} must start at v1 (found v${now.version})`);
      continue;
    }
    if (now.version === was.version + 1) continue;
    if (now.version !== was.version) fail(REGISTRY, now.n, `${id} v${was.version} -> v${now.version}: a changed criterion goes up by exactly 1`);
    else if (now.rest !== was.rest && !(now.retired && !was.retired)) fail(REGISTRY, now.n, `${id} text changed without raising its version (v${now.version})`);
  }
}

// ------------------------------------------------------------------------------ run

const status = git(['diff', '--name-status', '-M', '--no-color', ...range, '--', ...PATHS]);
if (status === null) {
  console.log(`FAIL git diff ${range.join(' ')} failed`);
  process.exit(1);
}
const notes = [];
let checked = 0;
for (const row of status.split('\n').filter(Boolean)) {
  const [st, a, b] = row.split('\t');
  const kind = st[0];
  const file = b ?? a;
  if (kind === 'A') continue; // a new file changes no existing test
  checked++;
  if (file === REGISTRY || a === REGISTRY) checkRegistry(kind);
  else {
    const note = checkTestFile(file, a, kind);
    if (note) notes.push(`  ${file}: ${note}`);
  }
}

const what = `${head ? head.slice(0, 7) : 'working tree'} against ${baseRef} (merge base ${from.slice(0, 7)})`;
console.log(`check-test-changes: ${what}; ${source}; allowed: ${[...allowed].join(', ') || 'none'}`);
if (notes.length) console.log(notes.join('\n'));
if (problems.length) {
  console.log(problems.join('\n'));
  console.log(`\ncheck-test-changes: ${problems.length} violation(s) in ${checked} changed file(s). A test whose criteria did not change may change only by the plumbing clause.`);
  process.exit(1);
}
console.log(`check-test-changes: ${checked} changed file(s), no violations`);
