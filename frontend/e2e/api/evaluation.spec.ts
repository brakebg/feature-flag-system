import { API_URL, adminApi, clientToken, keyFor } from '../support/api';
import { expect, test } from '../support/fixtures';
import { expiredClientToken } from '../support/jwt';

const basic = (id: string, secret: string) =>
  `Basic ${Buffer.from(`${id}:${secret}`).toString('base64')}`;
const TOKEN_URL = `${API_URL}/api/v1/auth/token`;

function claims(jwt: string): Record<string, unknown> {
  return JSON.parse(Buffer.from(jwt.split('.')[1], 'base64url').toString('utf8'));
}

test.describe('evaluation API and client tokens (spec 5.5, 7)', () => {
  test('[AC-EVAL-1] client credentials give a bearer JWT with scope flags:read and expires_in 900', async ({
    request,
  }) => {
    const res = await request.post(TOKEN_URL, {
      headers: { Authorization: basic('order-service', 'order-service-dev-secret') },
      form: { grant_type: 'client_credentials', scope: 'flags:read' },
    });
    expect(res.status()).toBe(200);
    expect(res.headers()['cache-control']).toBe('no-store');
    const body = await res.json();
    expect(body).toEqual({
      access_token: expect.any(String),
      token_type: 'Bearer',
      expires_in: 900,
      scope: 'flags:read',
    });
    const c = claims(body.access_token);
    expect(c).toMatchObject({
      sub: 'order-service',
      scope: 'flags:read',
      aud: ['feature-flag-service'],
      iss: 'feature-flag-service',
    });
    expect((c.exp as number) - (c.iat as number)).toBe(900);
  });

  test('[AC-EVAL-2] wrong secret 401 invalid_client; unknown grant 400; admin scope 400 invalid_scope', async ({
    request,
  }) => {
    const wrong = await request.post(TOKEN_URL, {
      headers: { Authorization: basic('order-service', 'wrong') },
      form: { grant_type: 'client_credentials' },
    });
    expect(wrong.status()).toBe(401);
    expect(await wrong.json()).toEqual({ error: 'invalid_client' });
    expect(wrong.headers()['www-authenticate']).toMatch(/^Basic/);

    const grant = await request.post(TOKEN_URL, {
      headers: { Authorization: basic('order-service', 'order-service-dev-secret') },
      form: { grant_type: 'password' },
    });
    expect(grant.status()).toBe(400);
    expect(await grant.json()).toEqual({ error: 'unsupported_grant_type' });

    const scope = await request.post(TOKEN_URL, {
      headers: { Authorization: basic('order-service', 'order-service-dev-secret') },
      form: { grant_type: 'client_credentials', scope: 'admin' },
    });
    expect(scope.status()).toBe(400);
    expect(await scope.json()).toEqual({ error: 'invalid_scope' });
  });

  test('[AC-EVAL-3] evaluation: no token 401, expired 401, admin token 403, client token 200', async ({
    request,
  }) => {
    const url = `${API_URL}/api/v1/evaluate/flags`;
    expect((await request.get(url)).status()).toBe(401);
    const expired = await request.get(url, {
      headers: { Authorization: `Bearer ${expiredClientToken()}` },
    });
    expect(expired.status()).toBe(401);
    const admin = await adminApi(request);
    expect(
      (await request.get(url, { headers: { Authorization: `Bearer ${admin.token}` } })).status(),
    ).toBe(403);
    const ok = await request.get(url, {
      headers: { Authorization: `Bearer ${await clientToken(request)}` },
    });
    expect(ok.status()).toBe(200);
    const body = await ok.json();
    expect(typeof body.revision).toBe('number');
    expect(typeof body.flags).toBe('object');
  });

  test('[AC-EVAL-4] a client token on any admin endpoint gets 403', async ({ request }) => {
    const headers = { Authorization: `Bearer ${await clientToken(request)}` };
    const calls = [
      request.get(`${API_URL}/api/v1/admin/groups`, { headers }),
      request.get(`${API_URL}/api/v1/admin/audit`, { headers }),
      request.post(`${API_URL}/api/v1/admin/groups`, {
        headers,
        data: { key: 'nope', name: 'No' },
      }),
      request.delete(`${API_URL}/api/v1/admin/flags/0191f0c2-0000-7000-8000-000000000001`, {
        headers,
      }),
    ];
    for (const res of await Promise.all(calls)) {
      expect(res.status()).toBe(403);
      expect((await res.json()).type).toBe('https://featureflags.local/problems/forbidden');
    }
  });

  test('[AC-EVAL-7] unknown group or flag returns 404', async ({ request, api }, info) => {
    const headers = { Authorization: `Bearer ${await clientToken(request)}` };
    const key = keyFor(info, 'known');
    await api.createGroup(key, 'Known');
    const urls = [
      `${API_URL}/api/v1/evaluate/groups/${keyFor(info, 'missing')}`,
      `${API_URL}/api/v1/evaluate/flags/${keyFor(info, 'missing')}/flag`,
      `${API_URL}/api/v1/evaluate/flags/${key}/no-such-flag`,
      `${API_URL}/api/v1/evaluate/groups/Not_A_Key`,
    ];
    for (const url of urls) expect((await request.get(url, { headers })).status(), url).toBe(404);
    const group = await request.get(`${API_URL}/api/v1/evaluate/groups/${key}`, { headers });
    expect(group.status()).toBe(200);
    expect((await group.json()).flags).toEqual({});
  });
});
