import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse, delay } from 'msw';
import { describe, expect, it } from 'vitest';
import { TOKEN_KEY } from '../../auth/token';
import { fakeToken, renderApp } from '../../test/render';
import { server } from '../../test/server';

const LOGIN = '/api/v1/auth/login';

async function signIn(username: string, password: string, submitWithEnter = false) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Username'), username);
  await user.type(screen.getByLabelText('Password'), password);
  if (submitWithEnter) {
    await user.keyboard('{Enter}');
  } else {
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
  }
}

describe('LoginPage (spec 8.2)', () => {
  it('[AC-AUTH-1] wrong credentials show the generic error and stay on /login', async () => {
    let calls = 0;
    server.use(
      http.post(LOGIN, () => {
        calls++;
        return HttpResponse.json(
          {
            type: 'https://featureflags.local/problems/unauthorized',
            status: 401,
            detail: 'server text',
          },
          { status: 401 },
        );
      }),
    );
    renderApp('/login');
    await signIn('admin', 'wrong');

    expect((await screen.findByRole('alert')).textContent).toBe('Invalid username or password');
    expect(screen.getByTestId('location').textContent).toBe('/login');
    expect(screen.queryByRole('status')).toBeNull();
    expect(calls).toBe(1);
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it('[AC-AUTH-2] admin / admin123 lands on /groups and stores the raw token', async () => {
    const token = fakeToken('admin', 3600);
    let body: unknown;
    server.use(
      http.post(LOGIN, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({
          accessToken: token,
          expiresAt: '2026-10-01T22:00:00Z',
          username: 'admin',
        });
      }),
    );
    renderApp('/login');
    await signIn('admin', 'admin123', true);

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/groups'));
    expect(body).toEqual({ username: 'admin', password: 'admin123' });
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe(token);
  });

  it('disables the button and shows a spinner while the request runs', async () => {
    server.use(
      http.post(LOGIN, async () => {
        await delay(50);
        return HttpResponse.json({ type: 'x', status: 401 }, { status: 401 });
      }),
    );
    renderApp('/login');
    await signIn('admin', 'x');
    const button = screen.getByRole('button', { name: 'Sign in' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
    expect(button.querySelector('[data-spinner]')).not.toBeNull();
    await screen.findByRole('alert');
    expect(button).toBeEnabled();
    expect(button.querySelector('[data-spinner]')).toBeNull();
  });

  it.each([
    ['5', 'Too many attempts, try again in 5 seconds'],
    ['1', 'Too many attempts, try again in 1 second'],
    [null, 'Too many attempts, try again later'],
    ['-5', 'Too many attempts, try again later'],
    ['soon', 'Too many attempts, try again later'],
    ['1.5', 'Too many attempts, try again later'],
  ])('429 with Retry-After %s shows "%s"', async (retryAfter, text) => {
    server.use(
      http.post(
        LOGIN,
        () =>
          new HttpResponse('slow down', {
            status: 429,
            headers: retryAfter === null ? {} : { 'Retry-After': retryAfter },
          }),
      ),
    );
    renderApp('/login');
    await signIn('admin', 'admin123');
    expect((await screen.findByRole('alert')).textContent).toBe(text);
  });

  it('a network failure shows "Cannot reach server"', async () => {
    server.use(http.post(LOGIN, () => HttpResponse.error()));
    renderApp('/login');
    await signIn('admin', 'admin123');
    expect((await screen.findByRole('alert')).textContent).toBe('Cannot reach server');
  });

  it('shows the error between the fields and the Sign in button', async () => {
    server.use(http.post(LOGIN, () => HttpResponse.json({ status: 401 }, { status: 401 })));
    renderApp('/login');
    await signIn('admin', 'x');
    const alert = await screen.findByRole('alert');
    const password = screen.getByLabelText('Password');
    const button = screen.getByRole('button', { name: 'Sign in' });
    expect(password.compareDocumentPosition(alert) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(alert.compareDocumentPosition(button) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('?expired=1 shows the session-expired banner as status', () => {
    renderApp('/login?expired=1');
    expect(screen.getByRole('status')).toHaveTextContent(
      'Your session expired. Please sign in again.',
    );
  });

  it('shows no banner without ?expired=1 and no session-length text', () => {
    renderApp('/login');
    expect(screen.queryByRole('status')).toBeNull();
    expect(screen.queryByText(/Sessions last/)).toBeNull();
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.getByText('Feature Flags')).toBeInTheDocument();
  });

  it('a signed-in user who opens /login goes to /', async () => {
    sessionStorage.setItem(TOKEN_KEY, fakeToken('admin', 3600));
    renderApp('/login');
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/groups'));
  });
});
