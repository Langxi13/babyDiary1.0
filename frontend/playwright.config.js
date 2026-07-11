import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.E2E_WEB_BASE_URL || 'http://127.0.0.1:4173'

export default defineConfig({
  testDir: './e2e',
  outputDir: './test-results',
  timeout: 45_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: process.env.CI ? 2 : 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI
    ? [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]]
    : [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  globalSetup: './e2e/global-setup.js',
  globalTeardown: './e2e/global-teardown.js',
  use: {
    baseURL,
    storageState: './e2e/.auth/user.json',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai'
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1366, height: 900 } }
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'], viewport: { width: 1366, height: 900 } }
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'], viewport: { width: 1366, height: 900 } }
    }
  ],
  webServer: [
    {
      command: 'bash ../scripts/start-e2e-backend.sh',
      url: 'http://127.0.0.1:11002/actuator/health',
      timeout: 180_000,
      reuseExistingServer: false
    },
    {
      command: 'VITE_DEV_API_TARGET=http://127.0.0.1:11002 npm run dev -- --host 127.0.0.1 --port 4173',
      url: baseURL,
      timeout: 120_000,
      reuseExistingServer: false
    }
  ]
})
