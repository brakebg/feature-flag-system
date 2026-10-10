import { useQueryClient } from '@tanstack/react-query';
import { useEffect, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { clearToken, getToken, isExpired } from './token';

/**
 * Clears the expired token and the cached data of the session after render (render stays pure,
 * also under StrictMode). FF-6: the cache is cleared like on a 401 and on Sign out.
 */
function ExpiredRedirect() {
  const client = useQueryClient();
  useEffect(() => {
    clearToken();
    client.clear();
  }, [client]);
  return <Navigate to="/login?expired=1" replace />;
}

/**
 * Spec 8.1: no token → `/login`; a token whose `exp` is in the past → token cleared and
 * `/login?expired=1`. Checked again on every navigation (the location is read for that).
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  useLocation();
  const token = getToken();
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  if (isExpired(token)) {
    return <ExpiredRedirect />;
  }
  return <>{children}</>;
}
