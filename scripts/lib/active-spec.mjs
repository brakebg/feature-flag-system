// Active spec and milestone for the gates (docs/specs/README.md).
//
// Every spec has its own folder docs/specs/<NNN-name>/ with spec.json (owner) and, once its
// work has started, a MILESTONE file (agent): "<n>" while milestone n is in progress,
// "<n> complete" when it is done. Nothing here is shared between specs, so several specs
// can be worked on in parallel, each on its own branch.
//
// Active spec: env FF_SPEC (id or folder name), else the spec whose spec.json "branch"
// equals the current branch (GITHUB_HEAD_REF / GITHUB_REF_NAME in CI, else git). On a branch
// that belongs to no spec (main), there is no active spec and the highest MILESTONE of all
// specs applies.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';

const SPECS = 'docs/specs';

function readMilestone(file) {
  if (!existsSync(file)) return null;
  const m = /^\s*(\d+)\s*(complete)?\s*$/.exec(readFileSync(file, 'utf8'));
  if (!m) throw new Error(`${file}: expected "<n>" or "<n> complete"`);
  return { n: Number(m[1]), complete: Boolean(m[2]) };
}

/** All specs, sorted by id. */
export function specs(root = '.') {
  const base = path.join(root, SPECS);
  if (!existsSync(base)) return [];
  return readdirSync(base, { withFileTypes: true })
    .filter((d) => d.isDirectory() && existsSync(path.join(base, d.name, 'spec.json')))
    .map((d) => {
      const dir = path.join(SPECS, d.name);
      const meta = JSON.parse(readFileSync(path.join(root, dir, 'spec.json'), 'utf8'));
      return { ...meta, name: d.name, dir, milestone: readMilestone(path.join(root, dir, 'MILESTONE')) };
    })
    .sort((a, b) => a.id.localeCompare(b.id));
}

export function currentBranch() {
  const ci = process.env.GITHUB_HEAD_REF || process.env.GITHUB_REF_NAME;
  if (ci) return ci;
  try {
    return execFileSync('git', ['rev-parse', '--abbrev-ref', 'HEAD'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  } catch {
    return '';
  }
}

export function activeSpec(root = '.') {
  const all = specs(root);
  const want = process.env.FF_SPEC;
  if (want) {
    const s = all.find((x) => x.id === want || x.name === want);
    if (!s) throw new Error(`FF_SPEC=${want}: no such spec in ${SPECS}`);
    return s;
  }
  const branch = currentBranch();
  return all.find((x) => x.branch === branch) ?? null;
}

/** The milestone the gates use: { n, complete, spec }. */
export function milestoneState(root = '.') {
  const active = activeSpec(root);
  if (active) {
    return { ...(active.milestone ?? { n: Math.min(...active.milestones), complete: false }), spec: active };
  }
  const started = specs(root).filter((s) => s.milestone);
  if (!started.length) return { n: 1, complete: false, spec: null };
  const top = started.reduce((a, b) => (b.milestone.n > a.milestone.n ? b : a));
  return { ...top.milestone, spec: null };
}

/** A criterion due in milestone `due` must have a passing test from the end of that milestone. */
export function isDue(due, state = milestoneState()) {
  return due < state.n || (due === state.n && state.complete);
}
