import { test,expect,type Page } from '@playwright/test';
const PASSWORD='synthetic-browser-workspace-password', MEMBER='synthetic-member-browser-password';
async function signin(page:Page,name='owner',password=PASSWORD){
  await page.goto('/');await page.getByLabel('Username',{exact:true}).fill(name);await page.getByLabel('Workspace password',{exact:true}).fill(password);await page.getByRole('button',{name:'Unlock workspace',exact:true}).click();await expect(page.getByText('Signed in as '+name,{exact:false})).toBeVisible();
}
async function access(page:Page){await page.getByRole('button',{name:'Household access',exact:true}).click();await expect(page.getByRole('heading',{name:'Household access',exact:true})).toBeVisible();}
async function enrol(owner:Page,member:Page,name:string){
  await access(owner);await owner.getByLabel('New username',{exact:true}).fill(name);await owner.getByRole('button',{name:'Create private invitation'}).click();const code=await owner.getByLabel('Private code — shown once').inputValue();await owner.getByRole('button',{name:'Hide code',exact:true}).click();
  await member.goto('/');await member.getByRole('button',{name:'Accept invitation',exact:true}).click();await member.getByLabel('Invitation code',{exact:true}).fill(code);await member.getByLabel('New account password',{exact:true}).fill(MEMBER);await member.getByLabel('I accept this household invitation').check();await member.getByRole('button',{name:'Create my account',exact:true}).click();await expect(member.getByText('Account accepted.',{exact:false})).toBeVisible();
  await signin(member,name,MEMBER);
  await owner.getByRole('button',{name:'Debt decision',exact:true}).click();await access(owner);
}
test('named reader sees only explicit shared records and loses access on withdrawal',async({page,browser,baseURL},info)=>{
  test.setTimeout(60000);
  const context=await browser.newContext({baseURL,viewport:info.project.name==='mobile'?{width:390,height:844}:{width:1280,height:900}});const member=await context.newPage();
  try{
    await signin(page);const name='member'+Date.now();const label='Synthetic household account '+name;
    const result=await page.request.post('/api/ledger/accounts',{headers:{'X-Workspace-Request':'1'},data:{name:label,kind:'CASH',openingDate:new Date().toLocaleDateString('en-CA',{timeZone:'Europe/Dublin'}),openingEuro:'72.34',source:'Synthetic browser evidence'}});expect(result.ok()).toBeTruthy();const account=await result.json();
    await enrol(page,member,name);await expect(member.getByRole('button',{name:'Money position',exact:true})).toHaveCount(0);
    const group=page.getByRole('group',{name:new RegExp('^'+name+' · ACTIVE$')});await group.getByLabel('READER',{exact:true}).check();await group.getByRole('button',{name:'Save permissions'}).click();
    await signin(member,name,MEMBER);await expect(member.getByRole('heading',{name:'Permitted money position'})).toBeVisible();await expect(member.getByText(label,{exact:true})).toHaveCount(0);
    const card=page.getByText(label,{exact:true}).locator('..');await card.getByRole('button',{name:'Share with household'}).click();await signin(member,name,MEMBER);await expect(member.getByRole('heading',{name:label,exact:true})).toBeVisible();
    expect((await member.request.get('/api/workspace/records')).status()).toBe(403);expect((await member.request.get('/api/ledger/accounts/'+account.id+'/activity')).status()).toBe(200);
    const denied=await member.request.post('/api/ledger/accounts',{headers:{'X-Workspace-Request':'1'},data:{}});expect(denied.status()).toBe(403);
    await member.screenshot({path:info.outputPath('permitted-reader.png'),fullPage:true});
    const otherTab=await context.newPage();await otherTab.goto('/');await expect(otherTab.getByRole('heading',{name:label,exact:true})).toBeVisible();
    await card.getByRole('button',{name:'Make private'}).click();
    // Both already-open tabs lose rendered data after server revocation, without reload/sign-in.
    await expect(member.getByRole('button',{name:'Unlock workspace',exact:true})).toBeVisible({timeout:25000});
    await expect(otherTab.getByRole('button',{name:'Unlock workspace',exact:true})).toBeVisible({timeout:25000});
    await expect(member.getByText(label,{exact:true})).toHaveCount(0);await expect(otherTab.getByText(label,{exact:true})).toHaveCount(0);
    await signin(member,name,MEMBER);await expect(member.getByText(label,{exact:true})).toHaveCount(0);expect((await member.request.get('/api/ledger/accounts/'+account.id+'/activity')).status()).toBe(400);
    // Keep this synthetic source reconciled for the existing shared-database regression journeys.
    const current=(await (await page.request.get('/api/ledger?asOf='+account.openingDate)).json()).accounts.find((a:{id:string})=>a.id===account.id);
    expect((await page.request.post('/api/ledger/accounts/'+account.id+'/reconcile?asOf='+account.openingDate,{headers:{'X-Workspace-Request':'1'},data:{revision:current.revision,statementEuro:'72.34',source:'Synthetic browser closing evidence',commit:true,acceptAdjustment:false}})).ok()).toBeTruthy();
    expect(await member.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();
    await page.screenshot({path:info.outputPath('household-access.png'),fullPage:true});
  }finally{await context.close();}
});
test('local recovery restores named access without changing role assignments',async({page,browser,baseURL})=>{
  const context=await browser.newContext({baseURL});const member=await context.newPage();
  try{
    await signin(page);const name='recover'+Date.now();await enrol(page,member,name);
    const group=page.getByRole('group',{name:new RegExp('^'+name+' · ACTIVE$')});await group.getByLabel('READER',{exact:true}).check();await group.getByRole('button',{name:'Save permissions'}).click();await signin(member,name,MEMBER);
    await access(member);await member.getByRole('button',{name:'Create my recovery code'}).click();const code=await member.getByLabel('Private code — shown once').inputValue();await member.getByRole('button',{name:'Lock workspace',exact:true}).click();
    await member.getByRole('button',{name:'Recover access',exact:true}).click();await member.getByLabel('Username',{exact:true}).fill(name);await member.getByLabel('Recovery code',{exact:true}).fill(code);await member.getByLabel('New account password',{exact:true}).fill(MEMBER+'-new');await member.getByRole('button',{name:'Reset account password'}).click();await expect(member.getByText('Password changed. Sign in again.',{exact:false})).toBeVisible();
    await signin(member,name,MEMBER+'-new');await expect(member.getByRole('heading',{name:'Permitted money position'})).toBeVisible();await expect(member.getByRole('button',{name:'Backup & recovery',exact:true})).toHaveCount(0);
    const replay=await member.request.post('/api/access/recover',{headers:{'X-Workspace-Request':'1'},data:{username:name,code,password:MEMBER}});expect(replay.status()).toBe(400);
  }finally{await context.close();}
});
