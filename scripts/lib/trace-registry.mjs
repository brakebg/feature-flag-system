// ID registry for gate 14 (docs/process/traceability.md).
//
// - AC IDs come from docs/project-acceptance-criteria.md, the one current list. Each line:
//     - [ ] **<ID>** v<N> · <text> · spec <NNN>[ · changed by spec <MMM>] · due M<n>
//     - [ ] **<ID>** v<N> · <text> · spec <NNN> · retired by <decision or spec>
//   `due`: the milestone from whose end every test of the ID must pass. Before that it is
//   reported as pending, not as a failure.
// - A criterion of spec 001 always counts. A criterion created or changed by a later spec
//   counts on a branch where that spec is active or has started (its builder/MILESTONE file),
//   so an unbuilt spec's criteria never fail another spec's branch. A changed criterion of an
//   unstarted spec counts at its spec 001 due milestone (all of spec 001 is due from M8).
// - ERR IDs: one per status code in the Errors column of spec 6.1, plus the auth and
//   evaluation endpoints of spec 5 and 7. Format ERR-<METHOD>-<path>-<status>, path
//   relative to /api/v1 (or the actuator path).
// - Each 9.1 error-table row names the ERR ID whose integration test asserts that row's
//   status and problem `type`.
// Owner condition (ESC-001): a due milestone may only move earlier, never later, without a
// new escalation.
import { readFileSync } from 'node:fs';
import { activeSpec, specs } from './active-spec.mjs';

export const REGISTRY = 'docs/project-acceptance-criteria.md';
export const AC_LINE =
  /^- \[([ x])\] \*\*(AC-[A-Z]+-\d+)\*\* v(\d+) · (.*) · spec (\d{3})(?: · changed by spec (\d{3}))?(?: · due M(\d+)| · retired by (.+))$/;

/** Every criterion line of the registry text, outside code blocks. Malformed lines in `bad`. */
export function parseRegistry(text) {
  const out = [];
  const bad = [];
  let fence = false;
  text.split('\n').forEach((line, i) => {
    if (/^```/.test(line)) fence = !fence;
    if (fence || !/^- \[[ x]\] /.test(line)) return;
    const m = AC_LINE.exec(line);
    if (!m) return bad.push({ n: i + 1, line });
    out.push({
      n: i + 1, id: m[2], version: Number(m[3]), text: m[4], spec: m[5], changedBy: m[6] ?? null,
      due: m[7] ? Number(m[7]) : null, removed: Boolean(m[8]), retiredBy: m[8] ?? null,
    });
  });
  return { acs: out, bad };
}

export function acIds(file = REGISTRY) {
  return parseRegistry(readFileSync(file, 'utf8')).acs;
}

/** The criteria that count on this branch (see the header), with their due milestone. */
export function countedAcs(root = '.') {
  const active = activeSpec(root);
  const live = new Set(['001', ...specs(root).filter((s) => s.milestone || (active && active.id === s.id)).map((s) => s.id)]);
  const out = [];
  for (const a of acIds(`${root}/${REGISTRY}`)) {
    if (a.changedBy && !live.has(a.changedBy)) {
      if (live.has(a.spec)) out.push({ ...a, due: a.removed ? null : 8 });
      continue;
    }
    if (live.has(a.spec)) out.push(a);
  }
  return out;
}

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
