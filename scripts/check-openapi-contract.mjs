#!/usr/bin/env node
// Gate 8, contract part (issue #54): the API the build serves must match the owner's contract.
//
// docs/architecture/openapi.json is the one project contract, written by the owner in the spec
// phase (docs/process/spec-flow.md). backend/openapi.json is what springdoc renders from the
// code. The two must be equal after normalization:
//   - top-level "servers" is ignored (springdoc fills in the test server URL);
//   - object keys are compared in any order;
//   - "required" arrays are compared as sets.
// Every other difference counts. Arrays such as "enum" or "type" keep their order.
//
// When: a difference fails the gate on a branch without an active spec (main), and on the
// active spec's branch once its last milestone is complete. During the spec's earlier
// milestones the differences are printed as WARN: the builder works towards the contract.
//
// Usage: node scripts/check-openapi-contract.mjs [contract] [built]
//        node scripts/check-openapi-contract.mjs --validate <file>
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { milestoneState } from './lib/active-spec.mjs';

export const CONTRACT = 'docs/architecture/openapi.json';
export const BUILT = 'backend/openapi.json';
const MAX_SHOWN = 50;

function normalize(value, key) {
  if (Array.isArray(value)) {
    const items = value.map((v) => normalize(v));
    return key === 'required' ? [...items].sort() : items;
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.keys(value)
        .sort()
        .map((k) => [k, normalize(value[k], k)]),
    );
  }
  return value;
}

function pointer(parts) {
  return '/' + parts.map((p) => String(p).replaceAll('~', '~0').replaceAll('/', '~1')).join('/');
}

function walk(a, b, parts, out) {
  if (JSON.stringify(a) === JSON.stringify(b)) return;
  const bothObjects = a && b && typeof a === 'object' && typeof b === 'object' && Array.isArray(a) === Array.isArray(b);
  if (!bothObjects || (Array.isArray(a) && a.length !== b.length)) {
    out.push(`${pointer(parts)}: contract ${JSON.stringify(a) ?? '(missing)'}, build ${JSON.stringify(b) ?? '(missing)'}`);
    return;
  }
  for (const k of new Set([...Object.keys(a), ...Object.keys(b)])) {
    if (!(k in b)) out.push(`${pointer([...parts, k])}: only in the contract`);
    else if (!(k in a)) out.push(`${pointer([...parts, k])}: only in the build`);
    else walk(a[k], b[k], [...parts, k], out);
  }
}

/** Differences between the contract and the built document, as "<JSON pointer>: ..." lines. */
export function differences(contract, built) {
  const strip = ({ servers, ...rest }) => normalize(rest);
  const out = [];
  walk(strip(contract), strip(built), [], out);
  return out;
}

/** Problems that make a file no usable OpenAPI 3 document. */
export function validate(doc) {
  const problems = [];
  if (!doc || typeof doc !== 'object' || Array.isArray(doc)) return ['not a JSON object'];
  if (typeof doc.openapi !== 'string' || !/^3\.\d+\.\d+$/.test(doc.openapi)) problems.push('"openapi" is not a 3.x.y version');
  if (!doc.info || typeof doc.info.title !== 'string' || typeof doc.info.version !== 'string') problems.push('"info" needs a "title" and a "version"');
  if (!doc.paths || typeof doc.paths !== 'object') problems.push('"paths" is missing');
  else for (const p of Object.keys(doc.paths)) if (!p.startsWith('/')) problems.push(`path "${p}" does not start with /`);
  return problems;
}

/** True when a difference fails the gate (see the header). */
export function enforced(state) {
  if (!state.spec) return true;
  return state.complete && state.n === Math.max(...state.spec.milestones);
}

function read(file) {
  try {
    return JSON.parse(readFileSync(file, 'utf8'));
  } catch (e) {
    console.error(`${file}: ${e.code === 'ENOENT' ? 'not found' : `not valid JSON (${e.message})`}`);
    process.exit(1);
  }
}

function main(args) {
  if (args[0] === '--validate') {
    const problems = validate(read(args[1] ?? CONTRACT));
    for (const p of problems) console.error(`${args[1] ?? CONTRACT}: ${p}`);
    if (!problems.length) console.log(`${args[1] ?? CONTRACT}: valid OpenAPI ${read(args[1] ?? CONTRACT).openapi}`);
    return problems.length ? 1 : 0;
  }
  const [contractFile = CONTRACT, builtFile = BUILT] = args;
  const contract = read(contractFile);
  const problems = validate(contract);
  if (problems.length) {
    for (const p of problems) console.error(`${contractFile}: ${p}`);
    return 1;
  }
  const diff = differences(contract, read(builtFile));
  if (!diff.length) {
    console.log(`API contract: ${builtFile} matches ${contractFile}`);
    return 0;
  }
  const state = milestoneState();
  const hard = enforced(state);
  const label = hard ? 'FAIL' : 'WARN';
  console.log(`${label}: ${builtFile} differs from the owner contract ${contractFile} in ${diff.length} place(s):`);
  for (const d of diff.slice(0, MAX_SHOWN)) console.log(`  ${d}`);
  if (diff.length > MAX_SHOWN) console.log(`  ... and ${diff.length - MAX_SHOWN} more`);
  if (hard) {
    console.log('Fix the code so it serves the contract. If the contract looks wrong, escalate; never edit it.');
    return 1;
  }
  console.log(`Allowed until spec ${state.spec.id} milestone ${Math.max(...state.spec.milestones)} is complete.`);
  return 0;
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) process.exit(main(process.argv.slice(2)));
