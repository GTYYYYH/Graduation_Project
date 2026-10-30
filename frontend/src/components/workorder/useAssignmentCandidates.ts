import { computed, shallowRef, watch } from 'vue'
import type { Ref } from 'vue'
import { fetchAssignmentCandidates, fetchAssignmentEligible, fetchOrderEligible } from '../../api/workorder'
import type { AssignmentTarget, AssignmentCandidates, AssignmentEligible, EligibleTechnician } from '../../api/workorder'
import type { ReadResult } from '../../api/common'
import { feedback } from '../../api/business'
import { useCampusRefresh } from '../../composables/useCampusRefresh'
import { useAuthStore } from '../../stores/auth'

interface Snapshot { recommendation: AssignmentCandidates | null; qualification: AssignmentEligible | null; recommendationError: string; qualificationError: string }
/** Owns reads and selection validity only. Editors and mutation drafts remain in their parents. */
export function useAssignmentCandidates(target: () => AssignmentTarget, selectedId: Ref<string>) {
  const auth=useAuthStore()
  const page=shallowRef(1),keyword=shallowRef(''),selected=shallowRef<EligibleTechnician|null>(null),notice=shallowRef('')
  const targetKey=computed(()=>JSON.stringify(target()))
  async function qualifications(t:AssignmentTarget,signal:AbortSignal):Promise<ReadResult<AssignmentEligible>> {
    const read=(p:number)=>t.workOrderId !== undefined ? fetchOrderEligible(t.workOrderId,t.context,p,'',{signal,timeoutMs:6000},100) : fetchAssignmentEligible(t.requestId,p,'',{signal,timeoutMs:6000},100)
    const first=await read(1);if(first.kind!=='SUCCESS')return first
    const records=[...first.data.candidates.records]
    for(let p=2;records.length<first.data.candidates.total;p++) {
      const next=await read(p);if(next.kind!=='SUCCESS')return next
      if(signal.aborted || next.data.eligibleTotal!==first.data.eligibleTotal || !next.data.candidates.records.length) return {kind:'READ_FAILED',message:'资格名单正在变化，请重新核对'}
      records.push(...next.data.candidates.records)
    }
    if(new Set(records.map(r=>r.id)).size!==records.length)return {kind:'READ_FAILED',message:'资格名单正在变化，请重新核对'}
    return {...first,data:{...first.data,candidates:{...first.data.candidates,records}}}
  }
  const read=useCampusRefresh<Snapshot>(()=>`${targetKey.value}/${page.value}/${keyword.value}`,async signal=>{
    const t=target(),p=page.value,k=keyword.value
    // Leave time inside the shared 8s read deadline to publish a successful qualification fallback.
    const [recommendation,qualification]=await Promise.all([fetchAssignmentCandidates(t,p,k,{signal,timeoutMs:6000}),qualifications(t,signal)])
    return {kind:'SUCCESS',requestId:'assignment-read',data:{recommendation:recommendation.kind==='SUCCESS'?recommendation.data:null,qualification:qualification.kind==='SUCCESS'?qualification.data:null,recommendationError:recommendation.kind==='SUCCESS'?'':feedback(recommendation),qualificationError:qualification.kind==='SUCCESS'?'':feedback(qualification)}}
  })
  const ready=computed(()=>!!read.data.value?.qualification && !read.loading.value && !read.stale.value)
  const recommendation=computed(()=>read.data.value?.recommendation??null),qualification=computed(()=>read.data.value?.qualification??null)
  const recommendationError=computed(()=>read.data.value?.recommendationError || read.message.value),qualificationError=computed(()=>read.data.value?.qualificationError || read.message.value)
  function reconcile() {
    if(!ready.value)return
    const candidate=qualification.value!.candidates.records.find(c=>c.id===selectedId.value && c.enabled && c.eligible)
    if(selectedId.value && !candidate) { selectedId.value='';selected.value=null;notice.value='所选维修人员资格已失效或当前不可分派，请重新选择；其余编辑内容已保留。' }
    else if(candidate && JSON.stringify(candidate)!==JSON.stringify(selected.value))selected.value=candidate
    else if(!selectedId.value)selected.value=null
  }
  watch([ready,qualification,selectedId],reconcile,{flush:'sync'})
  watch(targetKey,()=>{page.value=1;keyword.value='';selectedId.value='';selected.value=null;notice.value=''}, {flush:'sync'})
  watch(()=>auth.identityEpoch,()=>{selected.value=null;selectedId.value='';notice.value=''}, {flush:'sync'})
  function select(person:EligibleTechnician|null) {
    if(!ready.value)return
    if(person && !qualification.value!.candidates.records.some(c=>c.id===person.id && c.eligible && c.enabled))return
    selectedId.value=person?.id??'';notice.value='';reconcile()
  }
  return {page,keyword,selected,notice,ready,recommendation,qualification,recommendationError,qualificationError,loading:read.loading,load:read.load,select}
}
