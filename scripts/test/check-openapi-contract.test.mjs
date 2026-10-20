#!/usr/bin/env node
// Self-test of the gate 8 contract check (scripts/check-openapi-contract.mjs, issue #54).
// Runs in gate 8 before the check itself.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { differences, enforced, validate } from '../check-openapi-contract.mjs';

const doc = () => ({
  openapi: '3.1.0',
  info: { title: 'OpenAPI definition', version: 'v0' },
  servers: [{ url: 'http://localhost', description: 'Generated server url' }],
  paths: {
    '/api/v1/admin/groups': { get: { operationId: 'listGroups', responses: { 200: { description: 'OK' } } } },
  },
  components: {
    schemas: {
      Flag: {
        type: 'object',
        required: ['id', 'key', 'enabled'],
        properties: { id: { type: 'string' }, key: { type: 'string' }, enabled: { type: 'boolean' } },
      },
      State: { type: 'string', enum: ['ON', 'OFF'] },
    },
  },
});

test('equal documents have no differences', () => {
  assert.deepEqual(differences(doc(), doc()), []);
});

test('key order, servers and the order of "required" do not count', () => {
  const built = doc();
  built.servers = [{ url: 'http://127.0.0.1:54321' }];
  built.components.schemas.Flag.required = ['enabled', 'id', 'key'];
  const { paths, info, ...rest } = built;
  assert.deepEqual(differences(doc(), { paths, ...rest, info }), []);
});

test('the order of "enum" counts', () => {
  const built = doc();
  built.components.schemas.State.enum = ['OFF', 'ON'];
  assert.deepEqual(differences(doc(), built), [
    '/components/schemas/State/enum/0: contract "ON", build "OFF"',
    '/components/schemas/State/enum/1: contract "OFF", build "ON"',
  ]);
});

test('a path missing from the build is reported with its JSON pointer', () => {
  const built = doc();
  delete built.paths['/api/v1/admin/groups'];
  assert.deepEqual(differences(doc(), built), ['/paths/~1api~1v1~1admin~1groups: only in the contract']);
});

test('an extra property in the build is reported', () => {
  const built = doc();
  built.components.schemas.Flag.properties.extra = { type: 'string' };
  assert.deepEqual(differences(doc(), built), ['/components/schemas/Flag/properties/extra: only in the build']);
});

test('a changed type and a dropped required field are reported', () => {
  const built = doc();
  built.components.schemas.Flag.properties.enabled.type = 'string';
  built.components.schemas.Flag.required = ['id', 'key'];
  assert.deepEqual(differences(doc(), built), [
    '/components/schemas/Flag/properties/enabled/type: contract "boolean", build "string"',
    '/components/schemas/Flag/required: contract ["enabled","id","key"], build ["id","key"]',
  ]);
});

test('enforced on main and at the end of the spec only', () => {
  const spec = { id: '007', milestones: [14, 15] };
  assert.equal(enforced({ n: 3, complete: false, spec: null }), true);
  assert.equal(enforced({ n: 15, complete: true, spec }), true);
  assert.equal(enforced({ n: 15, complete: false, spec }), false);
  assert.equal(enforced({ n: 14, complete: true, spec }), false);
});

test('validate accepts the fixture and names each problem', () => {
  assert.deepEqual(validate(doc()), []);
  assert.deepEqual(validate([]), ['not a JSON object']);
  assert.deepEqual(validate({ openapi: '2.0', info: { title: 't' }, paths: { groups: {} } }), [
    '"openapi" is not a 3.x.y version',
    '"info" needs a "title" and a "version"',
    'path "groups" does not start with /',
  ]);
});
