import type { Position } from './ledger';
import type { Inputs } from './forecast';
export type Board={publisherId:string;publisherName:string;forecastRevision:number;forecast:Inputs;publishedAt:string};
export type MemberReport={asOf:string;scope:string;position:Position;recordedCashMinor:number|null;evidenceState:string;spendingRoomMinor:number|null;commitmentsMinor:number|null;reserveMinor:number|null;purchaseReservationsMinor:number;board:Board|null;boardRevision:number;canPublish:boolean;token:string;warnings:string[];trendState:string};
export type Purchase={title:string;category:string;amountMinor:number;date:string;state:string;requestDecision:string;sharedRequest:boolean;reviewerId:string|null;sourceIds:string[];accountId:string|null;entryId:string|null;decisionReason:string};
export type PurchaseView={id:string;ownerId:string;revision:number;purchase:Purchase;actualMinor:number|null;varianceMinor:number|null;matchValid:boolean;matchWarning:string|null;canDecide:boolean;assessment:string};
export type MemberEvent={purchaseId:string;actor:string;action:string;at:string;snapshot:Purchase};
export type MemberWorkspace={report:MemberReport;purchases:PurchaseView[];preferences:{revision:number;values:{categories:string[];dashboard:string;notifications:boolean;seenAt:string}};notifications:{id:string;purchaseId:string;action:string;at:string}[]};
export async function member<T=MemberWorkspace>(path='',body?:unknown,method=body===undefined?'GET':'POST'):Promise<T>{
  const saving=method!=='GET'&&method!=='HEAD';let r:Response;
  try{r=await fetch('/api/member'+path,{method,headers:body===undefined?undefined:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),cache:'no-store'});}
  catch{throw Error(saving?'Connection interrupted; the save may have completed. Refresh before retrying.':'Could not load the member workspace. Check the connection and refresh.');}
  if(r.status>=500)throw Error(saving?'Could not save. Refresh to check current records before retrying.':'Could not load the member workspace. Refresh to retry.');
  if(!r.ok){let message=await r.text();try{message=JSON.parse(message).error||message;}catch{/* access filter uses text */}throw Error(message||'Member request failed');}
  return r.json() as Promise<T>;
}
