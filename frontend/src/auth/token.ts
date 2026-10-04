// Spec 5.2 step 4: the admin JWT lives in sessionStorage under `ff.accessToken` as the raw string.
export const TOKEN_KEY = 'ff.accessToken';

export function getToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  sessionStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  sessionStorage.removeItem(TOKEN_KEY);
}

interface Claims {
  sub?: string;
  exp?: number;
}

/** Decodes the JWT payload without checking the signature (the server checks it). */
export function decodeClaims(token: string): Claims | null {
  const part = token.split('.')[1];
  if (!part) return null;
  try {
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const json = new TextDecoder().decode(Uint8Array.from(atob(padded), (c) => c.charCodeAt(0)));
    const value: unknown = JSON.parse(json);
    return typeof value === 'object' && value !== null ? (value as Claims) : null;
  } catch {
    return null;
  }
}

/** Spec 8.1: a token whose `exp` is in the past is expired. */
export function isExpired(token: string, nowMs: number = Date.now()): boolean {
  const claims = decodeClaims(token);
  if (!claims || typeof claims.exp !== 'number') return true;
  return claims.exp * 1000 <= nowMs;
}

export function usernameOf(token: string): string {
  return decodeClaims(token)?.sub ?? '';
}
