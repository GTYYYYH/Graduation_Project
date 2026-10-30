import { businessRead, isInstant, isPage, object, queryString } from './business'
import { isId } from './account'
import type { CatalogVo } from './account'
import type { Id, PageResult, RequestOptions } from './common'
export type LocationCategory='FAULT_PROCESSING'|'PENDING_ACCEPTANCE'|'NEEDS_SUPPLEMENT'|'PENDING_REVIEW'|'NONE'
export interface StageCounts {pendingAccept:number;reassignPending:number;inProgress:number;rework:number;pendingAcceptance:number;needsSupplement:number;pendingReview:number}
export interface LocationSummary {locationId:Id;category:LocationCategory;counts:StageCounts;visibleTotal:number;queriedAt:string}
export interface CampusSummaries {configVersion:string;locations:LocationSummary[];queriedAt:string}
export interface LocationItem {resourceType:'REPAIR_REQUEST'|'WORK_ORDER';id:Id;businessNo:string;equipmentName:string;status:string;stage:keyof StageCounts;createdAt:string;allowedActions:string[]}
export interface LocationItems {location:CatalogVo['locations'][number];counts:StageCounts;category:LocationCategory;visibleTotal:number;items:PageResult<LocationItem>;queriedAt:string}
export const categoryNames:Record<LocationCategory,string>={FAULT_PROCESSING:'故障处理中',PENDING_ACCEPTANCE:'待验收',NEEDS_SUPPLEMENT:'退回待补充',PENDING_REVIEW:'待审核',NONE:'当前无可查看事项'}
export const stageNames:Record<keyof StageCounts,string>={pendingAccept:'待接单',reassignPending:'待重派',inProgress:'处理中',rework:'返工',pendingAcceptance:'待验收',needsSupplement:'待补充',pendingReview:'待审核'}
const stages=Object.keys(stageNames) as (keyof StageCounts)[]
const count=(v:unknown):v is number=>typeof v==='number' && Number.isSafeInteger(v) && v>=0
function validCounts(v:unknown):v is StageCounts {return object(v) && stages.every(k=>count(v[k]))}
function category(c:StageCounts):LocationCategory {return c.pendingAccept+c.reassignPending+c.inProgress+c.rework>0?'FAULT_PROCESSING':c.pendingAcceptance>0?'PENDING_ACCEPTANCE':c.needsSupplement>0?'NEEDS_SUPPLEMENT':c.pendingReview>0?'PENDING_REVIEW':'NONE'}
function totals(v:Record<string,unknown>):boolean {return validCounts(v.counts) && count(v.visibleTotal) && Object.values(v.counts).reduce((a,b)=>a+b,0)===v.visibleTotal && category(v.counts)===v.category}
export function isSummaries(v:unknown):v is CampusSummaries {
  return object(v) && typeof v.configVersion==='string' && isInstant(v.queriedAt) && Array.isArray(v.locations)
    && v.locations.every(l=>object(l) && isId(l.locationId) && totals(l) && Number(l.visibleTotal)>0 && l.queriedAt===v.queriedAt)
    && new Set(v.locations.map(l=>l.locationId)).size===v.locations.length
}
function item(v:unknown):v is LocationItem {
  if(!object(v) || !isId(v.id) || typeof v.businessNo!=='string' || typeof v.equipmentName!=='string' || !isInstant(v.createdAt) || !Array.isArray(v.allowedActions)) return false
  const request=v.resourceType==='REPAIR_REQUEST', order=v.resourceType==='WORK_ORDER'
  const allowed=request?['SUPPLEMENT_REQUEST','WITHDRAW_REQUEST','RETURN_REQUEST','CLOSE_DUPLICATE','APPROVE_AND_ASSIGN']:['ACCEPT','REFUSE','ASSIGN','PROGRESS','SUBMIT_RESULT','ACCEPTANCE']
  const expected:Record<string,string[]>={PENDING_REVIEW:['pendingReview'],NEEDS_SUPPLEMENT:['needsSupplement'],PENDING_ACCEPT:['pendingAccept'],REASSIGN_PENDING:['reassignPending'],IN_PROGRESS:['inProgress','rework'],PENDING_ACCEPTANCE:['pendingAcceptance']}
  return (request && ['PENDING_REVIEW','NEEDS_SUPPLEMENT'].includes(String(v.status)) || order && ['PENDING_ACCEPT','REASSIGN_PENDING','IN_PROGRESS','PENDING_ACCEPTANCE'].includes(String(v.status)))
    && !!expected[String(v.status)]?.includes(String(v.stage)) && v.allowedActions.every(a=>typeof a==='string' && allowed.includes(a))
}
function locationItems(v:unknown):v is LocationItems {
  if(!object(v) || !object(v.location)) return false
  const l=v.location
  return isId(l.id) && isId(l.floorId) && typeof l.code==='string' && typeof l.name==='string' && ['ROOM','AREA'].includes(String(l.type)) && typeof l.use==='string' && typeof l.reportable==='boolean' && typeof l.canCreateRequest==='boolean' && count(l.displayOrder)
    && totals(v) && isPage(v.items,item) && v.items.total<=Number(v.visibleTotal) && isInstant(v.queriedAt)
}
export const fetchCampusSummaries=(query:{buildingId?:Id;floorId?:Id},options:RequestOptions={})=>businessRead(`/api/campus/summaries?${queryString(query)}`,isSummaries,options)
export const fetchLocationItems=(id:Id,query:{page?:number;pageSize?:number;category?:Exclude<LocationCategory,'NONE'>},options:RequestOptions={})=>businessRead(`/api/campus/locations/${encodeURIComponent(id)}/items?${queryString(query)}`,(v):v is LocationItems=>locationItems(v) && v.location.id===id,options)
