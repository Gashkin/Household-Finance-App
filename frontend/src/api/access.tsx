import { createContext, useContext } from 'react';
export type Identity = { id:string; username:string; roles:string[]; custodian:boolean; canRead:boolean; canEdit:boolean; scope:string };
export const IdentityContext=createContext<Identity|null>(null);
export function useIdentity(){return useContext(IdentityContext);}
export async function accessRequest<T>(path:string,body?:unknown,method=body===undefined?'GET':'POST'):Promise<T>{
  const r=await fetch('/api/access/'+path,{method,headers:body===undefined?undefined:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});
  if(!r.ok){let message=await r.text();try{const value=JSON.parse(message);message=value.error||value.message||message;}catch{/* bounded server text */}throw Error(message||'Access request failed');}
  return r.status===204?undefined as T:r.json() as Promise<T>;
}
