import { describe, expect, it } from 'vitest';
import { fakeToken } from '../test/render';
import {
  clearToken,
  decodeClaims,
  getToken,
  isExpired,
  setToken,
  TOKEN_KEY,
  usernameOf,
} from './token';

describe('token (spec 5.2, 8.1)', () => {
  it('stores the raw JWT under ff.accessToken', () => {
    setToken('a.b.c');
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe('a.b.c');
    expect(TOKEN_KEY).toBe('ff.accessToken');
    expect(getToken()).toBe('a.b.c');
    clearToken();
    expect(getToken()).toBeNull();
  });

  it('reads sub and exp; a past or missing exp is expired', () => {
    const live = fakeToken('admin', 60);
    expect(usernameOf(live)).toBe('admin');
    expect(isExpired(live)).toBe(false);
    expect(isExpired(fakeToken('admin', -1))).toBe(true);
    expect(isExpired('not-a-jwt')).toBe(true);
    expect(isExpired('a.###.c')).toBe(true);
    expect(decodeClaims('a.bnVsbA.c')).toBeNull();
    expect(usernameOf('x')).toBe('');
  });

  it('exp exactly now counts as expired', () => {
    const token = fakeToken('admin', 0);
    const exp = decodeClaims(token)?.exp ?? 0;
    expect(isExpired(token, exp * 1000)).toBe(true);
    expect(isExpired(token, exp * 1000 - 1)).toBe(false);
  });
});
