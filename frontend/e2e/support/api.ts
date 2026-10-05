import { expect, type APIRequestContext, type TestInfo } from '@playwright/test';

/** Spec 11.5: tests create their own data through the Admin API and delete it afterwards. */
export const API_URL = process.env.API_URL ?? 'http://localhost:8080';

export interface AdminApi {
  token: string;
  createGroup(
    key: string,
    name: string,
    description?: string,
  ): Promise<{ id: string; key: string }>;
  createFlag(
    groupId: string,
    key: string,
    enabled?: boolean,
    description?: string,
  ): Promise<{ id: string; fullKey: string }>;
  toggle(flagId: string, enabled: boolean): Promise<void>;
  patchGroup(groupId: string, body: object): Promise<void>;
  group(groupId: string): Promise<{
    id: string;
    version: number;
    flags: { id: string; key: string; version: number }[];
  }>;
  groups(): Promise<{ id: string; key: string }[]>;
  deleteGroup(groupId: string): Promise<void>;
}

export async function login(request: APIRequestContext, base = API_URL): Promise<string> {
  const res = await request.post(`${base}/api/v1/auth/login`, {
    data: { username: 'admin', password: 'admin123' },
  });
  expect(res.status()).toBe(200);
  return ((await res.json()) as { accessToken: string }).accessToken;
}

export async function adminApi(request: APIRequestContext, base = API_URL): Promise<AdminApi> {
  const token = await login(request, base);
  const headers = { Authorization: `Bearer ${token}` };
  const ok = async (res: { ok(): boolean; status(): number; text(): Promise<string> }) => {
    if (!res.ok()) throw new Error(`Admin API ${res.status()}: ${await res.text()}`);
  };
  return {
    token,
    async createGroup(key, name, description) {
      const res = await request.post(`${base}/api/v1/admin/groups`, {
        headers,
        data: { key, name, description },
      });
      await ok(res);
      return (await res.json()) as { id: string; key: string };
    },
    async createFlag(groupId, key, enabled = false, description) {
      const res = await request.post(`${base}/api/v1/admin/groups/${groupId}/flags`, {
        headers,
        data: { key, enabled, description },
      });
      await ok(res);
      return (await res.json()) as { id: string; fullKey: string };
    },
    async toggle(flagId, enabled) {
      await ok(
        await request.post(`${base}/api/v1/admin/flags/${flagId}/toggle`, {
          headers,
          data: { enabled },
        }),
      );
    },
    async patchGroup(groupId, body) {
      await ok(
        await request.patch(`${base}/api/v1/admin/groups/${groupId}`, { headers, data: body }),
      );
    },
    async group(groupId) {
      const res = await request.get(`${base}/api/v1/admin/groups/${groupId}`, { headers });
      await ok(res);
      return (await res.json()) as {
        id: string;
        version: number;
        flags: { id: string; key: string; version: number }[];
      };
    },
    async groups() {
      const res = await request.get(`${base}/api/v1/admin/groups`, { headers });
      await ok(res);
      return (await res.json()) as { id: string; key: string }[];
    },
    async deleteGroup(groupId) {
      const res = await request.delete(`${base}/api/v1/admin/groups/${groupId}`, { headers });
      if (res.status() !== 204 && res.status() !== 404) await ok(res);
    },
  };
}

/**
 * Unique, deterministic keys per test (spec 11.5: `e2e-<test-id>-...`; 12.4: no unseeded
 * random data). The test id differs per test and project; the repeat index separates
 * --repeat-each runs.
 */
export function keyFor(info: TestInfo, name: string): string {
  return `e2e-${info.testId.slice(0, 8)}-r${info.repeatEachIndex}-${name}`
    .toLowerCase()
    .replace(/[^a-z0-9-]/g, '-')
    .slice(0, 50);
}
