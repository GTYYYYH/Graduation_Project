import {businessRead,isInstant,object} from './business'
import {isId} from './account'
import type {BusinessHistoryItemVo} from './account'
export interface WorkbenchVo {counts:Record<string,number>;items:BusinessHistoryItemVo[];queriedAt:string}
export const fetchWorkbench=()=>businessRead('/api/workbench',(v:unknown):v is WorkbenchVo=>object(v)&&object(v.counts)&&Object.values(v.counts).every(n=>typeof n==='number'&&Number.isSafeInteger(n)&&n>=0)&&Array.isArray(v.items)&&v.items.length<=20&&v.items.every(item=>object(item)&&['REPAIR_REQUEST','WORK_ORDER'].includes(String(item.resourceType))&&isId(item.id)&&typeof item.businessNo==='string'&&typeof item.equipmentName==='string'&&typeof item.status==='string'&&isInstant(item.createdAt)&&item.canViewDetail===true)&&isInstant(v.queriedAt))
