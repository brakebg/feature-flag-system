import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import type { AuditEvent } from '../../api/types';
import { TOKEN_KEY } from '../../auth/token';
import { fakeToken, renderApp } from '../../test/render';
import { server } from '../../test/server';
import { auditDetails } from './auditFormat';

beforeEach(() => {
  sessionStorage.setItem(TOKEN_KEY, fakeToken('admin', 3600));
});

function event(
  id: number,
  action: string,
  targetKey: string,
  details?: Record<string, unknown>,
): AuditEvent {
  return {
    id,
    occurredAt: new Date(Date.UTC(2026, 9, 1, 10, 0, 0) + id * 60_000).toISOString(),
    actor: 'admin',
    action,
    targetKey,
    ...(details ? { details } : {}),
  };
}

describe('audit details text (spec 8.6)', () => {
  it.each([
    [event(1, 'FLAG_TOGGLED', 'o.a', { enabled: { from: false, to: true } }), 'false → true'],
    [event(2, 'FLAG_TOGGLED', 'o.a', { enabled: { from: true, to: false } }), 'true → false'],
    [event(3, 'FLAG_CREATED', 'o.a', { enabled: false }), 'Created off'],
    [event(4, 'FLAG_CREATED', 'o.a', { enabled: true }), 'Created on'],
    [event(5, 'FLAG_DELETED', 'o.a', { enabled: true }), 'Was on'],
    [event(6, 'FLAG_DELETED', 'o.a', { enabled: false }), 'Was off'],
    [event(7, 'GROUP_CREATED', 'o', { name: 'Orders' }), 'Name: Orders'],
    [event(8, 'GROUP_UPDATED', 'o', { name: { from: 'a', to: 'b' } }), 'Name: “a” → “b”'],
    [
      event(9, 'GROUP_UPDATED', 'o', {
        description: { from: null, to: 'x' },
        name: { from: 'a', to: 'b' },
      }),
      'Name: “a” → “b”; Description: “” → “x”',
    ],
    [
      event(10, 'FLAG_UPDATED', 'o.a', {
        enabled: { from: false, to: true },
        description: { from: 'a', to: 'b' },
      }),
      'Description: “a” → “b”; Enabled: false → true',
    ],
    [event(11, 'GROUP_DELETED', 'o', { deletedFlags: [] }), '0 flags deleted'],
    [event(12, 'GROUP_DELETED', 'o', { deletedFlags: ['o.a'] }), '1 flag deleted: o.a'],
    [
      event(13, 'GROUP_DELETED', 'o', { deletedFlags: ['o.a', 'o.b'] }),
      '2 flags deleted: o.a, o.b',
    ],
  ])('%#: %s', (e, text) => {
    expect(auditDetails(e)).toBe(text);
  });
});

describe('audit page (spec 8.6)', () => {
  it('[AC-AUD-2] lists events newest first with labels, filters by target key, and loads more', async () => {
    const all = Array.from({ length: 60 }, (_, i) =>
      event(60 - i, 'FLAG_TOGGLED', i < 55 ? `orders.f${i}` : `pay.f${i}`, {
        enabled: { from: false, to: true },
      }),
    );
    const requests: string[] = [];
    server.use(
      http.get('/api/v1/admin/audit', ({ request }) => {
        const url = new URL(request.url);
        requests.push(url.search);
        const page = Number(url.searchParams.get('page'));
        const size = Number(url.searchParams.get('size'));
        const prefix = url.searchParams.get('targetKey') ?? '';
        const matching = all.filter((e) => e.targetKey.startsWith(prefix));
        return HttpResponse.json({
          content: matching.slice(page * size, page * size + size),
          page: {
            size,
            number: page,
            totalElements: matching.length,
            totalPages: Math.ceil(matching.length / size),
          },
        });
      }),
    );
    renderApp('/audit');
    const table = await screen.findByRole('table', { name: 'Audit events' });
    expect(
      within(table)
        .getAllByRole('columnheader')
        .map((h) => h.textContent),
    ).toEqual(['Time', 'Actor', 'Action', 'Target', 'Details']);
    await waitFor(() => expect(within(table).getAllByRole('row')).toHaveLength(51));
    const firstRow = within(table).getAllByRole('row')[1];
    expect(firstRow).toHaveTextContent('orders.f0');
    expect(within(firstRow).getByText('Flag toggled')).toBeInTheDocument();
    expect(firstRow).toHaveTextContent('false → true');
    expect(requests[0]).toBe('?page=0&size=50');

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Load more' }));
    await waitFor(() => expect(within(table).getAllByRole('row')).toHaveLength(61));
    expect(screen.queryByRole('button', { name: 'Load more' })).toBeNull();
    expect(requests[1]).toBe('?page=1&size=50');

    await user.type(screen.getByLabelText('Filter by target key'), 'pay.');
    await waitFor(() => expect(within(table).getAllByRole('row')).toHaveLength(6));
    expect(requests.at(-1)).toBe('?page=0&size=50&targetKey=pay.');
    expect(within(table).queryByText('orders.f0')).toBeNull();
  });

  it('all action labels', async () => {
    const actions = [
      'GROUP_CREATED',
      'GROUP_UPDATED',
      'GROUP_DELETED',
      'FLAG_CREATED',
      'FLAG_UPDATED',
      'FLAG_TOGGLED',
      'FLAG_DELETED',
    ];
    server.use(
      http.get('/api/v1/admin/audit', () =>
        HttpResponse.json({
          content: actions.map((a, i) => event(i + 1, a, 't')),
          page: { size: 50, number: 0, totalElements: 7, totalPages: 1 },
        }),
      ),
    );
    renderApp('/audit');
    for (const label of [
      'Group created',
      'Group updated',
      'Group deleted',
      'Flag created',
      'Flag updated',
      'Flag toggled',
      'Flag deleted',
    ]) {
      expect(await screen.findByText(label)).toBeInTheDocument();
    }
  });

  it('[AC-AUTH-4] a 401 on a real screen clears the token, the cached data and goes to /login?expired=1', async () => {
    server.use(
      http.get('/api/v1/admin/audit', () => HttpResponse.json({ status: 401 }, { status: 401 })),
    );
    const { client } = renderApp('/audit');
    await waitFor(() =>
      expect(screen.getByTestId('location').textContent).toBe('/login?expired=1'),
    );
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(client.getQueryCache().getAll()).toHaveLength(0);
  });

  it('[AC-AUD-2] FF-2: events that move to the next page because of new events are shown once', async () => {
    let all = Array.from({ length: 60 }, (_, i) =>
      event(60 - i, 'FLAG_TOGGLED', `orders.f${60 - i}`),
    );
    server.use(
      http.get('/api/v1/admin/audit', ({ request }) => {
        const url = new URL(request.url);
        const page = Number(url.searchParams.get('page'));
        const size = Number(url.searchParams.get('size'));
        return HttpResponse.json({
          content: all.slice(page * size, page * size + size),
          page: {
            size,
            number: page,
            totalElements: all.length,
            totalPages: Math.ceil(all.length / size),
          },
        });
      }),
    );
    renderApp('/audit');
    const table = await screen.findByRole('table', { name: 'Audit events' });
    await waitFor(() => expect(within(table).getAllByRole('row')).toHaveLength(51));
    // Three new events arrive: the old events 13, 12, 11 move from page 0 to page 1.
    all = [63, 62, 61].map((id) => event(id, 'FLAG_TOGGLED', `orders.f${id}`)).concat(all);
    await userEvent.setup().click(screen.getByRole('button', { name: 'Load more' }));
    await waitFor(() => expect(within(table).getAllByRole('row')).toHaveLength(61));
    const targets = within(table)
      .getAllByRole('row')
      .slice(1)
      .map((r) => r.textContent?.match(/orders\.f\d+/)?.[0]);
    expect(new Set(targets).size).toBe(60);
  });
});
