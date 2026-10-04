import { ApiError } from '../../api/ApiError';

/** Spec 8.2: the error text shown for a failed sign in. */
export function loginErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.isNetwork) return 'Cannot reach server';
    if (error.status === 401) return 'Invalid username or password';
    if (error.status === 429) {
      const raw = error.headers?.get('Retry-After');
      if (raw && /^\d+$/.test(raw.trim())) {
        const n = Number(raw.trim());
        return `Too many attempts, try again in ${n} ${n === 1 ? 'second' : 'seconds'}`;
      }
      return 'Too many attempts, try again later';
    }
  }
  return 'Sign in failed';
}
