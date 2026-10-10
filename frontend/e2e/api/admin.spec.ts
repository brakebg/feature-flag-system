import { API_URL, keyFor } from '../support/api';
import { expect, test } from '../support/fixtures';

type AuditEvent = { action: string; actor: string; targetKey: string };

test.describe('admin API (spec 4.3, 6, 9.2)', () => {
  test('[AC-AUD-1] created/updated by the signed-in user, body fields ignored, one audit event per change, none for no-ops', async ({
    request,
    api,
    cleanup,
  }, info) => {
    const headers = { Authorization: `Bearer ${api.token}` };
    const key = keyFor(info, 'who');
    const created = await request.post(`${API_URL}/api/v1/admin/groups`, {
      headers,
      data: { key, name: 'Who', createdBy: 'mallory', updatedBy: 'mallory' },
    });
    expect(created.status()).toBe(201);
    const group = await created.json();
    expect(group).toMatchObject({ createdBy: 'admin', updatedBy: 'admin', version: 0 });
    cleanup.groups.push(group.id);
    const flagRes = await request.post(`${API_URL}/api/v1/admin/groups/${group.id}/flags`, {
      headers,
      data: { key: 'feature', enabled: false, createdBy: 'mallory' },
    });
    const flag = await flagRes.json();
    expect(flag).toMatchObject({ createdBy: 'admin', updatedBy: 'admin' });

    const toggled = await request.post(`${API_URL}/api/v1/admin/flags/${flag.id}/toggle`, {
      headers,
      data: { enabled: true },
    });
    expect(await toggled.json()).toMatchObject({
      createdBy: 'admin',
      updatedBy: 'admin',
      enabled: true,
    });
    // No-op toggle and no-op update: no audit event.
    await request.post(`${API_URL}/api/v1/admin/flags/${flag.id}/toggle`, {
      headers,
      data: { enabled: true },
    });
    const noop = await request.patch(`${API_URL}/api/v1/admin/groups/${group.id}`, {
      headers,
      data: { name: 'Who', version: 0 },
    });
    expect(noop.status()).toBe(200);
    const patched = await request.patch(`${API_URL}/api/v1/admin/groups/${group.id}`, {
      headers,
      data: { name: 'Who else', version: 0, updatedBy: 'mallory' },
    });
    expect(await patched.json()).toMatchObject({
      createdBy: 'admin',
      updatedBy: 'admin',
      version: 1,
    });

    const audit = await request.get(`${API_URL}/api/v1/admin/audit?targetKey=${key}&size=50`, {
      headers,
    });
    const events = (await audit.json()).content as AuditEvent[];
    expect(events.map((e) => e.action)).toEqual([
      'GROUP_UPDATED',
      'FLAG_TOGGLED',
      'FLAG_CREATED',
      'GROUP_CREATED',
    ]);
    expect(events.every((e) => e.actor === 'admin')).toBe(true);

    const del = await request.delete(`${API_URL}/api/v1/admin/groups/${group.id}`, { headers });
    expect(del.status()).toBe(204);
  });

  test('the 501st flag in a group is rejected with 409 limit-reached; deleting one frees the slot (9.2)', async ({
    request,
    api,
  }, info) => {
    test.setTimeout(120_000);
    const headers = { Authorization: `Bearer ${api.token}` };
    const group = await api.createGroup(keyFor(info, 'limit'), 'Limit');
    const url = `${API_URL}/api/v1/admin/groups/${group.id}/flags`;
    // D-031: writes into one group serialize on its row lock while holding a DB connection, so a
    // small batch keeps the pool free for the tests running in parallel.
    for (let start = 0; start < 500; start += 4) {
      const batch = Array.from({ length: 4 }, (_, i) =>
        request.post(url, { headers, data: { key: `f-${start + i}` } }),
      );
      for (const res of await Promise.all(batch)) expect(res.status()).toBe(201);
    }
    const extra = await request.post(url, { headers, data: { key: 'f-500' } });
    expect(extra.status()).toBe(409);
    expect((await extra.json()).type).toBe('https://featureflags.local/problems/limit-reached');
    const detail = await api.group(group.id);
    const one = detail.flags.find((f) => f.key === 'f-0');
    if (!one) throw new Error('flag f-0 missing');
    const freed = await request.delete(`${API_URL}/api/v1/admin/flags/${one.id}`, { headers });
    expect(freed.status()).toBe(204);
    expect((await request.post(url, { headers, data: { key: 'f-500' } })).status()).toBe(201);
  });

  test('a body over 65,536 bytes gets 413 payload-too-large (9.2)', async ({ request, api }) => {
    const res = await request.post(`${API_URL}/api/v1/admin/groups`, {
      headers: { Authorization: `Bearer ${api.token}`, 'Content-Type': 'application/json' },
      data: JSON.stringify({ key: 'big', name: 'x'.repeat(70_000) }),
    });
    expect(res.status()).toBe(413);
    expect((await res.json()).type).toBe('https://featureflags.local/problems/payload-too-large');
    expect(res.headers()['cache-control']).toBe('no-store'); // 10.2: any status
  });
});
