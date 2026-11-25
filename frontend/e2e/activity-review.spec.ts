import { openWorkspace } from './workspace-navigation';
import {test,expect} from '@playwright/test';
import {unlockFixture} from './workspace-helper';
test.use({extraHTTPHeaders:{'X-Workspace-Request':'1'}});
test.beforeEach(unlockFixture);
test.afterEach(async({page})=>{await page.request.post('/api/workspace/lock',{headers:{'X-Workspace-Request':'1'}});});
test('classify activity, approve a local rule and reopen protected source evidence',async({page},info)=>{
 test.setTimeout(90000);
 const day=(await(await page.request.get('/api/forecast')).json()).today;
 const suffix=info.project.name+'-'+Date.now();
 const response=await page.request.post('/api/ledger/accounts',{data:{name:'Synthetic review cash '+suffix,kind:'CASH',openingDate:day,openingEuro:'100.00',source:'Synthetic browser statement'}});expect(response.ok()).toBeTruthy();let account=await response.json();
 const row={reference:'review-'+suffix,date:day,description:'Synthetic fee '+suffix,amountEuro:'-10.00',status:'BOOKED',kind:'ACTIVITY'};
 expect((await page.request.post('/api/ledger/accounts/'+account.id+'/activity',{data:{revision:account.revision,row,source:'Synthetic fee receipt',acknowledgedOverlap:true}})).ok()).toBeTruthy();
 await page.goto('/');await openWorkspace(page, 'Review & reconcile');await expect(page.getByRole('heading',{name:'Review & reconcile',exact:true})).toBeVisible();
 await page.getByText('Categories, subcategories and monthly budgets',{exact:true}).click();await page.getByLabel('Category name',{exact:true}).fill('Interest & Fees '+suffix);await page.getByLabel('Monthly budget (€)',{exact:true}).fill('20.00');await page.getByLabel('Category change reason',{exact:true}).fill('Synthetic category approval');await page.getByRole('button',{name:'Save category',exact:true}).click();await expect(page.getByRole('status')).toContainText('Category saved');
 await page.getByRole('combobox',{name:'Account filter',exact:true}).selectOption(account.id);await page.getByRole('button',{name:'Review '+row.description,exact:true}).click();
 await page.getByRole('button',{name:'Suggest locally'}).click();
 await expect(page.getByRole('alert')).toContainText('Local suggestions are disabled');
 await page.route('**/api/ledger/review/activity/*/suggest',route=>route.fulfill({json:{
   revision:route.request().postDataJSON().revision, model:'synthetic fixture',
   advice:{merchant:'Synthetic fee',categoryId:null,commitmentId:null,explanation:'The description includes the label.'},
   commitments:{revision:0,options:[]}
 }}));
 await page.getByRole('button',{name:'Suggest locally'}).click();
 await page.getByRole('button',{name:'Dismiss suggestions'}).click();
 await expect(page.getByText('Reviewed merchant: Synthetic fee')).toHaveCount(0);
 await page.getByRole('button',{name:'Suggest locally'}).click();
 await page.getByLabel('Suggested merchant').fill('Reviewed synthetic fee');
 await page.getByRole('button',{name:'Save merchant label'}).click();
 await expect(page.getByText('Reviewed merchant: Reviewed synthetic fee')).toBeVisible();
 await page.getByRole('combobox',{name:'Category',exact:true}).selectOption({label:'Interest & Fees '+suffix});await page.getByRole('combobox',{name:'Review state',exact:true}).selectOption('CONFIRMED');await page.getByLabel('Review reason / source',{exact:true}).fill('Synthetic fee source checked');await page.getByLabel('Offer an exact-description local rule after this confirmed correction',{exact:true}).check();await page.getByRole('button',{name:'Save classification',exact:true}).click();await expect(page.getByRole('status')).toContainText('Classification saved');
 await page.getByText('Review reusable local rules',{exact:true}).click();await page.getByLabel('Rule decision reason',{exact:true}).fill('Synthetic explicitly reviewed exact match');await page.getByRole('button',{name:'Approve rule for '+row.description,exact:true}).click();await expect(page.getByRole('status')).toContainText('Rule choice saved');
 account=(await(await page.request.get('/api/ledger?asOf='+day)).json()).accounts.find((a:{id:string})=>a.id===account.id);
 expect((await page.request.post('/api/ledger/accounts/'+account.id+'/reconcile?asOf='+day,{data:{revision:account.revision,statementEuro:'90.00',source:'Synthetic closing statement',commit:true,acceptAdjustment:false}})).ok()).toBeTruthy();
 // Every old mutation route must honor the protected date.
 expect((await page.request.post('/api/ledger/accounts/'+account.id+'/activity',{data:{revision:account.revision,row:{...row,reference:'late-'+suffix},source:'Synthetic late receipt',acknowledgedOverlap:true}})).status()).toBe(400);
 await page.getByRole('button',{name:'Refresh review',exact:true}).click();await expect(page.getByRole('status')).toContainText('Review refreshed');await page.getByLabel('Review completion / reopening reason',{exact:true}).fill('Synthetic late statement correction');await page.getByRole('button',{name:'Reopen '+account.name,exact:true}).click();await expect(page.getByRole('status')).toContainText('Reconciliation reopened');
 await page.getByRole('button',{name:'Load account audit',exact:true}).click();await expect(page.getByText('Actor: owner',{exact:false}).first()).toBeVisible();await expect(page.getByText('Synthetic late statement correction',{exact:false}).first()).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();await page.screenshot({path:info.outputPath('activity-review.png'),fullPage:true});
 account=(await(await page.request.get('/api/ledger?asOf='+day)).json()).accounts.find((a:{id:string})=>a.id===account.id);
 expect((await page.request.post('/api/ledger/accounts/'+account.id+'/reconcile?asOf='+day,{data:{revision:account.revision,statementEuro:'90.00',source:'Synthetic rechecked closing statement',commit:true,acceptAdjustment:false}})).ok()).toBeTruthy();
});
