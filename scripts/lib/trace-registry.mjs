// ID registry for gate 14 (spec 11.4).
//
// - AC IDs come from docs/acceptance-criteria.md (verbatim copy of spec 11.2), plus the
//   acceptance-criteria.md of every later spec that is active or has started
//   (docs/specs/<id>/, "Due milestone: <n>" in that file; docs/specs/README.md).
// - ERR IDs: one per status code in the Errors column of spec 6.1, plus the auth and
//   evaluation endpoints of spec 5 and 7. Format ERR-<METHOD>-<path>-<status>, path
//   relative to /api/v1 (or the actuator path).
// - Each 9.1 error-table row names the ERR ID whose integration test asserts that row's
//   status and problem `type`.
// - `due`: the milestone (spec 12.2) whose "Done when" first needs the ID. Before that
//   milestone a missing test is reported as pending, not as a failure. From M8 every ID
//   is due (DECISIONS.md D-003).
// Owner condition (ESC-001): a due milestone may only move earlier, never later, without a
// new escalation.
import { existsSync, readFileSync } from 'node:fs';
import { activeSpec, specs } from './active-spec.mjs';

export function acIds(file = 'docs/acceptance-criteria.md') {
  const text = readFileSync(file, 'utf8');
  const ids = [];
  for (const m of text.matchAll(/^- \[[ x]\] \*\*(AC-[A-Z]+-\d+)\*\* · (.*)$/gm)) {
    ids.push({ id: m[1], removed: m[2].startsWith('[Removed') });
  }
  return ids;
}

/**
 * Criteria of the later specs (docs/specs/<id>/acceptance-criteria.md) that count on this
 * branch: the active spec, and every spec whose work has started here (MILESTONE file).
 * Spec 001 uses docs/acceptance-criteria.md and acDue below.
 */
export function specAcs(root = '.') {
  const active = activeSpec(root);
  const out = [];
  for (const s of specs(root)) {
    if (s.acceptance === 'docs/acceptance-criteria.md' || !s.acceptance) continue;
    if (!(s.milestone || (active && active.id === s.id))) continue;
    const file = `${root}/${s.acceptance}`;
    if (!existsSync(file)) throw new Error(`${s.dir}/spec.json: ${s.acceptance} does not exist`);
    const text = readFileSync(file, 'utf8');
    const due = Number(/^Due milestone: (\d+)\s*$/m.exec(text)?.[1]);
    if (!due) throw new Error(`${s.acceptance}: missing "Due milestone: <n>"`);
    for (const a of acIds(file)) out.push({ ...a, due, spec: s.id });
  }
  return out;
}

// Milestone in which each AC is first fully testable (spec 12.2).
export const acDue = {
  'AC-AUTH-1': 6, 'AC-AUTH-2': 6, 'AC-AUTH-3': 6, 'AC-AUTH-4': 6, 'AC-AUTH-6': 6,
  'AC-GRP-1': 7, 'AC-GRP-2': 7, 'AC-GRP-3': 7, 'AC-GRP-4': 7, 'AC-GRP-5': 7,
  'AC-FLAG-1': 7, 'AC-FLAG-2': 7, 'AC-FLAG-3': 7, 'AC-FLAG-4': 7, 'AC-FLAG-5': 7, 'AC-FLAG-6': 7,
  'AC-EVAL-1': 3, 'AC-EVAL-2': 3, 'AC-EVAL-4': 3,
  'AC-EVAL-3': 5, 'AC-EVAL-5': 5, 'AC-EVAL-6': 5, 'AC-EVAL-7': 5,
  'AC-CACHE-1': 5, 'AC-CACHE-2': 5, 'AC-CACHE-3': 5, 'AC-CACHE-4': 5, 'AC-CACHE-5': 5,
  'AC-CACHE-6': 5, 'AC-CACHE-7': 5, 'AC-CACHE-8': 5, 'AC-CACHE-9': 8,
  'AC-AUD-1': 4, 'AC-AUD-2': 7, 'AC-AUD-3': 4,
  'AC-OPS-1': 8, 'AC-OPS-2': 8, 'AC-OPS-3': 2, 'AC-OPS-4': 5,
};

const admin = [
  ['GET', '/admin/groups', [400, 401, 403]],
  ['POST', '/admin/groups', [400, 401, 403, 409, 413]],
  ['GET', '/admin/groups/{groupId}', [400, 401, 403, 404]],
  ['PATCH', '/admin/groups/{groupId}', [400, 401, 403, 404, 409, 413]],
  ['DELETE', '/admin/groups/{groupId}', [400, 401, 403, 404]],
  ['POST', '/admin/groups/{groupId}/flags', [400, 401, 403, 404, 409, 413]],
  ['PATCH', '/admin/flags/{flagId}', [400, 401, 403, 404, 409, 413]],
  ['POST', '/admin/flags/{flagId}/toggle', [400, 401, 403, 404, 413]],
  ['DELETE', '/admin/flags/{flagId}', [400, 401, 403, 404]],
  ['GET', '/admin/audit', [400, 401, 403]],
];
const auth = [
  ['POST', '/auth/login', [400, 401, 403]],
  ['POST', '/auth/token', [400, 401, 403]],
];
const evaluation = [
  ['GET', '/evaluate/flags', [401, 403]],
  ['GET', '/evaluate/groups/{groupKey}', [401, 403, 404]],
  ['GET', '/evaluate/flags/{groupKey}/{flagKey}', [401, 403, 404]],
];

export const errIds = {};
for (const [list, due] of [[auth, 3], [admin, 4], [evaluation, 5]]) {
  for (const [method, p, statuses] of list) {
    for (const s of statuses) errIds[`ERR-${method}-${p}-${s}`] = due;
  }
}
// 500 row of 9.1 (backend integration tests only, spec 11.4).
errIds['ERR-GET-/admin/groups-500'] = 4;

// Spec 9.1 error table: row -> the ERR ID whose test asserts this status and type.
export const errorTableRows = [
  { row: '400 validation', id: 'ERR-POST-/admin/groups-400' },
  { row: '400 malformed-request', id: 'ERR-POST-/admin/flags/{flagId}/toggle-400' },
  { row: '401 unauthorized', id: 'ERR-GET-/admin/groups-401' },
  { row: '403 forbidden', id: 'ERR-GET-/admin/groups-403' },
  { row: '403 https-required', id: 'ERR-POST-/auth/login-403' },
  { row: '404 not-found', id: 'ERR-GET-/admin/groups/{groupId}-404' },
  { row: '409 duplicate-key', id: 'ERR-POST-/admin/groups-409' },
  { row: '409 version-conflict', id: 'ERR-PATCH-/admin/flags/{flagId}-409' },
  { row: '409 limit-reached', id: 'ERR-POST-/admin/groups/{groupId}/flags-409' },
  { row: '413 payload-too-large', id: 'ERR-POST-/admin/groups-413' },
  { row: '500 internal', id: 'ERR-GET-/admin/groups-500' },
];
