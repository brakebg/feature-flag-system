import { QueryClient } from '@tanstack/react-query';

/** TanStack Query client: no retries (errors are shown at once), no refetch on window focus. */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, refetchOnWindowFocus: false },
      mutations: { retry: false },
    },
  });
}
