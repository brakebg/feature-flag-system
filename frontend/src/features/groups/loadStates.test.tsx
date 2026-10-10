import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { TOKEN_KEY } from '../../auth/token';
import { fakeApi, makeGroup, type FakeApi } from '../../test/fakeApi';
import { fakeToken, renderApp } from '../../test/render';
import { server } from '../../test/server';

const fail500 = () =>
  HttpResponse.json(
    { type: 'https://featureflags.local/problems/internal', status: 500 },
    { status: 500 },
  );

beforeEach(() => {
  sessionStorage.setItem(TOKEN_KEY, fakeToken('admin', 3600));
});

describe('load states (spec 8.4, 8.6; D-029)', () => {
  it('the "No groups yet" empty state is not shown while the list loads or when it fails', async () => {
    server.use(
      http.get('/api/v1/admin/groups', async () => {
        await delay(50);
        return fail500();
      }),
    );
    renderApp('/groups');
    expect(screen.queryByText('No groups yet')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Create your first group' })).toBeNull();
    expect((await screen.findByRole('alert')).textContent).toBe('Could not load groups');
    expect(screen.queryByText('No groups yet')).toBeNull();
  });

  it('a failed group detail (500) shows an error, not the "select a group" placeholder', async () => {
    const g = makeGroup('orders', 'Orders');
    const fake = fakeApi([g]);
    server.use(...fake.handlers);
    fake.api.failNext('GET', `/groups/${g.id}`, 500, 'internal');
    renderApp(`/groups/${g.id}`);
    expect((await screen.findByRole('alert')).textContent).toBe('Could not load the group');
    expect(screen.queryByText('Select a group or create one')).toBeNull();
  });

  it('a failed audit request shows an error', async () => {
    server.use(http.get('/api/v1/admin/audit', fail500));
    renderApp('/audit');
    expect((await screen.findByRole('alert')).textContent).toBe('Could not load audit events');
  });
});

describe('toggle robustness (spec 8.5)', () => {
  let api: FakeApi;
  function start(groups: ReturnType<typeof makeGroup>[]) {
    const fake = fakeApi(groups);
    api = fake.api;
    server.use(...fake.handlers);
  }

  it('[AC-FLAG-4] the error toast still shows when the user leaves the group before the toggle fails', async () => {
    const orders = makeGroup('orders', 'Orders', [['new-checkout', false]]);
    const payments = makeGroup('payments', 'Payments');
    start([orders, payments]);
    api.toggleDelay = 100;
    renderApp(`/groups/${orders.id}`);
    const user = userEvent.setup();
    api.failNext('POST', '/toggle', 500, 'internal');
    await user.click(await screen.findByRole('switch', { name: 'Toggle orders.new-checkout' }));
    await user.click(
      within(screen.getByRole('navigation', { name: 'Flag groups' })).getByRole('button', {
        name: /Payments/,
      }),
    );
    expect((await screen.findByRole('alert')).textContent).toBe(
      'Could not update flag orders.new-checkout',
    );
  });

  it('[AC-FLAG-4] a failed toggle does not undo a newer successful toggle of another flag', async () => {
    const g = makeGroup('orders', 'Orders', [
      ['a-flag', false],
      ['b-flag', false],
    ]);
    start([g]);
    // A is slow and fails; B is fast and succeeds, so B's success lands before A's failure.
    server.use(
      http.post(`/api/v1/admin/flags/${g.flags[0].id}/toggle`, async () => {
        await delay(150);
        return fail500();
      }),
    );
    renderApp(`/groups/${g.id}`);
    const user = userEvent.setup();
    const a = await screen.findByRole('switch', { name: 'Toggle orders.a-flag' });
    const b = screen.getByRole('switch', { name: 'Toggle orders.b-flag' });
    await user.click(a);
    await user.click(b);
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    await waitFor(() => expect(a).toHaveAttribute('aria-checked', 'false'));
    await waitFor(() => expect(b).toBeEnabled());
    expect(b).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByText('1 of 2 flags on')).toBeInTheDocument();
  });
});
