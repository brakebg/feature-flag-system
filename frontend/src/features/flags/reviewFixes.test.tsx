import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { TOKEN_KEY } from '../../auth/token';
import { fakeApi, makeGroup, type FakeApi } from '../../test/fakeApi';
import { fakeToken, renderApp } from '../../test/render';
import { server } from '../../test/server';

let api: FakeApi;
function start(groups: ReturnType<typeof makeGroup>[]) {
  const fake = fakeApi(groups);
  api = fake.api;
  server.use(...fake.handlers);
}

beforeEach(() => {
  sessionStorage.setItem(TOKEN_KEY, fakeToken('admin', 3600));
});

describe('final review fixes (spec 8.5)', () => {
  it('[AC-FLAG-3] FF-1: a refetch after one toggle does not flip another toggle that is still running', async () => {
    const g = makeGroup('orders', 'Orders', [
      ['a-flag', false],
      ['b-flag', false],
    ]);
    start([g]);
    let releaseB!: () => void;
    const bHeld = new Promise<void>((r) => (releaseB = r));
    const bId = g.flags[1].id;
    server.use(
      http.post(`/api/v1/admin/flags/${bId}/toggle`, async () => {
        await bHeld;
        g.flags[1].enabled = true;
        g.flags[1].version++;
        return HttpResponse.json(g.flags[1]);
      }),
    );
    renderApp(`/groups/${g.id}`);
    const user = userEvent.setup();
    const a = await screen.findByRole('switch', { name: 'Toggle orders.a-flag' });
    const b = screen.getByRole('switch', { name: 'Toggle orders.b-flag' });
    await user.click(b);
    await user.click(a);
    // A's toggle ends (its settle step is awaited before the switch is enabled again). The server
    // still has B off, so a refetch at this point would flip B back while B is in flight.
    await waitFor(() => expect(a).toBeEnabled());
    expect(b).toHaveAttribute('aria-checked', 'true');
    const gets = () => api.calls.filter((c) => c.method === 'GET' && c.path.endsWith(g.id)).length;
    const before = gets();
    releaseB();
    await waitFor(() => expect(b).toBeEnabled());
    // The last toggle in the group refetches it.
    expect(gets()).toBeGreaterThan(before);
    expect(b).toHaveAttribute('aria-checked', 'true');
    expect(a).toHaveAttribute('aria-checked', 'true');
  });

  it('FF-4: deleting a flag that is already gone closes the dialog, removes the row and says so', async () => {
    const g = makeGroup('orders', 'Orders', [
      ['a-flag', true],
      ['b-flag', false],
    ]);
    start([g]);
    renderApp(`/groups/${g.id}`);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Delete orders.a-flag' }));
    g.flags = g.flags.filter((f) => f.key !== 'a-flag'); // someone else deleted it
    const dialog = screen.getByRole('alertdialog', { name: 'Delete flag orders.a-flag?' });
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));
    expect((await screen.findByRole('alert')).textContent).toBe(
      'Flag orders.a-flag was already deleted',
    );
    await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull());
    await waitFor(() => expect(screen.queryByText('orders.a-flag')).toBeNull());
    expect(screen.getByText('orders.b-flag')).toBeInTheDocument();
  });

  it('FF-4: deleting a group that is already gone closes the dialog and goes to /groups', async () => {
    const g = makeGroup('orders', 'Orders');
    start([g, makeGroup('payments', 'Payments')]);
    renderApp(`/groups/${g.id}`);
    const user = userEvent.setup();
    await screen.findByRole('heading', { name: 'Orders' });
    await user.click(screen.getByRole('button', { name: 'Delete group' }));
    api.groups = api.groups.filter((x) => x.id !== g.id);
    const dialog = screen.getByRole('alertdialog');
    await user.type(within(dialog).getByLabelText('Type orders to confirm'), 'orders');
    await user.click(within(dialog).getByRole('button', { name: 'Delete group and 0 flags' }));
    expect((await screen.findByRole('alert')).textContent).toBe('Group orders was already deleted');
    await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/groups'));
    expect(
      within(screen.getByRole('navigation', { name: 'Flag groups' })).queryByText('Orders'),
    ).toBeNull();
  });

  it('FF-3: the dialog cannot be closed while its request runs, so the field error is shown', async () => {
    start([makeGroup('orders', 'Orders')]);
    server.use(
      http.post('/api/v1/admin/groups', async () => {
        await delay(100);
        return HttpResponse.json(
          { type: 'https://featureflags.local/problems/duplicate-key', status: 409, detail: 'dup' },
          { status: 409 },
        );
      }),
    );
    renderApp('/groups');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'New group' }));
    const dialog = screen.getByRole('dialog', { name: 'New group' });
    await user.type(within(dialog).getByLabelText('Name'), 'Orders');
    await user.click(within(dialog).getByRole('button', { name: 'Create group' }));
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toBeDisabled();
    expect(within(dialog).getByRole('button', { name: 'Close' })).toBeDisabled();
    await user.keyboard('{Escape}');
    expect(screen.getByRole('dialog', { name: 'New group' })).toBeInTheDocument();
    await waitFor(() =>
      expect(within(dialog).getByLabelText('Key')).toHaveAccessibleDescription(
        'Key already exists',
      ),
    );
  });
});
