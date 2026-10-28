const { defineConfig } = require('playwright/test');
const { randomUUID } = require('node:crypto');

module.exports = defineConfig({
  testDir: './src/test/browser',
  outputDir: './target/browser-results',
  workers: 1,
  timeout: 30000,
  reporter: [['list'], ['html', { outputFolder: 'target/browser-report', open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:8082',
    channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome',
    headless: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: `java -Dserver.port=8082 -Ddata.directory=target/ui-tests/${randomUUID()} -jar target/cadastro-eventos-1.0.0.jar`,
    url: 'http://127.0.0.1:8082/health',
    reuseExistingServer: false,
    timeout: 60000,
  },
});
