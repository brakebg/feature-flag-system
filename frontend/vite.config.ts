import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Spec 8.7: in dev, Vite proxies /api to the backend on 8080.
export default defineConfig({
  plugins: [react()],
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
