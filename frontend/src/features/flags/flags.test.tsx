import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { TOKEN_KEY } from '../../auth/token';
import { fakeApi, makeFlag, makeGroup, type FakeApi } from '../../test/fakeApi';
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

const badge = (name: RegExp) =>
  within(
    within(screen.getByRole('navigation', { name: 'Flag groups' })).getByRole('button', { name }),
  ).getByText(/^\d+\/\d+$/);

describe('flags (spec 8.4, 8.5)', () => {
  it('[AC-FLAG-1] add flag new-checkout to orders, initially off, with the full key shown', async () => {
    const g = makeGroup('orders', 'Orders');
    start([g]);
    renderApp(`/groups/${g.id}`);
    const user = userEvent.setup();
    expect(await screen.findByText('No flags in this group')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Add flag' }));

    const dialog = screen.getByRole('dialog', { name: 'New flag in Orders' });
    expect(within(dialog).getByText('orders.')).toBeInTheDocument();
    const initial = within(dialog).getByRole('switch', { name: 'Initial state' });
    expect(initial).toHaveAttribute('aria-checked', 'false');
    expect(
      within(dialog).getByText('Off: services read false until you turn it on.'),
    ).toBeInTheDocument();
    await user.click(initial);
    expect(
      within(dialog).getByText('On: services read true as soon as the flag is created.'),
    ).toBeInTheDocument();
    await user.click(initial);
    await user.type(within(dialog).getByLabelText('Key'), 'new-checkout');
    await user.type(
      within(dialog).getByLabelText('Description (optional)'),
      'New one-page checkout',
    );
    await user.click(within(dialog).getByRole('button', { name: 'Create flag' }));

    const row = (await screen.findByText('orders.new-checkout')).closest(
      '[role="row"]',
    ) as HTMLElement;
    expect(within(row).getByRole('switch', { name: 'Toggle orders.new-checkout' })).toHaveAttribute(
      'aria-checked',
      'false',
    );
    expect(within(row).getByText('Off')).toBeInTheDocument();
    expect(screen.getByRole('status').textContent).toBe('Flag orders.new-checkout created');
    expect(api.calls.find((c) => c.method === 'POST')?.body).toEqual({
      key: 'new-checkout',
      description: 'New one-page checkout',
      enabled: false,
    });
  });

  it('[AC-FLAG-2] the same flag key in two groups', async () => {
    const orders = makeGroup('orders', 'Orders', [['new-checkout', false]]);
    const payments = makeGroup('payments', 'Payments');
    start([orders, payments]);
    renderApp(`/groups/${payments.id}`);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'New flag' }));
    const dialog = screen.getByRole('dialog', { name: 'New flag in Payments' });
    await user.type(within(dialog).getByLabelText('Key'), 'new-checkout');
    await user.click(within(dialog).getByRole('button', { name: 'Create flag' }));
    expect(await screen.findByText('payments.new-checkout')).toBeInTheDocument();
  });

  it('duplicate flag key shows "Key already exists" on Key', async () => {
    const g = makeGroup('orders', 'Orders', [['new-checkout', false]]);
    start([g]);
    renderApp(`/groups/${g.id}`);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'New flag' }));
    const dialog = screen.getByRole('dialog');
    await user.type(within(dialog).getByLabelText('Key'), 'new-checkout');
    await user.click(within(dialog).getByRole('button', { name: 'Create flag' }));
    const key = within(dialog).getByLabelText('Key');
    await waitFor(() => expect(key).toHaveAttribute('aria-invalid', 'true'));
    // The error text, then the help text of the Key field (both linked by aria-describedby).
    expect(key).toHaveAccessibleDescription(
      'Key already exists Lowercase letters, digits and hyphens, 2 to 50 characters. Cannot be changed later.',
    );
    expect(screen.getByRole('dialog', { name: 'New flag in Orders' })).toBeInTheDocument();
  });

  it.each(['Orders', '1abc', 'a', 'has space'])(
    '[AC-GRP-3] flag key %s is rejected in the New flag dialog without a request',
    async (bad) => {
      const g = makeGroup('orders', 'Orders');
      start([g]);
      renderApp(`/groups/${g.id}`);
      const user = userEvent.setup();
      await user.click(await screen.findByRole('button', { name: 'New flag' }));
      const dialog = screen.getByRole('dialog', { name: 'New flag in Orders' });
      const key = within(dialog).getByLabelText('Key');
      await user.type(key, bad);
      await user.click(within(dialog).getByRole('button', { name: 'Create flag' }));
      await waitFor(() => expect(key).toHaveAttribute('aria-invalid', 'true'));
      expect(key).toHaveAccessibleDescription(
        'Use 2 to 50 lowercase letters, digits or hyphens, starting with a letter Lowercase letters, digits and hyphens, 2 to 50 characters. Cannot be changed later.',
      );
      expect(key).toHaveValue(bad);
      expect(api.calls.filter((c) => c.method === 'POST')).toHaveLength(0);
    },
  );

  it('a description over 500 characters is rejected in New flag and Edit flag without a request', async () => {
    const g = makeGroup('orders', 'Orders', [['new-checkout', false]]);
    start([g]);
    renderApp(`/groups/${g.id}`);
    const user = userEvent.setup();
    const long = 'x'.repeat(501);

    await user.click(await screen.findByRole('button', { name: 'New flag' }));
    let dialog = screen.getByRole('dialog', { name: 'New flag in Orders' });
    await user.type(within(dialog).getByLabelText('Key'), 'other');
    await user.click(within(dialog).getByLabelText('Description (optional)'));
    await user.paste(long);
    await user.click(within(dialog).getByRole('button', { name: 'Create flag' }));
    const created = within(dialog).getByLabelText('Description (optional)');
    await waitFor(() => expect(created).toHaveAttribute('aria-invalid', 'true'));
    expect(
      within(dialog).getByText('Description must be at most 500 characters'),
    ).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    await user.click(screen.getByRole('button', { name: 'Edit orders.new-checkout' }));
    dialog = screen.getByRole('dialog', { name: 'Edit flag' });
    await user.click(within(dialog).getByLabelText('Description (optional)'));
    await user.paste(long);
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    const edited = within(dialog).getByLabelText('Description (optional)');
    await waitFor(() => expect(edited).toHaveAttribute('aria-invalid', 'true'));
    expect(
      within(dialog).getByText('Description must be at most 500 characters'),
    ).toBeInTheDocument();
    expect(api.calls.filter((c) => c.method !== 'GET')).toHaveLength(0);
  });

  it('server 400 description errors are shown on the Description field of Edit flag', async () => {
    const g = makeGroup('orders', 'Orders', [['new-checkout', false]]);
    start([g]);
    server.use(
      http.patch('/api/v1/admin/flags/:id', () =>
        HttpResponse.json(
          {
            type: 'https://featureflags.local/problems/validation',
            status: 400,
            errors: [{ field: 'description', message: 'server says no' }],
          },
          { status: 400 },
        ),
      ),
    );
    renderApp(`/groups/${g.id}`);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Edit orders.new-checkout' }));
    const dialog = screen.getByRole('dialog', { name: 'Edit flag' });
    await user.type(within(dialog).getByLabelText('Description (optional)'), 'x');
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    await waitFor(() =>
      expect(within(dialog).getByLabelText('Description (optional)')).toHaveAccessibleDescription(
        'server says no',
      ),
    );
  });

  it('[AC-FLAG-3] toggling updates the switch at once, the badge, and stays after a reload', async () => {
    const g = makeGroup('orders', 'Orders', [
      ['new-checkout', false],
      ['split-payments', false],
    ]);
    start([g]);
    api.toggleDelay = 50;
    const { unmount } = renderApp(`/groups/${g.id}`);
    const user = userEvent.setup();
    const sw = await screen.findByRole('switch', { name: 'Toggle orders.new-checkout' });
    expect(badge(/Orders/).textContent).toBe('0/2');

    await user.click(sw);
    expect(sw).toHaveAttribute('aria-checked', 'true');
    expect(sw).toBeDisabled();
    expect(badge(/Orders/).textContent).toBe('1/2');
    await waitFor(() => expect(sw).toBeEnabled());
    expect(api.calls.find((c) => c.path.endsWith('/toggle'))?.body).toEqual({ enabled: true });
    expect(screen.queryByRole('status')).toBeNull();

    unmount();
    renderApp(`/groups/${g.id}`);
    expect(
      await screen.findByRole('switch', { name: 'Toggle orders.new-checkout' }),
    ).toHaveAttribute('aria-checked', 'true');
    await waitFor(() => expect(badge(/Orders/).textContent).toBe('1/2'));
    expect(screen.getByText('1 of 2 flags on')).toBeInTheDocument();
  });

  it('[AC-FLAG-4] a failed toggle reverts the switch and shows the error toast', async () => {
    const g = makeGroup('orders', 'Orders', [['new-checkout', false]]);
    start([g]);
    renderApp(`/groups/${g.id}`);
    const user = userEvent.setup();
    const sw = await screen.findByRole('switch', { name: 'Toggle orders.new-checkout' });
    api.failNext('POST', '/toggle', 500, 'internal');
    await user.click(sw);
    expect((await screen.findByRole('alert')).textContent).toBe(
      'Could not update flag orders.new-checkout',
    );
    expect(sw).toHaveAttribute('aria-checked', 'false');
    expect(badge(/Orders/).textContent).toBe('0/1');
  });

  it('[AC-FLAG-5] deleting one flag removes only that flag', async () => {
    const g = makeGroup('orders', 'Orders', [
      ['a-flag', true],
      ['b-flag', false],
    ]);
    start([g]);
    renderApp(`/groups/${g.id}`);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Delete orders.a-flag' }));
    const dialog = screen.getByRole('alertdialog', { name: 'Delete flag orders.a-flag?' });
    expect(dialog).toHaveTextContent('Services reading it will get 404.');
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(screen.queryByText('orders.a-flag')).toBeNull());
    expect(screen.getByText('orders.b-flag')).toBeInTheDocument();
    expect(screen.getByRole('status').textContent).toBe('Flag orders.a-flag deleted');
  });

  it('[AC-FLAG-6] editing with a stale version gives 409: dialog closes, toast, and the group is refetched', async () => {
    const g = makeGroup('orders', 'Orders', [['new-checkout', false]]);
    start([g]);
    const { client } = renderApp(`/groups/${g.id}`);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Edit orders.new-checkout' }));
    const dialog = screen.getByRole('dialog', { name: 'Edit flag' });
    expect(within(dialog).getByLabelText('Key')).toHaveValue('orders.new-checkout');
    expect(within(dialog).getByLabelText('Key')).toHaveAttribute('readonly');
    const flag = g.flags[0];
    flag.description = 'Changed elsewhere';
    flag.version = 4;
    // Newer data arrives while the dialog is open; the dialog must still send version 0 (8.5).
    await act(() => client.invalidateQueries({ queryKey: ['group', g.id] }));
    expect(await screen.findByText('Changed elsewhere')).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText('Description (optional)'), 'mine');
    // TA-2: the refetch after the 409 is a new GET of the group, after the PATCH.
    const getsBefore = api.calls.filter((c) => c.method === 'GET' && c.path.endsWith(g.id)).length;
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }));

    expect((await screen.findByRole('alert')).textContent).toBe(
      'This item was changed by someone else',
    );
    await waitFor(() =>
      expect(
        api.calls.filter((c) => c.method === 'GET' && c.path.endsWith(g.id)).length,
      ).toBeGreaterThan(getsBefore),
    );
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(await screen.findByText('Changed elsewhere')).toBeInTheDocument();
    expect(api.calls.find((c) => c.method === 'PATCH')?.body).toEqual({
      description: 'mine',
      version: 0,
    });
  });

  it('edit flag saves the description', async () => {
    const g = makeGroup('orders', 'Orders', [['new-checkout', false]]);
    start([g]);
    renderApp(`/groups/${g.id}`);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Edit orders.new-checkout' }));
    await user.type(
      within(screen.getByRole('dialog')).getByLabelText('Description (optional)'),
      'Shiny',
    );
    await user.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Shiny')).toBeInTheDocument();
    expect(screen.getByRole('status').textContent).toBe('Flag orders.new-checkout updated');
  });

  it('search and status filter the table; no match shows the message', async () => {
    const g = makeGroup('orders', 'Orders', [
      ['new-checkout', true],
      ['split-payments', false],
    ]);
    start([g]);
    renderApp(`/groups/${g.id}`);
    const user = userEvent.setup();
    await screen.findByText('orders.new-checkout');
    const filter = screen.getByRole('group', { name: 'Filter by status' });
    expect(within(filter).getByRole('button', { name: 'All' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await user.click(within(filter).getByRole('button', { name: 'Off' }));
    expect(within(filter).getByRole('button', { name: 'Off' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.queryByText('orders.new-checkout')).toBeNull();
    expect(screen.getByText('orders.split-payments')).toBeInTheDocument();
    await user.click(within(filter).getByRole('button', { name: 'All' }));
    await user.type(screen.getByLabelText('Search flags'), 'CHECK');
    expect(screen.getByText('orders.new-checkout')).toBeInTheDocument();
    expect(screen.queryByText('orders.split-payments')).toBeNull();
    await user.click(within(filter).getByRole('button', { name: 'Off' }));
    expect(screen.getByText('No flags match this filter.')).toBeInTheDocument();
  });

  it('search matches the description (any case) and the On chip shows only enabled flags', async () => {
    const g = makeGroup('orders', 'Orders');
    g.flags = [
      makeFlag(g, 'alpha', true, 'Shiny Banner'),
      makeFlag(g, 'beta', false, 'plain text'),
      makeFlag(g, 'gamma', false, 'another BANNER'),
    ];
    start([g]);
    renderApp(`/groups/${g.id}`);
    const user = userEvent.setup();
    await screen.findByText('orders.alpha');
    const filter = screen.getByRole('group', { name: 'Filter by status' });
    await user.click(within(filter).getByRole('button', { name: 'On' }));
    expect(within(filter).getByRole('button', { name: 'On' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(within(filter).getByRole('button', { name: 'All' })).toHaveAttribute(
      'aria-pressed',
      'false',
    );
    expect(screen.getByText('orders.alpha')).toBeInTheDocument();
    expect(screen.queryByText('orders.beta')).toBeNull();
    expect(screen.queryByText('orders.gamma')).toBeNull();
    await user.click(within(filter).getByRole('button', { name: 'All' }));
    await user.type(screen.getByLabelText('Search flags'), 'bAnNeR');
    expect(screen.getByText('orders.alpha')).toBeInTheDocument();
    expect(screen.getByText('orders.gamma')).toBeInTheDocument();
    expect(screen.queryByText('orders.beta')).toBeNull();
  });

  it('the table has the spec column headers', async () => {
    const g = makeGroup('orders', 'Orders', [['a-flag', true]]);
    start([g]);
    renderApp(`/groups/${g.id}`);
    const table = await screen.findByRole('table', { name: 'Flags' });
    expect(
      within(table)
        .getAllByRole('columnheader')
        .map((h) => h.textContent),
    ).toEqual(['Key', 'Description', 'Status', 'Created by', 'Updated', 'Actions']);
  });
});
