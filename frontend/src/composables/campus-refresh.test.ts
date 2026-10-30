import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { effectScope, nextTick, shallowRef } from 'vue'
import type { EffectScope, Ref, MaybeRefOrGetter } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../stores/auth'
import type { ReadResult } from '../api/common'
interface State { data: Ref<string | null>; loading: Ref<boolean>; stale: Ref<boolean>; lastSuccessAt: Ref<string>; message: Ref<string>; load: () => Promise<void> }
const modules = import.meta.glob<{ useCampusRefresh: (target: MaybeRefOrGetter<string | null>, loader: (signal: AbortSignal) => Promise<ReadResult<string>>, forbidden?: () => void) => State; requestCampusRefresh: () => void }>('./useCampusRefresh.ts', { eager: true })
const feature = modules['./useCampusRefresh.ts']
class Page extends EventTarget { visibilityState = 'visible' }
let page: Page, browser: EventTarget
const scopes: EffectScope[] = []
const success = (data: string): ReadResult<string> => ({ kind: 'SUCCESS', data, requestId: 'controlled-read' })
const failure = (): ReadResult<string> => ({ kind: 'READ_FAILED', message: '连接失败' })
const denied = (code: string): ReadResult<string> => ({ kind: 'HTTP_ERROR', status: 403, response: { code, message: code, data: null, requestId: 'controlled-denial' } })
const settle = async () => { await nextTick(); await vi.advanceTimersByTimeAsync(0); await nextTick() }
function setup(loader: (signal: AbortSignal) => Promise<ReadResult<string>>, forbidden?: () => void) {
  const target=shallowRef<string | null>('floor/1'), scope=effectScope(); let state: State | undefined
  scope.run(() => { state=feature?.useCampusRefresh(target,loader,forbidden) }); scopes.push(scope)
  return { target, scope, state }
}
beforeEach(() => {
  vi.useFakeTimers(); vi.setSystemTime(new Date('2026-10-18T13:00:00Z')); setActivePinia(createPinia())
  useAuthStore().setConfirmedUser({ id:'10',loginName:'owner',name:'合成用户',phone:'13800000000',role:'REPORTER',enabled:true,reviewStatus:null,version:0,capabilities:{canBrowseCampus:true,canCreateRequest:true,canManage:false,canWorkOnTasks:false} })
  page=new Page(); browser=new EventTarget(); vi.stubGlobal('document',page); vi.stubGlobal('window',browser)
})
afterEach(() => { scopes.splice(0).forEach(scope=>scope.stop()); vi.useRealTimers(); vi.restoreAllMocks(); vi.unstubAllGlobals() })
it('failed_read_does_not_advance_success_time', async () => {
  const loader=vi.fn().mockResolvedValueOnce(success('last authorized records')).mockResolvedValue(failure())
  const { state }=setup(loader); await settle()
  expect(state?.data.value).toBe('last authorized records')
  const previousSuccessAt=state!.lastSuccessAt.value
  await vi.advanceTimersByTimeAsync(10_000)
  const lastSuccessAt=state!.lastSuccessAt.value
  expect(lastSuccessAt).toBe(previousSuccessAt)
  expect(state!.data.value).toBe('last authorized records'); expect(state!.stale.value).toBe(true)
})
it('first_failure_is_unknown_instead_of_an_empty_success', async () => {
  const { state }=setup(async()=>failure()); await settle()
  expect(state?.data.value).toBeNull(); expect(state!.lastSuccessAt.value).toBe(''); expect(state!.message.value).toContain('连接失败')
})
it('visible_ten_seconds_no_overlap_and_eight_second_read_timeout', async () => {
  const signals: AbortSignal[]=[]; const loader=vi.fn((signal:AbortSignal)=>{signals.push(signal); return new Promise<ReadResult<string>>(()=>{})})
  const { state }=setup(loader); await settle(); expect(state?.loading.value).toBe(true)
  await vi.advanceTimersByTimeAsync(7999); expect(signals[0]?.aborted).toBe(false)
  await vi.advanceTimersByTimeAsync(1); expect(signals[0]?.aborted).toBe(true); expect(state!.loading.value).toBe(false); expect(state!.lastSuccessAt.value).toBe('')
  await vi.advanceTimersByTimeAsync(2000); expect(loader).toHaveBeenCalledTimes(2)
})
it('background_pauses_and_resume_immediately_reads_then_unmount_releases_everything', async () => {
  const loader=vi.fn().mockResolvedValue(success('authorized'))
  const { state,scope }=setup(loader); await settle(); expect(state?.data.value).toBe('authorized')
  page.visibilityState='hidden'; page.dispatchEvent(new Event('visibilitychange')); await vi.advanceTimersByTimeAsync(60_000); expect(loader).toHaveBeenCalledTimes(1)
  page.visibilityState='visible'; page.dispatchEvent(new Event('visibilitychange')); await settle(); expect(loader).toHaveBeenCalledTimes(2)
  scope.stop(); browser.dispatchEvent(new Event('focus')); page.dispatchEvent(new Event('visibilitychange')); feature!.requestCampusRefresh(); await vi.advanceTimersByTimeAsync(30_000); expect(loader).toHaveBeenCalledTimes(2)
})
it('identity_target_and_request_generations_drop_late_responses', async () => {
  const releases: ((result:ReadResult<string>)=>void)[]=[]
  const loader=vi.fn(()=>new Promise<ReadResult<string>>(resolve=>releases.push(resolve)))
  const {state,target}=setup(loader); await settle(); target.value='floor/2'; await settle()
  releases[0]!(success('old floor')); await settle(); expect(state?.data.value).toBeNull()
  releases[1]!(success('new floor')); await settle(); expect(state!.data.value).toBe('new floor')
  const reading=state!.load(); useAuthStore().clearIdentity(); await settle(); releases[2]!(success('previous account')); await reading; expect(state!.data.value).toBeNull()
})
it('confirmed_invalidation_queues_one_fresh_read_instead_of_applying_the_old_snapshot', async () => {
  let release!: (result:ReadResult<string>)=>void
  const loader=vi.fn().mockResolvedValueOnce(success('before')).mockImplementationOnce(()=>new Promise<ReadResult<string>>(resolve=>{release=resolve})).mockResolvedValue(success('after'))
  const {state}=setup(loader); await settle(); const reading=state!.load(); await settle()
  feature!.requestCampusRefresh(); feature!.requestCampusRefresh(); await settle(); expect(loader).toHaveBeenCalledTimes(2)
  release(success('before commit')); await reading; await settle(); expect(loader).toHaveBeenCalledTimes(3); expect(state!.data.value).toBe('after')
})
it('awaiting_a_read_waits_for_its_queued_fresh_replacement',async()=>{
  const releases:((result:ReadResult<string>)=>void)[]=[]
  const {state}=setup(()=>new Promise<ReadResult<string>>(resolve=>releases.push(resolve)))
  await settle();let settled=false
  const reading=state!.load().then(()=>{settled=true})
  browser.dispatchEvent(new Event('focus'));releases[0]!(success('obsolete'))
  await settle();expect(releases).toHaveLength(2);expect(state!.loading.value).toBe(true);expect(settled).toBe(false)
  releases[1]!(success('fresh'));await reading
  expect(state!.data.value).toBe('fresh');expect(state!.loading.value).toBe(false);expect(settled).toBe(true)
})
it.each(['RECORD_FORBIDDEN','TECHNICIAN_NOT_APPROVED','REVIEW_REQUIRED'])('clears_unauthorized_contents_but_keeps_identity_for_%s', async code=> {
  const clear=vi.fn(),loader=vi.fn().mockResolvedValueOnce(success('private')).mockResolvedValue(denied(code))
  const {state}=setup(loader,clear); await settle(); await state!.load(); expect(state!.data.value).toBeNull(); expect(clear).toHaveBeenCalledTimes(1); expect(useAuthStore().currentUser?.id).toBe('10')
})
it('csrf_denial_retains_authorized_data_as_stale_and_does_not_destroy_editors', async()=> {
  const clear=vi.fn(),loader=vi.fn().mockResolvedValueOnce(success('private')).mockResolvedValue(denied('CSRF_INVALID'))
  const {state}=setup(loader,clear); await settle(); await state!.load(); expect(state!.data.value).toBe('private'); expect(state!.stale.value).toBe(true); expect(clear).not.toHaveBeenCalled()
})
