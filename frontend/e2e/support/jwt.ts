import { createHmac } from 'node:crypto';

/** Spec 11.5: tests may sign HS256 JWTs with the documented dev secret (5.1) for negative cases. */
export const DEV_JWT_SECRET = 'change-me-to-a-32-byte-minimum-secret!!';

const b64url = (data: string | Buffer) => Buffer.from(data).toString('base64url');

export function signJwt(claims: Record<string, unknown>, secret = DEV_JWT_SECRET): string {
  const head = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = b64url(JSON.stringify(claims));
  const sig = createHmac('sha256', secret).update(`${head}.${body}`).digest('base64url');
  return `${head}.${body}.${sig}`;
}

export function expiredAdminToken(): string {
  const now = Math.floor(Date.now() / 1000);
  return signJwt({
    sub: 'admin',
    scope: 'admin',
    aud: ['feature-flag-admin'],
    iss: 'feature-flag-service',
    iat: now - 7200,
    exp: now - 3600,
  });
}

export function expiredClientToken(): string {
  const now = Math.floor(Date.now() / 1000);
  return signJwt({
    sub: 'order-service',
    scope: 'flags:read',
    aud: ['feature-flag-service'],
    iss: 'feature-flag-service',
    iat: now - 1000,
    exp: now - 100,
  });
}

/** A token whose signature no longer matches (spec 11.5: a "changed signature" token). */
export function tamperedToken(token: string): string {
  const [h, b, s] = token.split('.');
  const flipped = (s[0] === 'A' ? 'B' : 'A') + s.slice(1);
  return `${h}.${b}.${flipped}`;
}
