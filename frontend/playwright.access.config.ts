import { defineConfig,devices } from '@playwright/test';
export default defineConfig({
  testDir:'./e2e',testMatch:'household-access.spec.ts',workers:1,retries:0,timeout:60000,
  use:{baseURL:'http://127.0.0.1:43902',launchOptions:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE?{executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE}:{},screenshot:'only-on-failure',trace:'retain-on-failure'},
  projects:[{name:'desktop',use:{...devices['Desktop Chrome']}},{name:'mobile',use:{...devices['iPhone 13'],defaultBrowserType:'chromium'}}],
  webServer:[
    {command:'mvn -s ../.mvn/settings.xml -B -ntp -f ../backend/pom.xml org.springframework.boot:spring-boot-maven-plugin:4.1.2:run -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:mem:access-browser;DB_CLOSE_DELAY=-1 --server.port=43901 --server.address=127.0.0.1 --workspace.password=synthetic-browser-workspace-password --workspace.origins=http://127.0.0.1:43902"',url:'http://127.0.0.1:43901/health',timeout:180000,reuseExistingServer:false},
    {command:'npm run dev -- --host 127.0.0.1 --port 43902 --strictPort',env:{WORKSPACE_BACKEND:'http://127.0.0.1:43901'},url:'http://127.0.0.1:43902',timeout:30000,reuseExistingServer:false}
  ]
});
