import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { effectScope, nextTick, shallowRef } from 'vue'
import type { EffectScope, Ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../../stores/auth'
import type { EligibleTechnician, AssignmentEligible } from '../../api/workorder'
import type { ReadResult } from '../../api/common'
const api=vi.hoisted(()=>({recommend:vi.fn(),eligible:vi.fn()}))
vi.mock('../../api/workorder',async original=>({...await original<typeof import('../../api/workorder')>(),fetchAssignmentCandidates:api.recommend,fetchAssignmentEligible:api.eligible,fetchOrderEligible:api.eligible}))
interface State { page:Ref<number>;keyword:Ref<string>;selected: Ref<EligibleTechnician | null>; ready: Ref<boolean>; notice: Ref<string>; load:()=>Promise<void>; select:(p:EligibleTechnician|null)=>void; recommendationError:Ref<string>; qualificationError:Ref<string> }
const modules=import.meta.glob<{useAssignmentCandidates:(target:()=>{requestId:string},selected:Ref<string>)=>State}>('./useAssignmentCandidates.ts',{eager:true})
const feature=modules['./useAssignmentCandidates.ts']
const person=(id:string):EligibleTechnician=>({id,name:`维修人${id}`,enabled:true,reviewStatus:'APPROVED',eligible:true,refusal:[]})
const success=<T>(data:T):ReadResult<T>=>({kind:'SUCCESS',data,requestId:'test-read'})
const eligible=(people:EligibleTechnician[]):AssignmentEligible=>({candidates:{records:people,total:people.length,page:1,pageSize:100},eligibleTotal:people.length,queriedAt:'2026-10-24T15:59:59.000Z'})
function recommendations(people:EligibleTechnician[]) {const rows=people.map((p,i)=>({...p,W:i,I:0,R:0,A:0,load:i,todayCompleted:0,lastAssignedAt:null,reason:'负荷排序'}));return {top5:rows.slice(0,5),candidates:{records:rows,total:rows.length,page:1,pageSize:20},eligibleTotal:rows.length,preferredTotal:rows.length,queriedAt:'2026-10-24T15:59:59.000Z',businessDate:'2026-10-24'} }
const scopes:EffectScope[]=[]
class Page extends EventTarget {visibilityState='visible'}
let browser:EventTarget,page:Page
const settle=async()=>{await nextTick();await vi.advanceTimersByTimeAsync(0);await nextTick()}
function setup() {const selectedId=shallowRef(''),target=shallowRef('42'),scope=effectScope();scopes.push(scope);let state:State|undefined;scope.run(()=>{state=feature?.useAssignmentCandidates(()=>({requestId:target.value}),selectedId)});expect(state,'B08 recommendation coordinator must exist').toBeDefined();return {state:state!,selectedId,target} }
beforeEach(()=>{vi.useFakeTimers();vi.setSystemTime(new Date('2026-10-24T15:59:59Z'));setActivePinia(createPinia());useAuthStore().setConfirmedUser({id:'10',loginName:'admin',name:'管理员',phone:'13800000000',role:'ADMIN',enabled:true,reviewStatus:null,version:0,capabilities:{canBrowseCampus:true,canCreateRequest:false,canWorkOnTasks:false,canManage:true}});page=new Page();browser=new EventTarget();vi.stubGlobal('window',browser);vi.stubGlobal('document',page);api.eligible.mockReset().mockResolvedValue(success(eligible([person('1'),person('2')])));api.recommend.mockReset().mockResolvedValue(success(recommendations([person('1'),person('2')])));})
afterEach(()=>{scopes.splice(0).forEach(s=>s.stop());vi.useRealTimers();vi.restoreAllMocks();vi.unstubAllGlobals()})
it('reorder_keeps_valid_selection',async()=>{const {state,selectedId}=setup();await settle();state.select(person('2'));const previousSelectedId=selectedId.value;api.recommend.mockResolvedValue(success(recommendations([person('2'),person('1')])));await state.load();const selectedIdAfterReorder=selectedId.value;expect(selectedIdAfterReorder).toBe(previousSelectedId);expect(state.ready.value).toBe(true)})
it('search_and_page_changes_keep_an_eligible_selection_outside_the_visible_results',async()=>{const {state,selectedId}=setup();await settle();state.select(person('2'));state.keyword.value='different person';await settle();expect(selectedId.value).toBe('2');state.page.value=2;await settle();expect(selectedId.value).toBe('2');expect(state.ready.value).toBe(true)})
it('qualification_loss_clears_only_selection_and_blocks_submit',async()=>{const {state,selectedId}=setup();await settle();state.select(person('2'));const otherEditor=shallowRef('保留原因与草稿');api.eligible.mockResolvedValue(success(eligible([person('1')])));await state.load();expect(selectedId.value).toBe('');expect(state.selected.value).toBeNull();expect(state.notice.value).toContain('资格');expect(otherEditor.value).toBe('保留原因与草稿')})
it('recommendation_failure_uses_fresh_complete_eligibility',async()=>{api.recommend.mockResolvedValue({kind:'READ_FAILED',message:'推荐断开'});const {state,selectedId}=setup();await settle();expect(state.recommendationError.value).toContain('推荐');expect(state.qualificationError.value).toBe('');state.select(person('2'));expect(selectedId.value).toBe('2');expect(state.ready.value).toBe(true)})
it('recommendation_timeout_still_yields_fresh_eligibility_before_the_outer_read_deadline',async()=>{
  api.recommend.mockImplementation((_target:unknown,_page:number,_keyword:string,options:{timeoutMs?:number})=>new Promise(resolve=>setTimeout(()=>resolve({kind:'READ_FAILED',message:'推荐超时'}),options.timeoutMs??8000)))
  const {state}=setup();await settle();await vi.advanceTimersByTimeAsync(7000)
  expect(state.ready.value).toBe(true);expect(state.recommendationError.value).toContain('超时');state.select(person('2'));expect(state.selected.value?.id).toBe('2')
})
it('qualification_timeout_reports_only_qualification_failure_when_recommendation_succeeds',async()=>{
  api.eligible.mockImplementation((_id:string,_page:number,_keyword:string,options:{timeoutMs?:number})=>new Promise(resolve=>setTimeout(()=>resolve({kind:'READ_FAILED',message:'资格超时'}),options.timeoutMs??8000)))
  const {state}=setup();await settle();await vi.advanceTimersByTimeAsync(7000)
  expect(state.ready.value).toBe(false);expect(state.qualificationError.value).toContain('超时');expect(state.recommendationError.value).toBe('')
})
it('qualification_failure_never_authorizes_cached_submission',async()=>{const {state,selectedId}=setup();await settle();state.select(person('2'));api.eligible.mockResolvedValue({kind:'READ_FAILED',message:'资格断开'});api.recommend.mockResolvedValue({kind:'READ_FAILED',message:'推荐断开'});await state.load();expect(state.ready.value).toBe(false);expect(state.qualificationError.value).toContain('资格');expect(selectedId.value).toBe('2');state.select(person('1'));expect(selectedId.value).toBe('2')})
it('late_responses_cannot_cross_target_or_identity',async()=>{let release!:(r:ReadResult<AssignmentEligible>)=>void;api.eligible.mockImplementationOnce(()=>new Promise(r=>{release=r}));const {state,target}=setup();await settle();target.value='43';await settle();release(success(eligible([person('9')])));await settle();state.select(person('9'));expect(state.selected.value).toBeNull();useAuthStore().clearIdentity();await settle();expect(state.ready.value).toBe(false);expect(state.selected.value).toBeNull()})
it('ten_second_refresh_focus_and_shanghai_midnight_reload',async()=>{const {state}=setup();await settle();await vi.advanceTimersByTimeAsync(10_000);expect(api.recommend).toHaveBeenCalledTimes(2);browser.dispatchEvent(new Event('focus'));await settle();expect(api.recommend).toHaveBeenCalledTimes(3);page.visibilityState='hidden';page.dispatchEvent(new Event('visibilitychange'));await vi.advanceTimersByTimeAsync(30_000);expect(api.recommend).toHaveBeenCalledTimes(3);page.visibilityState='visible';page.dispatchEvent(new Event('visibilitychange'));await settle();expect(api.recommend).toHaveBeenCalledTimes(4);expect(state.ready.value).toBe(true)})
