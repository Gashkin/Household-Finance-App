export type Rule={name:string;accountId:string;categoryId:string;direction:string;certainty:string;amountType:string;amountEuro:string;frequency:string;anchor:string;end:string|null;payrollId:string|null;offsetDays:number;counterpartId:string|null};
export type Version={from:string;cancelled:boolean;rule:Rule};
export type Schedule={id:string;revision:number;versions:Version[]};
export type Edit={id:string;revision:number;effectiveFrom:string;cancelled:boolean;rule:Rule};
export type PayrollRule={name:string;day:number;weekend:number[];adjustment:string;holidays:string[];exceptions:Record<string,string>};
export type Payroll={id:string;revision:number;versions:{from:string;rule:PayrollRule}[]};
export type State={revision:number;schedules:Schedule[];payrolls:Payroll[];operations:Record<string,string>};
export type Account={id:string;name:string;revision:number};
export type Workspace={today:string;state:State;accounts:Account[];categories:{id:string;name:string;retired:boolean}[]};
export type Occurrence={id:string;scheduleId:string;nominalDate:string;date:string;name:string;accountId:string;categoryId:string;direction:string;certainty:string;amountType:string;amountMinor:number;state:string;entryId:string|null;actualMinor:number|null;suppressed:boolean;warnings:string[]};
export type Candidate={id:string;accountId:string;date:string;description:string;amountMinor:number;status:string;accountRevision:number};
export type Point={date:string;openingMinor:number;expensesMinor:number;incomeMinor:number;lowMinor:number;closingMinor:number;occurrences:string[]};
export type CashLine={id:string|null;name:string;startingMinor:number;lowestMinor:number;lowestDate:string;closingMinor:number;shortfallDate:string|null;days:Point[]};
export type Outlook={baseline:string;end:string;currentEvidence:boolean;warnings:string[];occurrences:Occurrence[];household:CashLine;accounts:CashLine[]};
export type CsvPreview={revision:number;applicable:boolean;rows:{row:number;edit:Edit|null;errors:string[]}[]};
export class CommitmentError extends Error {constructor(message:string,public status:number){super(message);}}
export async function commitments<T>(path='',body?:unknown):Promise<T>{
 let response:Response;
 try{response=await fetch('/api/forecast/commitments'+path,{method:body===undefined?'GET':'POST',headers:body===undefined?undefined:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),cache:'no-store'});}
 catch{throw new CommitmentError('Save status may be unknown. Recheck the operation before retrying; your draft is retained.',0);}
 if(!response.ok){let message=await response.text();try{message=JSON.parse(message).error||message;}catch{/* plain access response */}throw new CommitmentError(response.status>=500?'Local service unavailable. Recheck before retrying.':message,response.status);}
 return response.json();
}
