import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Spec 8.7: in dev, Vite proxies /api to the backend on 8080.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
