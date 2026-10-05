import { ApiError } from '../api/ApiError';

/** Spec 8.5: server field errors for a form: 409 duplicate on Key, 400 field errors as given. */
export function serverFieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {};
  if (error.status === 409 && error.kind === 'duplicate-key') {
    return { key: 'Key already exists' };
  }
  if (error.status === 400 && error.kind === 'validation') {
    const out: Record<string, string> = {};
    for (const e of error.errors) {
      if (!(e.field in out)) out[e.field] = e.message;
    }
    return out;
  }
  return {};
}

export function isVersionConflict(error: unknown): boolean {
  return error instanceof ApiError && error.status === 409 && error.kind === 'version-conflict';
}

export function errorText(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (error.isNetwork) return 'Cannot reach server';
    if (error.kind === 'limit-reached') return error.detail || 'Limit reached';
    if (error.detail) return error.detail;
  }
  return fallback;
}
