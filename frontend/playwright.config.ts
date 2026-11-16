import { defineConfig, devices } from '@playwright/test';

const backendPort=process.env.WORKSPACE_TEST_BACKEND_PORT || '8080';
const backendURL='http://127.0.0.1:'+backendPort;
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
      command: 'mvn -B -ntp -f ../backend/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.2.5:test-run -Dspring-boot.run.useTestClasspath=true -Dspring-boot.run.main-class=com.householdfinance.bank.ProductionBrowserApplication -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:mem:production-browser;DB_CLOSE_DELAY=-1 --server.port=8082 --server.address=127.0.0.1 --workspace.password=synthetic-browser-workspace-password --workspace.origins=http://127.0.0.1:8082"',
      url: 'http://127.0.0.1:8082/health', timeout: 180000, reuseExistingServer: false,
    },
    {
      command: 'mvn -B -ntp -f ../backend/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.2.5:run -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:mem:recovery;DB_CLOSE_DELAY=-1 --server.port=8081 --server.address=127.0.0.1 --workspace.password=synthetic-browser-workspace-password --workspace.origins=http://127.0.0.1:4174"',
      url: 'http://127.0.0.1:8081/health', timeout: 180000, reuseExistingServer: false,
    },
    { command: 'npm run dev -- --host 127.0.0.1 --port 4174 --strictPort', env: { WORKSPACE_BACKEND: 'http://127.0.0.1:8081' }, url: 'http://127.0.0.1:4174', timeout: 30000, reuseExistingServer: false },
    {
      command: `mvn -B -ntp -f ../backend/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.2.5:test-run -Dspring-boot.run.useTestClasspath=true -Dspring-boot.run.main-class=com.householdfinance.bank.SandboxBrowserApplication -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:mem:browser;DB_CLOSE_DELAY=-1 --spring.h2.console.enabled=false --server.port=${backendPort} --server.address=127.0.0.1 --evidence.intake.access-key=synthetic-browser-intake-key --workspace.password=synthetic-browser-workspace-password --workspace.origins=http://127.0.0.1:4173"`,
      url: backendURL+'/health', timeout: 180000, reuseExistingServer: false,
    },
    { command: 'npm run dev -- --host 127.0.0.1 --port 4173 --strictPort', env: {WORKSPACE_BACKEND:backendURL}, url: 'http://127.0.0.1:4173', timeout: 30000, reuseExistingServer: false },
  ],
});
