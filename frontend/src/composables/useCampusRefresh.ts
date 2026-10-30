import { computed, onScopeDispose, shallowRef, toValue, watch } from 'vue'
import type { MaybeRefOrGetter } from 'vue'
import type { ReadResult } from '../api/common'
import { feedback } from '../api/business'
import { useAuthStore } from '../stores/auth'

const confirmedEvent = 'campus:business-confirmed'
/** Only confirmed service receipts invalidate reads; UNKNOWN never announces success. */
export function requestCampusRefresh() { if (typeof window !== 'undefined') window.dispatchEvent(new Event(confirmedEvent)) }
export interface CampusRefreshOptions<T = unknown> { poll?: boolean; immediate?: boolean; reconcile?: (previous: T | null, next: T) => T }

/** Each instance owns one target and one read. Independent instances can read in parallel. */
export function useCampusRefresh<T>(target: MaybeRefOrGetter<string | null>, loader: (signal: AbortSignal) => Promise<ReadResult<T>>, onForbidden?: () => void, options: CampusRefreshOptions<T> = {}) {
  const auth=useAuthStore(), data=shallowRef<T | null>(null), loading=shallowRef(false), message=shallowRef(''), stale=shallowRef(false), lastSuccessAt=shallowRef('')
  const browser=typeof window!=='undefined' && typeof document!=='undefined'
  const visible=shallowRef(!browser || document.visibilityState!=='hidden')
  const enabled=computed(()=>auth.businessRouteGranted && !!toValue(target))
  let generation=0, disposed=false, queued=false, active: {controller:AbortController; promise:Promise<void>; obsolete:boolean} | null=null
  let timer:ReturnType<typeof setInterval> | null=null
  function stopTimer() { if(timer!==null) clearInterval(timer); timer=null }
  function cancel() { generation++; queued=false; const previous=active; active=null; previous?.controller.abort(); loading.value=false }
  function clear() { cancel(); data.value=null; stale.value=false; lastSuccessAt.value=''; message.value='' }
  async function load():Promise<void> {
    if(disposed || !enabled.value || !visible.value) return
    if(active) return active.promise
    const ticket=++generation, epoch=auth.identityEpoch, key=toValue(target), controller=new AbortController()
    const own={controller,promise:Promise.resolve(),obsolete:false}; active=own; loading.value=true; message.value=''
    const current=()=>!disposed && active===own && ticket===generation && epoch===auth.identityEpoch && key===toValue(target) && enabled.value && visible.value
    own.promise=(async()=> {
      let timeout:ReturnType<typeof setTimeout> | undefined
      let aborted:()=>void=()=>{}
      try {
        const timed=new Promise<ReadResult<T>>(resolve=> {
          aborted=()=>resolve({kind:'READ_FAILED',message:'读取已取消，请重试'})
          controller.signal.addEventListener('abort',aborted,{once:true})
          timeout=setTimeout(()=>{resolve({kind:'READ_FAILED',message:'读取超过8秒，请检查连接后重试'});controller.abort()},8000)
        })
        const result=await Promise.race([Promise.resolve().then(()=>loader(controller.signal)).catch(()=>({kind:'READ_FAILED' as const,message:'读取失败，请检查连接后重试'})),timed])
        if(!current() || own.obsolete) return
        if(result.kind==='SUCCESS') { data.value=options.reconcile ? options.reconcile(data.value,result.data) : result.data; stale.value=false; lastSuccessAt.value=new Date().toLocaleString('zh-CN',{timeZone:'Asia/Shanghai'}); return }
        const lost=result.kind==='HTTP_ERROR' && ((result.status===403 && ['FORBIDDEN','RECORD_FORBIDDEN','TECHNICIAN_NOT_APPROVED','REVIEW_REQUIRED'].includes(result.response.code)) || (result.status===404 && result.response.code==='RESOURCE_NOT_FOUND'))
        if(lost) { data.value=null; stale.value=false; lastSuccessAt.value=''; onForbidden?.() }
        else stale.value=data.value!==null
        message.value=feedback(result)
      } finally {
        if(timeout!==undefined) clearTimeout(timeout)
        controller.signal.removeEventListener('abort',aborted)
        if(active===own) { active=null; loading.value=false; if(queued) {queued=false;await load()} }
      }
    })()
    return own.promise
  }
  function invalidate() {
    if(disposed || !enabled.value || !visible.value) return
    stale.value=data.value!==null
    if(active) {active.obsolete=true;queued=true} else void load()
  }
  function visibilityChanged() {
    visible.value=document.visibilityState!=='hidden'
    if(!visible.value) {cancel();stopTimer();stale.value=data.value!==null}
  }
  const scopeKey=computed(()=>`${auth.identityEpoch}/${auth.currentUser?.id ?? ''}/${toValue(target) ?? ''}`)
  watch(scopeKey,clear,{flush:'sync'})
  watch([enabled,scopeKey,visible],()=> {
    stopTimer()
    if(!enabled.value || !visible.value) {if(!enabled.value)clear();return}
    if(options.poll!==false && browser) timer=setInterval(()=>{if(!active)void load()},10_000)
    if(options.immediate!==false) void load()
  },{immediate:true})
  if(browser) {document.addEventListener('visibilitychange',visibilityChanged);window.addEventListener('focus',invalidate);window.addEventListener(confirmedEvent,invalidate)}
  onScopeDispose(()=> {disposed=true;stopTimer();clear();if(browser){document.removeEventListener('visibilitychange',visibilityChanged);window.removeEventListener('focus',invalidate);window.removeEventListener(confirmedEvent,invalidate)}})
  return {data,loading,message,stale,lastSuccessAt,clear,load,invalidate}
}
