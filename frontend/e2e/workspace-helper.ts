import { expect, type Page, type APIRequestContext } from '@playwright/test';
import { BACKEND_URL } from './fixture-urls';
export { BACKEND_URL } from './fixture-urls';
export const PASSWORD='synthetic-browser-workspace-password';
export async function unlockFixture({page,request}:{page:Page;request:APIRequestContext}){
  const response=await request.post(`${BACKEND_URL}/api/workspace/unlock`,{headers:{'X-Workspace-Request':'1'},data:{password:PASSWORD}});
  expect(response.status()).toBe(204);
  const {cookies}=await request.storageState();
  await page.context().addCookies(cookies);
}
