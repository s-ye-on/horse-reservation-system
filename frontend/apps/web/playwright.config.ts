import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { tmpdir } from 'node:os'
import { defineConfig, devices } from '@playwright/test'

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../..')
const backendPort = Number(process.env.HORSE_E2E_BACKEND_PORT ?? '8080')
const webPort = Number(process.env.HORSE_E2E_WEB_PORT ?? '5173')
const runtimeDir = process.env.HORSE_E2E_RUNTIME_DIR
  ?? resolve(tmpdir(), 'horse-playwright-direct')
const backendBaseUrl = `http://127.0.0.1:${backendPort}`
const webBaseUrl = `http://127.0.0.1:${webPort}`

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 45_000,
  expect: { timeout: 10_000 },
  reporter: [['list']],
  outputDir: resolve(runtimeDir, 'playwright-output'),
  use: {
    baseURL: webBaseUrl,
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
      command: 'exec ./scripts/run-e2e-backend.sh',
      cwd: repositoryRoot,
      url: `${backendBaseUrl}/actuator/health`,
      timeout: 180_000,
      reuseExistingServer: false,
    },
    {
      command: `exec pnpm --dir frontend/apps/web exec vite --host 127.0.0.1 --port ${webPort} --strictPort`,
      cwd: repositoryRoot,
      url: webBaseUrl,
      timeout: 60_000,
      reuseExistingServer: false,
    },
  ],
})
