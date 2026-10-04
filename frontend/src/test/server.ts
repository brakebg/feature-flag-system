import { setupServer } from 'msw/node';

/** MSW server for unit tests (spec 2: MSW for API mocking). Handlers are set per test. */
export const server = setupServer();
