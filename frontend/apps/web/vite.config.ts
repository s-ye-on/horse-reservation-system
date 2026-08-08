import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  cacheDir: process.env.HORSE_E2E_RUNTIME_DIR
    ? `${process.env.HORSE_E2E_RUNTIME_DIR}/vite-cache`
    : undefined,
  plugins: [react()],
  server: {
    host: 'localhost',
    proxy: {
      '/api': {
        target: process.env.HORSE_E2E_BACKEND_BASE_URL ?? 'http://localhost:8080',
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
  },
})
