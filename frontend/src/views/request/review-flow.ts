import type { PendingSubmission } from '../../stores/drafts'
import type { RequestDetail } from '../../api/repair-request'
import { isId } from '../../api/account'
import type { EligibleTechnician } from '../../api/workorder'
export type ReviewAction = 'approve-and-assign' | 'return' | 'duplicate-close'
export type ReviewTechnician = EligibleTechnician
export interface ReviewSelection {
  request: Pick<RequestDetail, 'id' | 'status' | 'version' | 'locationMode' | 'location'> | null
  equipment: { id: string; equipmentNo: string; name: string; locationId: string | null; enabled: boolean; currentWorkOrder: { id: string } | null } | null
  technician: ReviewTechnician | null
  reason: string
  ready: boolean
}
export function reviewBlock(action: ReviewAction, fields: ReviewSelection): string {
  const request = fields.request
  if (!fields.ready || !request) return '请先成功读取并核对当前申请资料。'
  if (request.status !== 'PENDING_REVIEW') return '仅待审核申请可以处理，请刷新核对当前状态。'
  if (!Number.isInteger(request.version) || request.version < 0 || request.version >= 2147483647) return '申请版本无效，请重新核对。'
  if (!isId(request.id)) return '申请标识无效。'
  if (request.locationMode === 'STRUCTURED' && !request.location) return '申请位置无效，请退回补充。'
  if (action !== 'approve-and-assign' && (!fields.reason.trim() || Array.from(fields.reason.trim()).length > 2000)) return '请填写原因，最多2000字。'
  if (action === 'return') return ''
  if (action === 'duplicate-close') {
    const equipment = fields.equipment
    if (!equipment || !isId(equipment.id)) return '请明确选择本次申请对应的具体设备台账。'
    if (request.locationMode === 'STRUCTURED' && request.location?.id !== equipment.locationId) return '申请与设备位置不一致，请退回补充纠正位置。'
    return ''
  }
  const technician = fields.technician
  if (!technician || !isId(technician.id) || !technician.enabled || technician.reviewStatus !== 'APPROVED' || !technician.eligible) return '请从当前合格名单主动选择维修人员。'
  if (technician.refusal.length) return '首次派单名单上下文已变化，请重新核对。'
  return ''
}
export function prepareReviewOperation(action: ReviewAction, fields: ReviewSelection, operationId: string): PendingSubmission | null {
  if (reviewBlock(action, fields)) return null
  const payload: Record<string, unknown> = { operationId, version: fields.request?.version }
  if (action === 'duplicate-close') payload.equipmentId = fields.equipment?.id
  if (action === 'approve-and-assign') { payload.technicianId = fields.technician?.id; payload.confirmPreviouslyRefused = false }
  else payload.reason = fields.reason.trim()
  return { operationId, operation: action === 'approve-and-assign' ? 'APPROVE_ASSIGN' : action === 'return' ? 'RETURN_REQUEST' : 'DUPLICATE_CLOSE', path: `/api/repair-requests/${fields.request?.id}/${action}`, method: 'POST', payload, resourceType: 'REPAIR_REQUEST', resourceId: fields.request?.id, phase: 'SENDING' }
}
