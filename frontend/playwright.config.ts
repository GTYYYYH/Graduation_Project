import { defineConfig } from '@playwright/test'

// The test backend and Vite proxy are started separately with the B11 test fixture.
export default defineConfig({
  testDir: './tests/e2e',
  workers: 1,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:5175',
    channel: 'chrome',
    headless: true,
    screenshot: 'only-on-failure',
  },
})
