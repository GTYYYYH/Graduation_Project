import { isId } from '../api/account'
import type { LocationItem } from '../api/campus'
export function campusItemPath(item: Pick<LocationItem, 'resourceType' | 'id' | 'allowedActions'>, manageRequests = false): string {
  return item.resourceType === 'WORK_ORDER' ? `/work-orders/${item.id}`
    : manageRequests || item.allowedActions.includes('APPROVE_AND_ASSIGN') ? `/admin/requests/${item.id}` : `/requests/${item.id}`
}
/** Only known local business destinations may be used as return links. */
export function businessReturnTo(value: unknown, fallback: string): string {
  if(typeof value!=='string' || value.length>4096 || /[\\\r\n#]/.test(value)) return fallback
  if(/^\/admin\/statistics(?:\/details)?(?:\?|$)/.test(value)) {
    const url=new URL(value,'http://campus.local'), keys=new Set<string>()
    const detail=url.pathname==='/admin/statistics/details'
    for(const [key,item] of url.searchParams) {
      if(keys.has(key))return fallback;keys.add(key)
      if(key==='month') {if(!/^[1-9][0-9]{3}-(0[1-9]|1[0-2])$/.test(item))return fallback}
      else if(key==='tab') {if(!['overview','analysis'].includes(item))return fallback}
      else if(!detail)return fallback
      else if(key==='metric') {if(!['COMPLETED','RESPONSE_DURATION','REPAIR_DURATION','DAILY_COMPLETED','EQUIPMENT_FREQUENCY','CURRENT_STATE'].includes(item))return fallback}
      else if(key==='date') {if(!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(item))return fallback}
      else if(key==='equipmentId') {if(!isId(item))return fallback}
      else if(key==='unlinkedEquipment') {if(item!=='true')return fallback}
      else if(key==='status') {if(!['PENDING_ACCEPT','REASSIGN_PENDING','IN_PROGRESS','PENDING_ACCEPTANCE','COMPLETED'].includes(item))return fallback}
      else if(key==='keyword') {if(item.length>200 || /[\r\n\\]/.test(item))return fallback}
      else if(key==='page') {if(!/^[1-9][0-9]{0,6}$/.test(item))return fallback}
      else if(key==='pageSize') {if(!/^[1-9][0-9]{0,2}$/.test(item) || Number(item)>100)return fallback}
      else return fallback
    }
    return value
  }
  if(/^\/campus(?:\?|$)/.test(value)) {
    const url=new URL(value,'http://campus.local')
    const keys=new Set<string>()
    for(const [key,item] of url.searchParams) {
      if(keys.has(key)) return fallback; keys.add(key)
      if(['buildingId','floorId','locationId','itemsLocationId'].includes(key)) {if(!isId(item))return fallback}
      else if(key==='observation') {if(!['OVERVIEW','BUILDING','FLOOR','DOOR','INTERIOR'].includes(item))return fallback}
      else if(key==='page') {if(!/^[1-9][0-9]{0,6}$/.test(item))return fallback}
      else return fallback
    }
    return value
  }
  return /^\/(?:requests|work-orders|equipment|tasks|list|admin\/requests|admin\/work-orders|admin\/personnel)(?:\/[1-9][0-9]*)?(?:\?[^\r\n#]*)?$/.test(value) ? value : fallback
}
export function relatedBusinessTo(target: string, current: string, previous: unknown) {
  const back = businessReturnTo(previous, '')
  if(back.startsWith('/campus')) return {path:target,query:{returnTo:back}}
  return back.split('?')[0] === target ? back : { path: target, query: { returnTo: current } }
}

