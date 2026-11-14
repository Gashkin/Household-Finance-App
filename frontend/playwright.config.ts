import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  use: { launchOptions: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE } : {}, baseURL: 'http://127.0.0.1:4173', trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile', use: { ...devices['iPhone 13'], defaultBrowserType: 'chromium' } },
  ],
  webServer: [
    {
      command: 'mvn -B -ntp -f ../backend/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.2.5:run -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:mem:recovery;DB_CLOSE_DELAY=-1 --server.port=8081 --server.address=127.0.0.1 --workspace.password=synthetic-browser-workspace-password --workspace.origins=http://127.0.0.1:4174"',
      url: 'http://127.0.0.1:8081/health', timeout: 180000, reuseExistingServer: false,
    },
    { command: 'WORKSPACE_BACKEND=http://127.0.0.1:8081 npm run dev -- --host 127.0.0.1 --port 4174 --strictPort', url: 'http://127.0.0.1:4174', timeout: 30000, reuseExistingServer: false },
    {
      command: 'mvn -B -ntp -f ../backend/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.2.5:run -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:mem:browser;DB_CLOSE_DELAY=-1 --spring.h2.console.enabled=false --server.address=127.0.0.1 --evidence.intake.access-key=synthetic-browser-intake-key --workspace.password=synthetic-browser-workspace-password --workspace.origins=http://127.0.0.1:4173"',
      url: 'http://127.0.0.1:8080/health', timeout: 180000, reuseExistingServer: false,
    },
    { command: 'npm run dev -- --host 127.0.0.1 --port 4173 --strictPort', url: 'http://127.0.0.1:4173', timeout: 30000, reuseExistingServer: false },
  ],
});
