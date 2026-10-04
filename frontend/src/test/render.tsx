import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LocationProbe } from './LocationProbe';
import { AppRoutes, Providers } from '../App';
import { ROUTER_FUTURE } from '../routerFuture';
import { createQueryClient } from '../queryClient';

export function renderApp(path: string) {
  const client = createQueryClient();
  const utils = render(
    <Providers client={client}>
      <MemoryRouter initialEntries={[path]} future={ROUTER_FUTURE}>
        <AppRoutes client={client} />
        <LocationProbe />
      </MemoryRouter>
    </Providers>,
  );
  return { ...utils, client };
}

function base64url(value: object): string {
  return btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** An unsigned JWT-shaped token (the UI never checks signatures, spec 5.2). */
export function fakeToken(sub: string, expSecondsFromNow: number): string {
  const exp = Math.floor(Date.now() / 1000) + expSecondsFromNow;
  return `${base64url({ alg: 'HS256' })}.${base64url({ sub, exp })}.sig`;
}
