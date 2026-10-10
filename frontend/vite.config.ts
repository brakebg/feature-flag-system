import { existsSync, readFileSync } from 'node:fs';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Spec 9.6: the UI shows the repository version. The Docker build passes it as
 * VITE_APP_VERSION (its context has no VERSION file); other builds read ../VERSION.
 */
function appVersion(): string {
  if (process.env.VITE_APP_VERSION) return process.env.VITE_APP_VERSION;
  const file = new URL('../VERSION', import.meta.url);
  if (existsSync(file)) return readFileSync(file, 'utf8').trim();
  throw new Error('No app version: set VITE_APP_VERSION or provide ../VERSION');
}

// Spec 8.7: in dev, Vite proxies /api to the backend on 8080.
export default defineConfig({
  plugins: [react()],
  define: {
    __APP_VERSION__: JSON.stringify(appVersion()),
  },
  build: {
    // Spec 10.2 CSP: font-src 'self' (no data: URLs), so fonts are never inlined.
    assetsInlineLimit: 0,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
