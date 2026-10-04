import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { clearToken, getToken, isExpired } from './token';

/**
 * Spec 8.1: no token → `/login`; a token whose `exp` is in the past → token cleared and
 * `/login?expired=1`.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const token = getToken();
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  if (isExpired(token)) {
    clearToken();
    return <Navigate to="/login?expired=1" replace />;
  }
  return <>{children}</>;
}
