import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { backend, server } from './fakeBackend';

// A request no handler answers is a mistake in the test, so it fails loudly.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));

afterEach(() => {
  cleanup();
  server.resetHandlers();
  backend.reset();
});

afterAll(() => server.close());
