import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  use: { baseURL: 'http://127.0.0.1:4173', trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile', use: { ...devices['iPhone 13'], defaultBrowserType: 'chromium' } },
  ],
  webServer: [
    {
      command: 'mvn -B -ntp -f ../backend/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.2.5:run -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:mem:browser;DB_CLOSE_DELAY=-1 --spring.h2.console.enabled=false --server.address=127.0.0.1"',
      url: 'http://127.0.0.1:8080/health', timeout: 180000, reuseExistingServer: false,
    },
    { command: 'npm run dev -- --host 127.0.0.1 --port 4173 --strictPort', url: 'http://127.0.0.1:4173', timeout: 30000, reuseExistingServer: false },
  ],
});
