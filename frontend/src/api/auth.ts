import { request } from './apiClient';

export interface LoginResponse {
  accessToken: string;
  expiresAt: string;
  username: string;
}

/** Spec 5.2: POST /api/v1/auth/login. */
export function login(username: string, password: string): Promise<LoginResponse> {
  return request<LoginResponse>('/v1/auth/login', {
    method: 'POST',
    body: { username, password },
    auth: false,
  });
}
