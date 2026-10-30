import { computed, onUnmounted, shallowRef, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { useDraftStore } from '../stores/drafts'
import type { ActionDraft, PendingSubmission } from '../stores/drafts'
import type { BusinessReceipt } from '../api/common'
import { businessMutation, feedback, fetchOperation } from '../api/business'
import { applyConfirmedOperation, freezeSubmission, settleSubmission } from './useMutationConfirmation'
import { requestNavigationCountRefresh } from './useReviewNotifications'
/** Explicit writes and original-marker recovery; no polling or automatic resubmission. */
export function useBusinessAction(kind: string, id: () => string, confirmed: (receipt: BusinessReceipt, original: PendingSubmission) => void | Promise<void>) {
  const auth = useAuthStore(), store = useDraftStore(), action = shallowRef<ActionDraft | null>(null), busy = shallowRef(false), message = shallowRef('')
  let alive = true
  watch(() => `${auth.identityEpoch}/${auth.currentUser?.id}/${id()}`, () => { action.value = auth.currentUser ? store.action(auth.currentUser.id, kind, id()) : null; busy.value = false; message.value = '' }, { immediate: true, flush: 'sync' })
  const pending = computed(() => action.value?.pending ?? null)
  function valid(epoch: number, own: ActionDraft) { return alive && epoch === auth.identityEpoch && own === action.value }
  async function send(own: ActionDraft, epoch: number) {
    const original = own.pending; if (!original) return
    const result = await businessMutation(original.path, original.method, original.payload, original.resourceType, original.resourceId)
    if (!valid(epoch, own) || own.pending?.operationId !== original.operationId) return
    settleSubmission(own, result, original.operationId); message.value = result.kind === 'SUCCESS' ? '操作已确认成功。' : feedback(result)
    if (result.kind === 'SUCCESS') {
      if (original.resourceType === 'WORK_ORDER') requestNavigationCountRefresh()
      await confirmed(result.data, original)
    }
    if (result.kind === 'HTTP_ERROR' && result.response.code === 'CSRF_INVALID') await auth.ensureCsrf(true)
  }
  async function submit(original: PendingSubmission) {
    const own = action.value, epoch = auth.identityEpoch; if (!own || busy.value || own.pending) return
    busy.value = true
    try { if (!await auth.ensureCsrf() || !valid(epoch, own)) return; freezeSubmission(own, original); await send(own, epoch) }
    finally { if (valid(epoch, own)) busy.value = false }
  }
  async function check() {
    const own = action.value, original = own?.pending, epoch = auth.identityEpoch; if (!own || !original || busy.value) return
    busy.value = true
    try {
      const result = await fetchOperation(original.operationId, original.operation)
      if (!valid(epoch, own) || own.pending?.operationId !== original.operationId) return
      if (result.kind === 'SUCCESS' && applyConfirmedOperation(own, result.data, original.operationId) && result.data.kind === 'SUCCEEDED') {
        message.value = '已确认原操作成功。'
        if (original.resourceType === 'WORK_ORDER') requestNavigationCountRefresh()
        await confirmed(result.data.receipt, original)
      }
      else message.value = result.kind === 'SUCCESS' ? '原操作尚未确认，不等于失败；输入与原标识继续保留。' : feedback(result)
    } finally { if (valid(epoch, own)) busy.value = false }
  }
  async function retry() {
    const own = action.value, epoch = auth.identityEpoch; if (!own?.pending || busy.value) return
    busy.value = true
    try { if (!await auth.ensureCsrf() || !valid(epoch, own)) return; await send(own, epoch) }
    finally { if (valid(epoch, own)) busy.value = false }
  }
  onUnmounted(() => { alive = false })
  return { pending, busy, message, submit, check, retry }
}
