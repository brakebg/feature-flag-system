import { API_URL } from '../support/api';
import { expect, test } from '../support/fixtures';

const UI_URL = process.env.UI_URL ?? 'http://localhost:3000';
const CSP =
  "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";
const HEADERS: Record<string, string> = {
  'content-security-policy': CSP,
  'x-content-type-options': 'nosniff',
  'x-frame-options': 'DENY',
  'referrer-policy': 'no-referrer',
  'permissions-policy': 'camera=(), microphone=(), geolocation=()',
};

test.describe('operations (spec 9.3, 10.2)', () => {
  test('[AC-OPS-3] /actuator/health reports UP with the DB status', async ({ request }) => {
    const res = await request.get(`${API_URL}/actuator/health`);
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.status).toBe('UP');
    expect(body.components.db.status).toBe('UP');
  });

  test('[AC-CACHE-6] readiness is UP once the stack serves traffic', async ({ request }) => {
    const res = await request.get(`${API_URL}/actuator/health/readiness`);
    expect(res.status()).toBe(200);
    expect(await res.json()).toEqual({ status: 'UP' });
  });

  for (const path of ['/', '/groups/some-id', '/assets/missing.js', '/api/v1/evaluate/flags']) {
    test(`[AC-OPS-4] UI response ${path} carries every security header once; HSTS only with https`, async ({
      request,
    }) => {
      const plain = await request.get(`${UI_URL}${path}`);
      const all = plain.headersArray();
      for (const [name, value] of Object.entries(HEADERS)) {
        const found = all.filter((h) => h.name.toLowerCase() === name);
        expect(
          found.map((h) => h.value),
          name,
        ).toEqual([value]);
      }
      expect(all.filter((h) => h.name.toLowerCase() === 'strict-transport-security')).toEqual([]);

      const https = await request.get(`${UI_URL}${path}`, {
        headers: { 'X-Forwarded-Proto': 'https' },
      });
      const hsts = https
        .headersArray()
        .filter((h) => h.name.toLowerCase() === 'strict-transport-security');
      expect(hsts.map((h) => h.value)).toEqual(['max-age=31536000; includeSubDomains']);
    });
  }
});
