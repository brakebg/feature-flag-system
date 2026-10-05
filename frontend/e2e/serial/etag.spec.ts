import { API_URL, clientToken, keyFor } from '../support/api';
import { expect, test } from '../support/fixtures';

// Spec 11.5: tests that depend on the global revision run alone, after the parallel projects.
test('[AC-EVAL-6] If-None-Match gives 304 until a write that changes data; failed and no-op writes keep the ETag', async ({
  request,
  api,
}, info) => {
  const client = { Authorization: `Bearer ${await clientToken(request)}` };
  const admin = { Authorization: `Bearer ${api.token}` };
  const group = await api.createGroup(keyFor(info, 'etag'), 'ETag');
  const flag = await api.createFlag(group.id, 'feature', false);
  const urls = [
    `${API_URL}/api/v1/evaluate/flags`,
    `${API_URL}/api/v1/evaluate/groups/${group.key}`,
    `${API_URL}/api/v1/evaluate/flags/${group.key}/feature`,
  ];

  const first = await request.get(urls[0], { headers: client });
  expect(first.status()).toBe(200);
  const etag = first.headers()['etag'];
  expect(etag).toMatch(/^"\d+"$/);
  expect(first.headers()['cache-control']).toBe('no-cache');
  expect(`"${(await first.json()).revision}"`).toBe(etag);

  const notModified = async (tag: string) => {
    for (const url of urls) {
      const res = await request.get(url, { headers: { ...client, 'If-None-Match': tag } });
      expect(res.status(), url).toBe(304);
      expect(res.headers()['etag']).toBe(tag);
      expect(await res.body()).toHaveLength(0);
    }
  };
  await notModified(etag);

  // A no-op toggle and a failed write (409 stale version) keep the ETag.
  await api.toggle(flag.id, false);
  const stale = await request.patch(`${API_URL}/api/v1/admin/flags/${flag.id}`, {
    headers: admin,
    data: { description: 'x', version: 99 },
  });
  expect(stale.status()).toBe(409);
  await notModified(etag);

  // A real change gives 200 and a new ETag.
  await api.toggle(flag.id, true);
  for (const url of urls) {
    const res = await request.get(url, { headers: { ...client, 'If-None-Match': etag } });
    expect(res.status(), url).toBe(200);
    expect(res.headers()['etag']).not.toBe(etag);
  }
});
