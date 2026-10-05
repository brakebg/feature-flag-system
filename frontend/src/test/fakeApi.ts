import { http, HttpResponse, delay } from 'msw';
import type { Flag, GroupDetail, GroupSummary } from '../api/types';

/**
 * A small in-memory Admin API for UI unit tests (MSW). It follows spec 6.1 for the cases the UI
 * needs: list, detail, create, patch with version, toggle, delete, duplicate keys, problems.
 */
export interface FakeApi {
  groups: GroupDetail[];
  calls: { method: string; path: string; body?: unknown }[];
  /** Makes the next matching request fail with this status and problem type. */
  failNext: (method: string, pathPart: string, status: number, type: string) => void;
  /** Delays toggle responses (ms). */
  toggleDelay: number;
}

const T = '2026-10-01T10:00:00Z';
let seq = 0;
const newId = () => `0191f0c2-0000-7000-8000-${String(++seq).padStart(12, '0')}`;
const problem = (status: number, type: string, extra: object = {}) =>
  HttpResponse.json(
    {
      type: `https://featureflags.local/problems/${type}`,
      title: type,
      status,
      detail: type,
      ...extra,
    },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );

export function makeFlag(
  group: { id: string; key: string },
  key: string,
  enabled: boolean,
  description?: string,
): Flag {
  return {
    id: newId(),
    groupId: group.id,
    key,
    fullKey: `${group.key}.${key}`,
    ...(description ? { description } : {}),
    enabled,
    createdAt: T,
    createdBy: 'admin',
    updatedAt: T,
    updatedBy: 'admin',
    version: 0,
  };
}

export function makeGroup(
  key: string,
  name: string,
  flags: [string, boolean][] = [],
  description?: string,
): GroupDetail {
  const g: GroupDetail = {
    id: newId(),
    key,
    name,
    ...(description ? { description } : {}),
    createdAt: T,
    createdBy: 'system',
    updatedAt: T,
    updatedBy: 'admin',
    version: 0,
    flags: [],
  };
  g.flags = flags.map(([k, e]) => makeFlag(g, k, e));
  return g;
}

function summary(g: GroupDetail): GroupSummary {
  const { flags, createdAt: _c, ...rest } = g;
  void _c;
  return { ...rest, flagCount: flags.length, enabledCount: flags.filter((f) => f.enabled).length };
}

export function fakeApi(initial: GroupDetail[] = []) {
  const api: FakeApi = { groups: initial, calls: [], failNext: () => {}, toggleDelay: 0 };
  let failure: { method: string; part: string; status: number; type: string } | null = null;
  api.failNext = (method, part, status, type) => {
    failure = { method, part, status, type };
  };
  const failing = (method: string, path: string) => {
    if (failure && failure.method === method && path.includes(failure.part)) {
      const f = failure;
      failure = null;
      return problem(f.status, f.type);
    }
    return null;
  };
  const findFlag = (id: string) => {
    for (const g of api.groups) {
      const f = g.flags.find((x) => x.id === id);
      if (f) return { g, f };
    }
    return null;
  };
  const log = async (request: Request) => {
    const url = new URL(request.url);
    const body =
      request.method === 'GET' || request.method === 'DELETE'
        ? undefined
        : await request
            .clone()
            .json()
            .catch(() => undefined);
    api.calls.push({ method: request.method, path: url.pathname + url.search, body });
    return url.pathname;
  };

  const handlers = [
    http.get('/api/v1/admin/groups', async ({ request }) => {
      const path = await log(request);
      return (
        failing('GET', path) ??
        HttpResponse.json(api.groups.map(summary).sort((a, b) => (a.key < b.key ? -1 : 1)))
      );
    }),
    http.post('/api/v1/admin/groups', async ({ request }) => {
      const path = await log(request);
      const fail = failing('POST', path);
      if (fail) return fail;
      const body = (await request.json()) as {
        key: string;
        name: string;
        description?: string | null;
      };
      if (api.groups.some((g) => g.key === body.key)) return problem(409, 'duplicate-key');
      const g = makeGroup(body.key, body.name.trim(), [], body.description ?? undefined);
      g.createdBy = 'admin';
      api.groups.push(g);
      const { flags: _f, ...group } = g;
      void _f;
      return HttpResponse.json(group, { status: 201 });
    }),
    http.get('/api/v1/admin/groups/:id', async ({ request, params }) => {
      const path = await log(request);
      const fail = failing('GET', path);
      if (fail) return fail;
      const g = api.groups.find((x) => x.id === params.id);
      return g
        ? HttpResponse.json({ ...g, flags: [...g.flags].sort((a, b) => (a.key < b.key ? -1 : 1)) })
        : problem(404, 'not-found');
    }),
    http.patch('/api/v1/admin/groups/:id', async ({ request, params }) => {
      const path = await log(request);
      const fail = failing('PATCH', path);
      if (fail) return fail;
      const g = api.groups.find((x) => x.id === params.id);
      if (!g) return problem(404, 'not-found');
      const body = (await request.json()) as {
        name?: string;
        description?: string | null;
        version: number;
      };
      if (body.version !== g.version) return problem(409, 'version-conflict');
      if (body.name !== undefined) g.name = body.name.trim();
      if (body.description !== undefined) {
        if (body.description) g.description = body.description;
        else delete g.description;
      }
      g.version++;
      const { flags: _f, ...group } = g;
      void _f;
      return HttpResponse.json(group);
    }),
    http.delete('/api/v1/admin/groups/:id', async ({ request, params }) => {
      const path = await log(request);
      const fail = failing('DELETE', path);
      if (fail) return fail;
      const before = api.groups.length;
      api.groups = api.groups.filter((g) => g.id !== params.id);
      return api.groups.length < before
        ? new HttpResponse(null, { status: 204 })
        : problem(404, 'not-found');
    }),
    http.post('/api/v1/admin/groups/:id/flags', async ({ request, params }) => {
      const path = await log(request);
      const fail = failing('POST', path);
      if (fail) return fail;
      const g = api.groups.find((x) => x.id === params.id);
      if (!g) return problem(404, 'not-found');
      const body = (await request.json()) as {
        key: string;
        description?: string | null;
        enabled?: boolean;
      };
      if (g.flags.some((f) => f.key === body.key)) return problem(409, 'duplicate-key');
      const f = makeFlag(g, body.key, body.enabled ?? false, body.description ?? undefined);
      g.flags.push(f);
      return HttpResponse.json(f, { status: 201 });
    }),
    http.patch('/api/v1/admin/flags/:id', async ({ request, params }) => {
      const path = await log(request);
      const fail = failing('PATCH', path);
      if (fail) return fail;
      const found = findFlag(String(params.id));
      if (!found) return problem(404, 'not-found');
      const body = (await request.json()) as {
        description?: string | null;
        enabled?: boolean;
        version: number;
      };
      if (body.version !== found.f.version) return problem(409, 'version-conflict');
      if (body.description !== undefined) {
        if (body.description) found.f.description = body.description;
        else delete found.f.description;
      }
      if (body.enabled !== undefined) found.f.enabled = body.enabled;
      found.f.version++;
      return HttpResponse.json(found.f);
    }),
    http.post('/api/v1/admin/flags/:id/toggle', async ({ request, params }) => {
      const path = await log(request);
      if (api.toggleDelay) await delay(api.toggleDelay);
      const fail = failing('POST', path);
      if (fail) return fail;
      const found = findFlag(String(params.id));
      if (!found) return problem(404, 'not-found');
      const body = (await request.json()) as { enabled: boolean };
      if (found.f.enabled !== body.enabled) {
        found.f.enabled = body.enabled;
        found.f.version++;
      }
      return HttpResponse.json(found.f);
    }),
    http.delete('/api/v1/admin/flags/:id', async ({ request, params }) => {
      const path = await log(request);
      const fail = failing('DELETE', path);
      if (fail) return fail;
      const found = findFlag(String(params.id));
      if (!found) return problem(404, 'not-found');
      found.g.flags = found.g.flags.filter((f) => f.id !== found.f.id);
      return new HttpResponse(null, { status: 204 });
    }),
  ];
  return { api, handlers };
}
