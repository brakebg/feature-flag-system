import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { TOKEN_KEY } from '../../auth/token';
import { fakeApi, makeGroup, type FakeApi } from '../../test/fakeApi';
import { fakeToken, renderApp } from '../../test/render';
import { server } from '../../test/server';

const KEY_TEXT = 'Use 2 to 50 lowercase letters, digits or hyphens, starting with a letter';
let api: FakeApi;

function start(groups = [makeGroup('payments', 'Payments', [['apple-pay', true]])]) {
  const fake = fakeApi(groups);
  api = fake.api;
  server.use(...fake.handlers);
}

beforeEach(() => {
  sessionStorage.setItem(TOKEN_KEY, fakeToken('admin', 3600));
});

const groupButton = (name: RegExp) =>
  within(screen.getByRole('navigation', { name: 'Flag groups' })).getByRole('button', { name });

async function openNewGroup() {
  const user = userEvent.setup();
  await user.click(await screen.findByRole('button', { name: 'New group' }));
  return { user, dialog: screen.getByRole('dialog', { name: 'New group' }) };
}

describe('groups pane (spec 8.4)', () => {
  it('[AC-GRP-1] create group orders (name Orders): it is selected, in the list with badge 0/0, and toasted', async () => {
    start();
    renderApp('/groups');
    const { user, dialog } = await openNewGroup();
    await user.type(within(dialog).getByLabelText('Name'), 'Orders');
    expect(within(dialog).getByLabelText('Key')).toHaveValue('orders');
    await user.click(within(dialog).getByRole('button', { name: 'Create group' }));

    const item = await waitFor(() => groupButton(/Orders/));
    expect(item).toHaveTextContent('orders');
    expect(item).toHaveTextContent('0/0');
    expect(item).toHaveAttribute('aria-current', 'true');
    const id = api.groups.find((g) => g.key === 'orders')?.id;
    expect(screen.getByTestId('location').textContent).toBe(`/groups/${id}`);
    expect(screen.getByRole('status').textContent).toBe('Group orders created');
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(api.calls.find((c) => c.method === 'POST')?.body).toEqual({
      key: 'orders',
      name: 'Orders',
      description: null,
    });
  });

  it('[AC-GRP-2] a second group with key orders shows "Key already exists" on the Key field', async () => {
    start([makeGroup('orders', 'Orders')]);
    renderApp('/groups');
    const { user, dialog } = await openNewGroup();
    await user.type(within(dialog).getByLabelText('Key'), 'orders');
    await user.type(within(dialog).getByLabelText('Name'), 'Again');
    await user.click(within(dialog).getByRole('button', { name: 'Create group' }));

    const key = within(dialog).getByLabelText('Key');
    await waitFor(() => expect(key).toHaveAttribute('aria-invalid', 'true'));
    expect(key).toHaveAccessibleDescription('Key already exists');
    expect(screen.getByRole('dialog', { name: 'New group' })).toBeInTheDocument();
  });

  it.each(['Orders', '1abc', 'a', 'has space'])(
    '[AC-GRP-3] key %s is rejected in the UI without a request',
    async (bad) => {
      start();
      renderApp('/groups');
      const { user, dialog } = await openNewGroup();
      await user.type(within(dialog).getByLabelText('Key'), bad);
      await user.type(within(dialog).getByLabelText('Name'), 'Some name');
      await user.click(within(dialog).getByRole('button', { name: 'Create group' }));

      const key = within(dialog).getByLabelText('Key');
      await waitFor(() => expect(key).toHaveAttribute('aria-invalid', 'true'));
      expect(key).toHaveAccessibleDescription(KEY_TEXT);
      expect(key).toHaveValue(bad);
      expect(api.calls.filter((c) => c.method === 'POST')).toHaveLength(0);
    },
  );

  it('name is required and at most 100 characters', async () => {
    start();
    renderApp('/groups');
    const { user, dialog } = await openNewGroup();
    await user.type(within(dialog).getByLabelText('Key'), 'ok-key');
    await user.click(within(dialog).getByRole('button', { name: 'Create group' }));
    await waitFor(() =>
      expect(within(dialog).getByLabelText('Name')).toHaveAccessibleDescription('Name is required'),
    );
    await user.type(within(dialog).getByLabelText('Name'), 'x'.repeat(101));
    await user.click(within(dialog).getByRole('button', { name: 'Create group' }));
    await waitFor(() =>
      expect(within(dialog).getByLabelText('Name')).toHaveAccessibleDescription(
        'Name must be at most 100 characters',
      ),
    );
    expect(api.calls.filter((c) => c.method === 'POST')).toHaveLength(0);
  });

  it('a 400 naming a field the form does not have shows the error toast', async () => {
    start();
    server.use(
      http.post('/api/v1/admin/groups', () =>
        HttpResponse.json(
          {
            type: 'https://featureflags.local/problems/validation',
            status: 400,
            detail: 'Request body is invalid',
            errors: [{ field: 'version', message: 'must not be null' }],
          },
          { status: 400 },
        ),
      ),
    );
    renderApp('/groups');
    const { user, dialog } = await openNewGroup();
    await user.type(within(dialog).getByLabelText('Name'), 'Valid');
    await user.click(within(dialog).getByRole('button', { name: 'Create group' }));
    expect((await screen.findByRole('alert')).textContent).toBe('Request body is invalid');
  });

  it('after a failed submit, a slug filled from the name clears the Key error', async () => {
    start();
    renderApp('/groups');
    const { user, dialog } = await openNewGroup();
    await user.click(within(dialog).getByRole('button', { name: 'Create group' }));
    const key = within(dialog).getByLabelText('Key');
    await waitFor(() => expect(key).toHaveAttribute('aria-invalid', 'true'));
    await user.type(within(dialog).getByLabelText('Name'), 'Orders');
    expect(key).toHaveValue('orders');
    await waitFor(() => expect(key).not.toHaveAttribute('aria-invalid'));
    expect(key).not.toHaveAccessibleDescription(/Use 2 to 50/);
  });

  it('the slug follows the name until the key is edited, then stops', async () => {
    start();
    renderApp('/groups');
    const { user, dialog } = await openNewGroup();
    const name = within(dialog).getByLabelText('Name');
    const key = within(dialog).getByLabelText('Key');
    await user.type(name, 'My New Group');
    expect(key).toHaveValue('my-new-group');
    await user.clear(key);
    await user.type(key, 'custom');
    await user.type(name, ' more');
    expect(key).toHaveValue('custom');
  });

  it('server 400 field errors are shown on their field', async () => {
    start();
    renderApp('/groups');
    const { user, dialog } = await openNewGroup();
    server.use(
      http.post('/api/v1/admin/groups', () =>
        HttpResponse.json(
          {
            type: 'https://featureflags.local/problems/validation',
            status: 400,
            errors: [{ field: 'name', message: 'server says no' }],
          },
          { status: 400 },
        ),
      ),
    );
    await user.type(within(dialog).getByLabelText('Name'), 'Valid');
    await user.click(within(dialog).getByRole('button', { name: 'Create group' }));
    await waitFor(() =>
      expect(within(dialog).getByLabelText('Name')).toHaveAccessibleDescription('server says no'),
    );
  });

  it('empty state, search without result, and an unknown group id', async () => {
    start([]);
    const { unmount } = renderApp('/groups');
    expect(await screen.findByText('No groups yet')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create your first group' })).toBeInTheDocument();
    expect(screen.getByText('Select a group or create one')).toBeInTheDocument();
    unmount();

    start();
    renderApp('/groups/0191f0c2-0000-7000-8000-999999999999');
    expect(await screen.findByText('Select a group or create one')).toBeInTheDocument();
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Search groups'), 'zzz');
    expect(screen.getByText('No groups match your search')).toBeInTheDocument();
    await user.clear(screen.getByLabelText('Search groups'));
    await user.type(screen.getByLabelText('Search groups'), 'PAY');
    expect(groupButton(/Payments/)).toBeInTheDocument();
  });

  it('the plus icons are not part of the button names', async () => {
    start();
    const g = api?.groups[0];
    renderApp(`/groups/${g?.id ?? ''}`);
    for (const name of ['New group', 'New flag']) {
      const button = await screen.findByRole('button', { name });
      expect(button.textContent?.trim()).toBe(name);
      expect(button.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    }
  });

  it('a description over 500 characters is rejected in New group without a request', async () => {
    start();
    renderApp('/groups');
    const { user, dialog } = await openNewGroup();
    await user.type(within(dialog).getByLabelText('Name'), 'Valid');
    await user.click(within(dialog).getByLabelText('Description (optional)'));
    await user.paste('x'.repeat(501));
    await user.click(within(dialog).getByRole('button', { name: 'Create group' }));
    const description = within(dialog).getByLabelText('Description (optional)');
    await waitFor(() => expect(description).toHaveAttribute('aria-invalid', 'true'));
    expect(description).toHaveAccessibleDescription('Description must be at most 500 characters');
    expect(api.calls.filter((c) => c.method === 'POST')).toHaveLength(0);
  });

  it('server 400 description errors are shown on the Description field of Edit group', async () => {
    const g = makeGroup('orders', 'Orders');
    start([g]);
    server.use(
      http.patch('/api/v1/admin/groups/:id', () =>
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
    await user.click(await screen.findByRole('button', { name: 'Edit group' }));
    const dialog = screen.getByRole('dialog', { name: 'Edit group' });
    await user.type(within(dialog).getByLabelText('Description (optional)'), 'x');
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    await waitFor(() =>
      expect(within(dialog).getByLabelText('Description (optional)')).toHaveAccessibleDescription(
        'server says no',
      ),
    );
  });

  it('clicking a group selects it: URL, aria-current and heading', async () => {
    const orders = makeGroup('orders', 'Orders');
    const pmt = makeGroup('pmt', 'Payments');
    start([orders, pmt]);
    renderApp('/groups');
    const user = userEvent.setup();
    await user.click(await waitFor(() => groupButton(/Payments/)));
    await waitFor(() =>
      expect(screen.getByTestId('location').textContent).toBe(`/groups/${pmt.id}`),
    );
    expect(groupButton(/Payments/)).toHaveAttribute('aria-current', 'true');
    expect(groupButton(/Orders/)).not.toHaveAttribute('aria-current');
    expect(await screen.findByRole('heading', { name: 'Payments' })).toBeInTheDocument();
  });

  it('group search matches the key or the name, in any case', async () => {
    start([makeGroup('pmt', 'Payments'), makeGroup('orders', 'Shop')]);
    renderApp('/groups');
    const user = userEvent.setup();
    const search = await screen.findByLabelText('Search groups');
    await waitFor(() => groupButton(/Payments/));
    await user.type(search, 'PmT');
    expect(groupButton(/Payments/)).toBeInTheDocument();
    expect(
      within(screen.getByRole('navigation', { name: 'Flag groups' })).queryByText('Shop'),
    ).toBeNull();
    await user.clear(search);
    await user.type(search, 'sHoP');
    expect(groupButton(/Shop/)).toBeInTheDocument();
    expect(
      within(screen.getByRole('navigation', { name: 'Flag groups' })).queryByText('Payments'),
    ).toBeNull();
  });
});

describe('group header and edit (spec 8.4, 8.5)', () => {
  it('[AC-GRP-4] edit name and description; the key is read-only', async () => {
    const g = makeGroup(
      'orders',
      'Orders',
      [
        ['new-checkout', true],
        ['split-payments', false],
      ],
      'Checkout',
    );
    start([g]);
    renderApp(`/groups/${g.id}`);
    expect(await screen.findByRole('heading', { name: 'Orders' })).toBeInTheDocument();
    expect(screen.getByText('Checkout · 1 of 2 flags on')).toBeInTheDocument();
    const meta = screen.getByText(
      (_, el) => el?.tagName === 'P' && !!el.textContent?.startsWith('Created by'),
    );
    expect(meta.textContent).toMatch(/^Created by system · Updated by admin, .+$/);

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Edit group' }));
    const dialog = screen.getByRole('dialog', { name: 'Edit group' });
    const key = within(dialog).getByLabelText('Key');
    expect(key).toHaveAttribute('readonly');
    expect(key).toHaveValue('orders');
    expect(within(dialog).getByLabelText('Name')).toHaveValue('Orders');
    expect(within(dialog).getByLabelText('Description (optional)')).toHaveValue('Checkout');
    await user.clear(within(dialog).getByLabelText('Name'));
    await user.type(within(dialog).getByLabelText('Name'), 'Shop');
    await user.clear(within(dialog).getByLabelText('Description (optional)'));
    await user.type(within(dialog).getByLabelText('Description (optional)'), 'All orders');
    // Spec 8.5: Enter submits.
    await user.type(within(dialog).getByLabelText('Name'), '{Enter}');

    expect(await screen.findByRole('heading', { name: 'Shop' })).toBeInTheDocument();
    expect(screen.getByText('All orders · 1 of 2 flags on')).toBeInTheDocument();
    expect(screen.getByRole('status').textContent).toBe('Group orders updated');
    expect(api.calls.find((c) => c.method === 'PATCH')?.body).toEqual({
      name: 'Shop',
      description: 'All orders',
      version: 0,
    });
  });

  it('[AC-FLAG-6] a stale group version closes the dialog, shows the error toast and refetches', async () => {
    const g = makeGroup('orders', 'Orders');
    start([g]);
    const { client } = renderApp(`/groups/${g.id}`);
    await screen.findByRole('heading', { name: 'Orders' });
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Edit group' }));
    g.name = 'Changed elsewhere';
    g.version = 3;
    // Newer data arrives while the dialog is open; the dialog must still send version 0 (8.5).
    await act(() => client.invalidateQueries({ queryKey: ['group', g.id] }));
    expect(await screen.findByRole('heading', { name: 'Changed elsewhere' })).toBeInTheDocument();
    await user.type(within(screen.getByRole('dialog')).getByLabelText('Name'), '!');
    // TA-2: the refetch after the 409 is a new GET of the group, after the PATCH.
    const getsBefore = api.calls.filter((c) => c.method === 'GET' && c.path.endsWith(g.id)).length;
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    expect((await screen.findByRole('alert')).textContent).toBe(
      'This item was changed by someone else',
    );
    await waitFor(() =>
      expect(
        api.calls.filter((c) => c.method === 'GET' && c.path.endsWith(g.id)).length,
      ).toBeGreaterThan(getsBefore),
    );
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(api.calls.find((c) => c.method === 'PATCH')?.body).toEqual({
      name: 'Orders!',
      description: null,
      version: 0,
    });
    expect(screen.getByRole('heading', { name: 'Changed elsewhere' })).toBeInTheDocument();
  });
});

describe('delete group (spec 8.5)', () => {
  it('[AC-GRP-5] needs the exact key typed, lists the flags, then goes to /groups with a toast', async () => {
    const g = makeGroup('orders', 'Orders', [
      ['new-checkout', true],
      ['split-payments', false],
    ]);
    start([g, makeGroup('payments', 'Payments')]);
    renderApp(`/groups/${g.id}`);
    await screen.findByRole('heading', { name: 'Orders' });
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Delete group' }));

    const dialog = screen.getByRole('alertdialog', { name: 'Delete group “Orders”?' });
    expect(dialog).toHaveTextContent(
      'This permanently deletes the group and all 2 flags in it. Services reading these flags will get 404.',
    );
    const list = within(dialog).getByRole('list', { name: 'Flags that will be deleted' });
    expect(
      within(list)
        .getAllByRole('listitem')
        .map((li) => li.textContent),
    ).toEqual(['orders.new-checkout', 'orders.split-payments']);
    const confirm = within(dialog).getByRole('button', { name: 'Delete group and 2 flags' });
    const input = within(dialog).getByLabelText('Type orders to confirm');
    expect(confirm).toBeDisabled();
    await user.type(input, 'Orders');
    expect(confirm).toBeDisabled();
    await user.clear(input);
    await user.type(input, ' orders');
    expect(confirm).toBeDisabled();
    await user.clear(input);
    await user.type(input, 'orders');
    expect(confirm).toBeEnabled();
    await user.click(confirm);

    await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/groups'));
    expect(screen.getByRole('status').textContent).toBe('Group orders and 2 flags deleted');
    expect(
      within(screen.getByRole('navigation', { name: 'Flag groups' })).queryByText('Orders'),
    ).toBeNull();
    expect(api.groups.map((x) => x.key)).toEqual(['payments']);
  });

  it('one flag uses the singular "1 flag"', async () => {
    const g = makeGroup('orders', 'Orders', [['only', true]]);
    start([g]);
    renderApp(`/groups/${g.id}`);
    await screen.findByRole('heading', { name: 'Orders' });
    await userEvent.setup().click(screen.getByRole('button', { name: 'Delete group' }));
    expect(screen.getByRole('button', { name: 'Delete group and 1 flag' })).toBeInTheDocument();
  });
});
