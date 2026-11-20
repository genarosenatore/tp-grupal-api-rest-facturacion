import { defineConfig } from '@playwright/test';

const port = Number(process.env.E2E_PORT ?? 5173);
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('E2E_PORT debe ser un puerto TCP válido.');
const baseURL = `http://127.0.0.1:${port}`;

export default defineConfig({
  testDir: './tests/browser', workers: 1, fullyParallel: false,
  use: { baseURL, browserName: 'chromium', channel: 'msedge', locale: 'es-AR', timezoneId: 'America/Argentina/Buenos_Aires', screenshot: 'only-on-failure' },
  reporter: 'list', outputDir: '../tmp/browser-results',
  webServer: {
    command: `"${process.execPath}" node_modules/vite/bin/vite.js --host 127.0.0.1 --port ${port} --strictPort`,
    url: baseURL,
    reuseExistingServer: !process.env.CI && !process.env.API_PROXY_TARGET
  }
});
