import { shallowReactive } from 'vue'
import type { MutationResult } from '../../api/common'
import type { MutationReceiptVo, ReviewMeta } from '../../api/account'

export interface ReviewSnapshot { targetId: string; identityEpoch: number; version: number; attempt: number; reviewVersion: number; allowed: boolean }
export function createReviewFlow<T extends ReviewMeta>(submit: (dto: T) => Promise<MutationResult<MutationReceiptVo>>, current: () => ReviewSnapshot | null) {
  type Phase = 'IDLE' | 'EDITING' | 'CONFIRMING' | 'SUBMITTING' | 'UNKNOWN' | 'SUCCEEDED' | 'STALE'
  const state = shallowReactive<{ phase: Phase; message: string; draft: T | null; pending: T | null }>({ phase: 'IDLE', message: '', draft: null, pending: null })
  let captured: ReviewSnapshot | null = null, generation = 0, disposed = false
  function sameIdentity() {
    const now = current()
    return now !== null && captured !== null && now.targetId === captured.targetId && now.identityEpoch === captured.identityEpoch
  }
  function sameVersion() {
    const now = current(), p = state.pending
    return now !== null && p !== null && now.allowed && now.version === p.version && now.attempt === p.attempt && now.reviewVersion === p.reviewVersion
  }
  function stale() { state.phase = 'STALE'; state.message = '申请、权限或账号已变化，请刷新后重新核对。已填写内容保留。' }
  function prepare(dto: T) {
    if (disposed || state.phase === 'SUBMITTING' || state.phase === 'UNKNOWN') return false
    generation++; captured = current(); state.draft = { ...dto }; state.pending = Object.freeze({ ...dto }) as T
    if (!sameIdentity() || !sameVersion()) { stale(); return false }
    state.message = ''; state.phase = 'CONFIRMING'; return true
  }
  async function confirm() {
    if (disposed || !state.pending || !['CONFIRMING', 'UNKNOWN'].includes(state.phase)) return
    if (!sameIdentity() || (state.phase !== 'UNKNOWN' && !sameVersion())) { stale(); return }
    const ownGeneration = generation, pending = state.pending
    state.phase = 'SUBMITTING'; state.message = ''
    const result = await submit(pending)
    if (disposed || ownGeneration !== generation || !sameIdentity()) { stale(); return result }
    if (result.kind === 'SUCCESS' && result.data.operationId === pending.operationId) {
      state.phase = 'SUCCEEDED'; state.message = '保存成功，正在重新读取最新申请。'; state.pending = null; state.draft = null
    } else if (result.kind === 'HTTP_ERROR') {
      state.phase = result.status === 409 ? 'STALE' : 'CONFIRMING'
      state.message = `${result.response.message}（请求编号：${result.response.requestId}）`
    } else { state.phase = 'UNKNOWN'; state.message = '提交结果待确认。请刷新核对；重试时使用原提交，不修改操作标识或内容。' }
    return result
  }
  function backToEdit() {
    if (state.phase === 'SUBMITTING' || state.phase === 'UNKNOWN') return
    generation++; state.pending = null; state.phase = 'EDITING'; state.message = ''
  }
  function cancel() {
    if (state.phase === 'SUBMITTING' || state.phase === 'UNKNOWN') return
    generation++; captured = null; state.pending = null; state.draft = null; state.phase = 'IDLE'; state.message = ''
  }
  function dispose() { disposed = true; generation++; captured = null; state.pending = null; state.draft = null }
  return { state, prepare, confirm, backToEdit, cancel, dispose }
}
