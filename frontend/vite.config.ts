import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The SPA talks to the Spring Boot API on :8080. We proxy /api through the dev
// server so the browser sees a single origin (avoids CORS; backend has none).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
