import { clearToken, getToken } from '../auth/token';
import { ApiError, type FieldError } from './ApiError';

// Spec 8.7: base URL from VITE_API_BASE_URL (default /api).
export const API_BASE: string = import.meta.env.VITE_API_BASE_URL ?? '/api';

type Unauthorized = () => void;
let onUnauthorized: Unauthorized = () => {
  window.location.assign('/login?expired=1');
};

/** Spec 5.2 step 5: any 401 from the Admin API clears the token and redirects. */
export function setUnauthorizedHandler(handler: Unauthorized): void {
  onUnauthorized = handler;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  body?: unknown;
  /** false for calls made without a session (login). */
  auth?: boolean;
}

/** Typed fetch for the backend: adds the bearer token, parses problem details into ApiError. */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true } = options;
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const token = auth ? getToken() : null;
  if (auth) {
    // Spec 8.3: no Admin API request is sent without a token.
    if (!token) throw new ApiError(401, 'unauthorized', 'Not signed in');
    headers.Authorization = `Bearer ${token}`;
  }
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'network', 'Cannot reach server');
  }
  // A late 401 of an older session must not end a newer one.
  if (response.status === 401 && auth && getToken() === token) {
    clearToken();
    onUnauthorized();
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  if (!text) return undefined as T;
  try {
    return JSON.parse(text) as T;
  } catch {
    throw new ApiError(response.status, 'malformed-response', 'Response is not valid JSON');
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  let type = '';
  let detail = '';
  let errors: FieldError[] = [];
  try {
    const problem: unknown = await response.json();
    if (problem && typeof problem === 'object') {
      const p = problem as { type?: unknown; detail?: unknown; errors?: unknown };
      type = typeof p.type === 'string' ? p.type : '';
      detail = typeof p.detail === 'string' ? p.detail : '';
      if (Array.isArray(p.errors)) {
        errors = p.errors.filter(
          (e): e is FieldError => !!e && typeof e === 'object' && 'field' in e && 'message' in e,
        );
      }
    }
  } catch {
    // Not JSON (for example a 429 from the edge): keep status and headers only.
  }
  return new ApiError(response.status, type, detail, errors, response.headers);
}
