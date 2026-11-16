import { expect, type Page, type APIRequestContext } from '@playwright/test';
export const PASSWORD='synthetic-browser-workspace-password';
export async function unlockFixture({page,request}:{page:Page;request:APIRequestContext}){
  const response=await request.post((process.env.WORKSPACE_TEST_BACKEND || 'http://127.0.0.1:8080')+'/api/workspace/unlock',{headers:{'X-Workspace-Request':'1'},data:{password:PASSWORD}});
  expect(response.status()).toBe(204);
  const {cookies}=await request.storageState();
  await page.context().addCookies(cookies);
}
