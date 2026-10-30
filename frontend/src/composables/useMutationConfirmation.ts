import type { BusinessReceipt, MutationResult, OperationResultVo } from '../api/common'
import type { PendingSubmission } from '../stores/drafts'
import { isId } from '../api/account'
import { expectedOrderRound } from '../api/business'
import { requestCampusRefresh } from './useCampusRefresh'
type Draft = { pending: PendingSubmission | null }
export function freezeSubmission(draft: Draft, pending: PendingSubmission) {
  if (draft.pending) throw new Error('请先核对原提交结果，不能换标识重发')
  draft.pending = { ...pending, payload: structuredClone(pending.payload) }
}
export function settleSubmission(draft: Draft, result: MutationResult<BusinessReceipt>, operation: string) {
  if (draft.pending?.operationId !== operation) return false
  if (result.kind === 'UNKNOWN') draft.pending.phase = 'UNKNOWN'
  else { draft.pending = null; if (result.kind === 'SUCCESS') requestCampusRefresh() }
  return true
}
export function applyConfirmedOperation(draft: Draft, result: OperationResultVo, operation: string) {
  if (draft.pending?.operationId !== operation) return false
  if (result.kind === 'SUCCEEDED' && result.receipt.operationId === operation && result.receipt.resourceType === draft.pending.resourceType && (!draft.pending.resourceId || result.receipt.resourceId === draft.pending.resourceId)
    && result.receipt.resultVersion === (draft.pending.payload.version === undefined ? 0 : Number(draft.pending.payload.version) + 1)
    && (draft.pending.resourceType !== 'WORK_ORDER' || (expectedOrderRound(draft.pending.path, draft.pending.payload) !== null && result.receipt.resultRound === expectedOrderRound(draft.pending.path, draft.pending.payload)))
    && (draft.pending.operation !== 'APPROVE_ASSIGN' || (result.receipt.relatedResourceType === 'WORK_ORDER' && isId(result.receipt.relatedResourceId)))) { draft.pending = null; requestCampusRefresh(); return true }
  draft.pending.phase = 'UNKNOWN'; return false
}
