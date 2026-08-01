import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  cacheDir: process.env.HORSE_E2E_RUNTIME_DIR
    ? `${process.env.HORSE_E2E_RUNTIME_DIR}/vite-cache`
    : undefined,
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
  },
})
