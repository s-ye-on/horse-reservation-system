import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig, devices } from '@playwright/test'

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../..')

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 45_000,
  expect: { timeout: 10_000 },
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:5173',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: [
    {
      command: 'mise run db:up && mise run backend:build && exec java -jar backend/build/libs/horse-backend-0.0.1-SNAPSHOT.jar',
      cwd: repositoryRoot,
      url: 'http://127.0.0.1:8080/actuator/health',
      timeout: 180_000,
      reuseExistingServer: false,
    },
    {
      command: 'pnpm --dir frontend/apps/web exec vite --host 127.0.0.1 --port 5173',
      cwd: repositoryRoot,
      url: 'http://127.0.0.1:5173',
      timeout: 60_000,
      reuseExistingServer: false,
    },
  ],
})
