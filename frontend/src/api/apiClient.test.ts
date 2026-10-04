import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { setToken, TOKEN_KEY } from '../auth/token';
import { server } from '../test/server';
import { ApiError } from './ApiError';
import { request, setUnauthorizedHandler } from './apiClient';

describe('apiClient (spec 8.7)', () => {
  it('sends the bearer token and JSON, and parses the JSON body', async () => {
    setToken('abc.def.ghi');
    let auth: string | null = null;
    let contentType: string | null = null;
    server.use(
      http.post('/api/v1/admin/groups', async ({ request: req }) => {
        auth = req.headers.get('Authorization');
        contentType = req.headers.get('Content-Type');
        return HttpResponse.json({ id: '1', key: 'orders' }, { status: 201 });
      }),
    );
    const body = await request<{ key: string }>('/v1/admin/groups', {
      method: 'POST',
      body: { key: 'orders' },
    });
    expect(body.key).toBe('orders');
    expect(auth).toBe('Bearer abc.def.ghi');
    expect(contentType).toBe('application/json');
  });

  it('204 resolves without a body', async () => {
    server.use(http.delete('/api/v1/admin/flags/1', () => new HttpResponse(null, { status: 204 })));
    await expect(request('/v1/admin/flags/1', { method: 'DELETE' })).resolves.toBeUndefined();
  });

  it('turns a problem detail into an ApiError with field errors', async () => {
    server.use(
      http.post('/api/v1/admin/groups', () =>
        HttpResponse.json(
          {
            type: 'https://featureflags.local/problems/validation',
            status: 400,
            detail: 'Request has 1 invalid field',
            errors: [{ field: 'key', message: 'must match' }, { bad: true }],
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    const error = await request('/v1/admin/groups', { method: 'POST', body: {} }).catch(
      (e: unknown) => e,
    );
    expect(error).toBeInstanceOf(ApiError);
    const e = error as ApiError;
    expect(e.status).toBe(400);
    expect(e.kind).toBe('validation');
    expect(e.detail).toBe('Request has 1 invalid field');
    expect(e.errors).toEqual([{ field: 'key', message: 'must match' }]);
    expect(e.isNetwork).toBe(false);
  });

  it('a non-JSON error keeps status and headers', async () => {
    server.use(
      http.get(
        '/api/v1/admin/audit',
        () => new HttpResponse('busy', { status: 429, headers: { 'Retry-After': '7' } }),
      ),
    );
    const e = (await request('/v1/admin/audit').catch((x: unknown) => x)) as ApiError;
    expect(e.status).toBe(429);
    expect(e.kind).toBe('');
    expect(e.headers?.get('Retry-After')).toBe('7');
  });

  it('a network failure is status 0', async () => {
    server.use(http.get('/api/v1/admin/audit', () => HttpResponse.error()));
    const e = (await request('/v1/admin/audit').catch((x: unknown) => x)) as ApiError;
    expect(e.isNetwork).toBe(true);
    expect(e.message).toBe('Cannot reach server');
  });

  it('a 401 clears the token and calls the unauthorized handler; auth: false does not', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    setToken('t.t.t');
    server.use(
      http.get('/api/v1/admin/groups', () => HttpResponse.json({ status: 401 }, { status: 401 })),
      http.post('/api/v1/auth/login', () => HttpResponse.json({ status: 401 }, { status: 401 })),
    );
    await request('/v1/admin/groups').catch(() => undefined);
    expect(handler).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();

    setToken('t.t.t');
    await request('/v1/auth/login', { method: 'POST', body: {}, auth: false }).catch(
      () => undefined,
    );
    expect(handler).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe('t.t.t');
  });

  it('a 200 with an empty body resolves to undefined', async () => {
    server.use(http.get('/api/v1/admin/groups', () => new HttpResponse('', { status: 200 })));
    await expect(request('/v1/admin/groups')).resolves.toBeUndefined();
  });
});
