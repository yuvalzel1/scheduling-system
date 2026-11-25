import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// Tests run in a fixed, non-UTC time zone so local-time conversions are checked for real.
process.env.TZ = 'America/New_York';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // The app always calls the relative path /api. In dev it is forwarded to the backend;
    // in Docker, nginx does the same. Set API_PROXY_TARGET if the backend runs elsewhere.
    proxy: {
      '/api': process.env.API_PROXY_TARGET ?? 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    testTimeout: 15000,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/main.tsx', 'src/test/**', 'src/**/*.test.{ts,tsx}', 'src/api/types.ts'],
    },
  },
});
