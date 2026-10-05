import { expect, test } from '@playwright/test';

// Runs in its own stack (LIMITS_*) started with FF_REQUIRE_HTTPS=true, 1 worker (spec 11.5).
const API = process.env.LIMITS_API_URL ?? 'http://localhost:8180';
const HTTPS = { 'X-Forwarded-Proto': 'https' };
const basic = `Basic ${Buffer.from('order-service:order-service-dev-secret').toString('base64')}`;

async function login(request: import('@playwright/test').APIRequestContext) {
  const res = await request.post(`${API}/api/v1/auth/login`, {
    headers: HTTPS,
    data: { username: 'admin', password: 'admin123' },
  });
  expect(res.status()).toBe(200);
  return ((await res.json()) as { accessToken: string }).accessToken;
}

test.describe.configure({ mode: 'serial' });

test('[AC-OPS-4] with FF_REQUIRE_HTTPS=true, login and token over http get 403 https-required; https succeeds', async ({
  request,
}) => {
  const plain: Record<string, string>[] = [{}, { 'X-Forwarded-Proto': 'http' }];
  for (const headers of plain) {
    const loginRes = await request.post(`${API}/api/v1/auth/login`, {
      headers,
      data: { username: 'admin', password: 'admin123' },
    });
    expect(loginRes.status()).toBe(403);
    expect((await loginRes.json()).type).toBe('https://featureflags.local/problems/https-required');
    const tokenRes = await request.post(`${API}/api/v1/auth/token`, {
      headers: { ...headers, Authorization: basic },
      form: { grant_type: 'client_credentials' },
    });
    expect(tokenRes.status()).toBe(403);
    expect((await tokenRes.json()).type).toBe('https://featureflags.local/problems/https-required');
  }
  await login(request);
  const token = await request.post(`${API}/api/v1/auth/token`, {
    headers: { ...HTTPS, Authorization: basic },
    form: { grant_type: 'client_credentials' },
  });
  expect(token.status()).toBe(200);
});

test('the 1,001st group is rejected with 409 limit-reached; deleting one frees its slot (9.2)', async ({
  request,
}, info) => {
  test.setTimeout(300_000);
  const headers = { Authorization: `Bearer ${await login(request)}` };
  const url = `${API}/api/v1/admin/groups`;
  const prefix = `lim-r${info.repeatEachIndex}`;
  const existing = ((await (await request.get(url, { headers })).json()) as unknown[]).length;
  const ids: string[] = [];
  try {
    for (let i = existing; i < 1000; i += 25) {
      const batch = Array.from({ length: Math.min(25, 1000 - i) }, (_, j) =>
        request.post(url, { headers, data: { key: `${prefix}-${i + j}`, name: 'Limit' } }),
      );
      for (const res of await Promise.all(batch)) {
        expect(res.status()).toBe(201);
        ids.push(((await res.json()) as { id: string }).id);
      }
    }
    const extra = await request.post(url, {
      headers,
      data: { key: `${prefix}-extra`, name: 'Extra' },
    });
    expect(extra.status()).toBe(409);
    expect((await extra.json()).type).toBe('https://featureflags.local/problems/limit-reached');
    expect((await request.delete(`${url}/${ids.pop()}`, { headers })).status()).toBe(204);
    const again = await request.post(url, {
      headers,
      data: { key: `${prefix}-extra`, name: 'Extra' },
    });
    expect(again.status()).toBe(201);
    ids.push(((await again.json()) as { id: string }).id);
  } finally {
    for (let i = 0; i < ids.length; i += 25) {
      await Promise.all(
        ids.slice(i, i + 25).map((id) => request.delete(`${url}/${id}`, { headers })),
      );
    }
  }
});
