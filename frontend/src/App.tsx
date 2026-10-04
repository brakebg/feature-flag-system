import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import { createQueryClient } from './queryClient';
import { ROUTER_FUTURE } from './routerFuture';
import { useEffect, useState, type ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { setUnauthorizedHandler } from './api/apiClient';
import { RequireAuth } from './auth/RequireAuth';
import { ToastProvider } from './components/Toast';
import { LoginPage } from './features/auth/LoginPage';
import { AppShell } from './features/shell/AppShell';
import { NotFoundPage } from './features/shell/NotFoundPage';
import { GroupsPage } from './features/groups/GroupsPage';
import { AuditPage } from './features/audit/AuditPage';

/** Spec 5.2 step 5: a 401 from the Admin API sends the user to /login?expired=1. */
function UnauthorizedRedirect({ client }: { client: QueryClient }) {
  const navigate = useNavigate();
  useEffect(() => {
    setUnauthorizedHandler(() => {
      client.clear();
      navigate('/login?expired=1', { replace: true });
    });
  }, [navigate, client]);
  return null;
}

/** Spec 8.1 routes. */
export function AppRoutes({ client }: { client: QueryClient }) {
  return (
    <>
      <UnauthorizedRedirect client={client} />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          element={
            <RequireAuth>
              <AppShell />
            </RequireAuth>
          }
        >
          <Route path="/" element={<Navigate to="/groups" replace />} />
          <Route path="/groups" element={<GroupsPage />} />
          <Route path="/groups/:groupId" element={<GroupsPage />} />
          <Route path="/audit" element={<AuditPage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </>
  );
}

export function Providers({ client, children }: { client: QueryClient; children: ReactNode }) {
  return (
    <QueryClientProvider client={client}>
      <ToastProvider>{children}</ToastProvider>
    </QueryClientProvider>
  );
}

export function App() {
  const [client] = useState(createQueryClient);
  return (
    <Providers client={client}>
      <BrowserRouter future={ROUTER_FUTURE}>
        <AppRoutes client={client} />
      </BrowserRouter>
    </Providers>
  );
}
