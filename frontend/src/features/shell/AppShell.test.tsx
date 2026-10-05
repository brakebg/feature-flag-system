import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { request } from '../../api/apiClient';
import { TOKEN_KEY } from '../../auth/token';
import { fakeToken, renderApp } from '../../test/render';
import { server } from '../../test/server';

describe('routes and app shell (spec 8.1, 8.3)', () => {
  it('[AC-AUTH-3] without a token /groups redirects to /login', async () => {
    renderApp('/groups');
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/login$/));
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('[AC-AUTH-4] a token whose exp is in the past is cleared and leads to /login?expired=1', async () => {
    sessionStorage.setItem(TOKEN_KEY, fakeToken('admin', -10));
    renderApp('/groups');
    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent('/login?expired=1'),
    );
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(screen.getByRole('status').textContent).toBe(
      'Your session expired. Please sign in again.',
    );
  });

  it('[AC-AUTH-4] the expired banner also shows under StrictMode (render stays pure)', async () => {
    sessionStorage.setItem(TOKEN_KEY, fakeToken('admin', -10));
    renderApp('/groups', { strict: true });
    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent('/login?expired=1'),
    );
    expect(screen.getByRole('status').textContent).toBe(
      'Your session expired. Please sign in again.',
    );
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it('[AC-AUTH-4] any 401 from the Admin API clears the token and leads to /login?expired=1', async () => {
    sessionStorage.setItem(TOKEN_KEY, fakeToken('admin', 3600));
    server.use(
      http.get('/api/v1/admin/groups', () =>
        HttpResponse.json(
          { type: 'https://featureflags.local/problems/unauthorized', status: 401 },
          { status: 401 },
        ),
      ),
    );
    renderApp('/groups');
    await act(async () => {
      await request('/v1/admin/groups').catch(() => undefined);
    });
    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent('/login?expired=1'),
    );
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it('/ redirects to /groups and the shell shows nav, user and version', async () => {
    sessionStorage.setItem(TOKEN_KEY, fakeToken('admin', 3600));
    renderApp('/');
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/groups'));
    const nav = screen.getByRole('navigation', { name: 'Main' });
    expect(nav).toContainElement(screen.getByRole('link', { name: 'Flags' }));
    expect(screen.getByRole('link', { name: 'Audit log' })).toHaveAttribute('href', '/audit');
    expect(screen.getByRole('link', { name: 'Flags' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByText('admin')).toBeInTheDocument();
    expect(screen.getByRole('contentinfo')).toHaveTextContent(/^v\d+\.\d+\.\d+$/);
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument();
  });

  it('[AC-AUTH-6] sign out clears the token, goes to /login and Back does not show protected data', async () => {
    sessionStorage.setItem(TOKEN_KEY, fakeToken('admin', 3600));
    let adminCalls = 0;
    server.use(
      http.all('/api/v1/admin/*', () => {
        adminCalls++;
        return HttpResponse.json([]);
      }),
    );
    renderApp(['/login', '/groups', '/audit']);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/login$/));
    expect(screen.queryByRole('status')).toBeNull();

    // Back from /login goes to the protected /groups entry, which must send the user to /login.
    await user.click(screen.getByTestId('history-back'));
    await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/login'));
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sign out' })).toBeNull();
    expect(adminCalls).toBe(0);
  });

  it('unknown routes show the 404 page with a link back', () => {
    renderApp('/no/such/page');
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to flags' })).toHaveAttribute('href', '/groups');
  });
});
