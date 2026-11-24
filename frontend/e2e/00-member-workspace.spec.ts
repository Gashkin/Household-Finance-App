import { openWorkspace } from './workspace-navigation';
import { test,expect,type Page } from '@playwright/test';
const PASSWORD='synthetic-browser-workspace-password',MEMBER='synthetic-member-purchase-password';
const headers={'X-Workspace-Request':'1'};
async function login(page:Page,name='owner',password=PASSWORD){await page.goto('/');await page.getByLabel('Username',{exact:true}).fill(name);await page.getByLabel('Workspace password',{exact:true}).fill(password);await page.getByRole('button',{name:'Unlock workspace',exact:true}).click();await expect(page.getByRole('link',{name:'Activity',exact:true})).toBeVisible();}
async function spending(page:Page){await openWorkspace(page, 'Spending & purchases');await expect(page.getByRole('heading',{name:'Spending & purchases',exact:true})).toBeVisible();await expect(page.getByText('Checking current evidence…')).toHaveCount(0);}

test('member plans, requests, matches and revisits a scoped purchase on desktop and mobile',async({page,browser,baseURL},info)=>{
  test.setTimeout(90000);await login(page);
  const day=new Date().toLocaleDateString('en-CA',{timeZone:'Europe/Dublin'}),next=new Date(Date.now()+7*86400000).toLocaleDateString('en-CA',{timeZone:'Europe/Dublin'});
  const name='purchase'+Date.now();
  const accountResponse=await page.request.post('/api/ledger/accounts',{headers,data:{name:'Synthetic member cash '+name,kind:'CASH',openingDate:day,openingEuro:'1000.00',source:'Synthetic browser statement'}});expect(accountResponse.ok()).toBeTruthy();let account=await accountResponse.json();
  const sharing=await page.request.put('/api/ledger/accounts/'+account.id+'/sharing',{headers,data:{revision:account.revision,shared:true}});expect(sharing.ok()).toBeTruthy();account=await sharing.json();
  expect((await page.request.post('/api/ledger/accounts/'+account.id+'/reconcile?asOf='+day,{headers,data:{revision:account.revision,statementEuro:'1000.00',source:'Synthetic reviewed statement',commit:true,acceptAdjustment:false}})).ok()).toBeTruthy();
  const existingPosition=await (await page.request.get('/api/ledger?asOf='+day)).json();
  for(const a of existingPosition.accounts.filter((a:{owned:boolean;kind:string;reconciled:boolean})=>a.owned&&a.kind==='CASH'&&!a.reconciled))expect((await page.request.post('/api/ledger/accounts/'+a.id+'/reconcile?asOf='+day,{headers,data:{revision:a.revision,statementEuro:(a.bookedMinor/100).toFixed(2),source:'Synthetic reviewed browser evidence',commit:true,acceptAdjustment:false}})).ok()).toBeTruthy();
  const oldForecast=await (await page.request.get('/api/forecast')).json();
  expect((await page.request.post('/api/forecast',{headers,data:{revision:oldForecast.plan?.revision||oldForecast.hiddenPlanRevision||0,name:'Synthetic member forecast',baseline:{mode:'SHARED_LEDGER',date:day},endDate:next,reserveEuro:'100.00',events:[{id:crypto.randomUUID(),date:next,label:'Synthetic household bill',direction:'EXPENSE',certainty:'EXPECTED',amountEuro:'200.00',source:'Synthetic bill evidence'}],acknowledged:true}})).ok()).toBeTruthy();
  await spending(page);await page.getByText('Publish a reviewed shared forecast',{exact:true}).click();await page.getByLabel('I reviewed the shared sources and consent to publish these forecast details').check();await page.getByRole('button',{name:'Publish shared forecast',exact:true}).click();await expect(page.getByText('Shared forecast published.',{exact:true})).toBeVisible();
  const invite=await (await page.request.post('/api/access/invitations',{headers,data:{username:name}})).json();
  expect((await page.request.post('/api/access/accept',{headers,data:{code:invite.code,password:MEMBER,accepted:true}})).ok()).toBeTruthy();
  const users=await (await page.request.get('/api/access/users')).json(),user=users.find((u:{username:string})=>u.username===name);
  expect((await page.request.put('/api/access/users/'+user.id,{headers,data:{revision:user.revision,roles:['READER'],state:'ACTIVE'}})).ok()).toBeTruthy();
  const context=await browser.newContext({baseURL,viewport:info.project.name==='mobile'?{width:390,height:844}:{width:1280,height:900}});const member=await context.newPage();
  try{
    await login(member,name,MEMBER);await spending(member);await expect(member.getByText('Conservative spending room',{exact:true})).toBeVisible();await expect(member.getByText('Synthetic household bill',{exact:false})).toBeVisible();
    await member.getByLabel('Purchase title',{exact:true}).fill('Synthetic shared desk '+name);await member.getByLabel('Planned amount EUR',{exact:true}).fill('80.00');await member.getByLabel('Request an additional shared cost from the publishing editor').check();await member.getByLabel('I confirm this cost is additional to published bills, pending debits and existing purchase plans').check();await member.getByRole('button',{name:'Submit shared request'}).click();
    const card=member.getByRole('article',{name:'Purchase: Synthetic shared desk '+name});await expect(card.getByText('REQUESTED',{exact:true})).toBeVisible();expect((await member.request.get('/api/workspace/records')).status()).toBe(403);
    await page.getByRole('button',{name:'Refresh spending workspace'}).click();const review=page.getByRole('article',{name:'Purchase: Synthetic shared desk '+name});await review.getByLabel('Decision reason',{exact:true}).fill('Synthetic additional cost accepted');await review.getByRole('button',{name:'Approve additional cost'}).click();await expect(review.getByText('APPROVED',{exact:true})).toBeVisible();
    await member.getByRole('button',{name:'Refresh spending workspace'}).click();await card.getByRole('button',{name:'Mark bought'}).click();await expect(card.getByText('BOUGHT',{exact:true})).toBeVisible();
    const position=await (await page.request.get('/api/ledger?asOf='+day)).json();account=position.accounts.find((a:{id:string})=>a.id===account.id);
    expect((await page.request.post('/api/ledger/accounts/'+account.id+'/reopen',{headers,data:{revision:account.revision,reason:'Synthetic newly booked purchase'}})).ok()).toBeTruthy();
    account=(await (await page.request.get('/api/ledger?asOf='+day)).json()).accounts.find((a:{id:string})=>a.id===account.id);
    expect((await page.request.post('/api/ledger/accounts/'+account.id+'/activity',{headers,data:{revision:account.revision,row:{reference:'actual-'+name,date:day,description:'Synthetic actual desk '+name,amountEuro:'-85.00',status:'BOOKED',kind:'ACTIVITY'},source:'Synthetic booking',acknowledgedOverlap:true}})).ok()).toBeTruthy();
    await member.getByRole('button',{name:'Refresh spending workspace'}).click();await card.getByLabel('Actual account',{exact:true}).selectOption(account.id);await expect(card.getByLabel('Booked outgoing activity',{exact:true}).locator('option')).toHaveCount(2);await card.getByLabel('Booked outgoing activity',{exact:true}).selectOption({index:1});await card.getByRole('button',{name:'Confirm actual match'}).click();await expect(card.getByText(/Matched actual .*85/)).toBeVisible();await expect(card.getByText(/variance .*5/)).toBeVisible();
    await card.getByRole('button',{name:'Decision history'}).click();await expect(member.getByRole('heading',{name:'History: Synthetic shared desk '+name})).toBeVisible();
    await member.getByText('Personal dashboard & notifications',{exact:true}).click();await member.getByLabel('Purchase categories (comma separated)',{exact:true}).fill('Home, Garden');await member.getByLabel('Dashboard detail',{exact:true}).selectOption('COMPACT');await member.getByRole('button',{name:'Save personal preferences'}).click();await expect(member.getByText('Preferences saved.',{exact:true})).toBeVisible();
    await member.reload();await spending(member);await member.getByText('Personal dashboard & notifications',{exact:true}).click();await expect(member.getByLabel('Dashboard detail',{exact:true})).toHaveValue('COMPACT');await expect(card.getByText('BOUGHT',{exact:true})).toBeVisible();
    const download=member.waitForEvent('download');await member.getByRole('button',{name:'Download permitted report'}).click();expect((await download).suggestedFilename()).toBe('permitted-spending-report.json');
    expect(await member.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();await member.screenshot({path:info.outputPath('member-spending.png'),fullPage:true});
    // Leave shared evidence reconciled for the suite's later existing journeys.
    const revised=await (await page.request.get('/api/ledger?asOf='+day)).json();const a=revised.accounts.find((a:{id:string})=>a.id===account.id);
    expect((await page.request.post('/api/ledger/accounts/'+account.id+'/reconcile?asOf='+day,{headers,data:{revision:a.revision,statementEuro:'915.00',source:'Synthetic final browser evidence',commit:true,acceptAdjustment:false}})).ok()).toBeTruthy();
  }finally{
    await member.request.post('/api/workspace/lock',{headers,data:{}});
    await page.request.post('/api/workspace/lock',{headers,data:{}});
    await context.close();
  }
});
