import { useEffect, type ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { clearToken, getToken, isExpired } from './token';

/** Clears the expired token after render (render stays pure, also under StrictMode). */
function ExpiredRedirect() {
  useEffect(() => {
    clearToken();
  }, []);
  return <Navigate to="/login?expired=1" replace />;
}

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
    return <ExpiredRedirect />;
  }
  return <>{children}</>;
}
