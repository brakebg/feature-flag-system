import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { App } from './App';

describe('App (spec 8.1)', () => {
  it('wires the real router: /login renders the sign-in page', () => {
    window.history.pushState({}, '', '/login');
    render(<App />);
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('wires the real router: /groups without a token goes to /login', () => {
    window.history.pushState({}, '', '/groups');
    render(<App />);
    expect(window.location.pathname).toBe('/login');
  });
});
