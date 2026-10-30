import {afterEach,beforeEach,expect,it,vi} from 'vitest'
import {createPinia,setActivePinia} from 'pinia'
import {fetchCampusSummaries,fetchLocationItems} from './campus'
const at='2026-10-18T13:00:00.000Z'
const counts={pendingAccept:0,reassignPending:0,inProgress:0,rework:0,pendingAcceptance:0,needsSupplement:0,pendingReview:1}
const summary=()=>({configVersion:'campus-v1',locations:[{locationId:'9007199254740993',category:'PENDING_REVIEW',counts:{...counts},visibleTotal:1,queriedAt:at}],queriedAt:at})
function answer(data:unknown) {vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({code:'OK',message:'查询成功',data,requestId:'campus-api-test'}),{headers:{'Content-Type':'application/json'}})))}
beforeEach(()=>setActivePinia(createPinia()))
afterEach(()=>vi.unstubAllGlobals())
it('reads_decimal_ids_without_number_conversion_and_forwards_abort_signal',async()=> {
  answer(summary());const controller=new AbortController(),result=await fetchCampusSummaries({floorId:'9007199254740994'},{signal:controller.signal})
  expect(result.kind).toBe('SUCCESS');if(result.kind==='SUCCESS')expect(result.data.locations[0]?.locationId).toBe('9007199254740993')
  expect(String(vi.mocked(fetch).mock.calls[0]?.[0])).toBe('/api/campus/summaries?floorId=9007199254740994')
  expect(vi.mocked(fetch).mock.calls[0]?.[1]?.cache).toBe('no-store')
})
it.each(['sum','category','duplicate','time','id'] as const)('rejects_invalid_%s_instead_of_turning_it_into_zero',async mode=> {
  const value=summary()
  if(mode==='sum')value.locations[0]!.visibleTotal=2
  if(mode==='category')value.locations[0]!.category='FAULT_PROCESSING'
  if(mode==='duplicate')value.locations.push({...value.locations[0]!})
  if(mode==='time')value.locations[0]!.queriedAt='2026-10-18T13:00:01.000Z'
  if(mode==='id')value.locations[0]!.locationId='9223372036854775808'
  answer(value);expect((await fetchCampusSummaries({})).kind).toBe('READ_FAILED')
})
it('location_items_bind_to_requested_location_and_reject_completed_or_inconsistent_stage',async()=> {
  const value={location:{id:'22',code:'wb1-f1-r101',floorId:'21',type:'ROOM',use:'DORM_ROOM',name:'101',reportable:true,canCreateRequest:true,displayOrder:0},counts,category:'PENDING_REVIEW',visibleTotal:1,items:{records:[{resourceType:'REPAIR_REQUEST',id:'44',businessNo:'RR-44',equipmentName:'投影仪无需几何',status:'PENDING_REVIEW',stage:'pendingReview',createdAt:at,allowedActions:['WITHDRAW_REQUEST']}],total:1,page:1,pageSize:20},queriedAt:at}
  answer(value);expect((await fetchLocationItems('22',{})).kind).toBe('SUCCESS')
  answer(value);expect((await fetchLocationItems('23',{})).kind).toBe('READ_FAILED')
  value.items.records[0]!.status='COMPLETED';answer(value);expect((await fetchLocationItems('22',{})).kind).toBe('READ_FAILED')
})
