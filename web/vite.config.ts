import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // The SPA and API are same-origin in every deployed environment (nginx
    // proxies /api), so the dev server mirrors that to keep session cookies
    // and CSRF behaving identically in development.
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: false,
      },
      '/actuator': {
        target: process.env.VITE_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
})
