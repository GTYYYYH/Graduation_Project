import { businessRead, businessMutation, object, isInstant, isVersion, isPage, queryString } from './business'
import { isId } from './account'
import { isAttachment } from './attachment'
import type { AttachmentVo } from './attachment'
import type { Id, RequestOptions } from './common'
export type RequestStatus = 'PENDING_REVIEW' | 'NEEDS_SUPPLEMENT' | 'APPROVED' | 'WITHDRAWN' | 'DUPLICATE_CLOSED'
export const requestStatusNames: Record<RequestStatus, string> = { PENDING_REVIEW: '待审核', NEEDS_SUPPLEMENT: '退回待补充', APPROVED: '已审核通过', WITHDRAWN: '已撤回', DUPLICATE_CLOSED: '重复关闭' }
export interface AccountReference { id: Id; name: string }
export interface LocationReference { id: Id; code: string; name: string; floorId: Id }
export interface EquipmentReference { id: Id; equipmentNo: string; name: string; locationId: Id | null }
export interface RequestSummary { id: Id; requestNo: string; equipmentName: string; status: RequestStatus; locationMode: 'STRUCTURED' | 'LEGACY_TEXT'; location: LocationReference | null; locationNameSnapshot: string | null; firstSubmittedAt: string; createdAt: string; version: number; workOrder: { id: Id; workOrderNo: string; status: string } | null; allowedActions: string[] }
export interface RequestSubmission { id: Id; submissionNo: number; equipmentName: string; equipmentCategory: string | null; locationMode: 'STRUCTURED' | 'LEGACY_TEXT'; location: LocationReference | null; locationNameSnapshot: string | null; addressText: string; description: string; contactName: string; contactPhone: string; attachments: AttachmentVo[]; submittedAt: string }
export interface RequestEvent { id: Id; type: string; fromStatus: RequestStatus | null; toStatus: RequestStatus; actor: AccountReference; reason: string | null; occurredAt: string }
export interface RequestDetail extends RequestSummary { reporter: AccountReference; currentSubmission: RequestSubmission; submissions: RequestSubmission[]; events: RequestEvent[]; matchedEquipment: EquipmentReference | null; queriedAt: string }
export function isAccountReference(value: unknown): value is AccountReference { return object(value) && isId(value.id) && typeof value.name === 'string' }
export function isLocationReference(value: unknown): value is LocationReference { return object(value) && isId(value.id) && isId(value.floorId) && typeof value.code === 'string' && typeof value.name === 'string' }
export function isEquipmentReference(value: unknown): value is EquipmentReference { return object(value) && isId(value.id) && typeof value.equipmentNo === 'string' && typeof value.name === 'string' && (value.locationId === null || isId(value.locationId)) }
export function isRequestSummary(value: unknown): value is RequestSummary {
  return object(value) && isId(value.id) && typeof value.requestNo === 'string' && typeof value.equipmentName === 'string' && Object.keys(requestStatusNames).includes(String(value.status)) && ['STRUCTURED', 'LEGACY_TEXT'].includes(String(value.locationMode))
    && (value.location === null || isLocationReference(value.location)) && (value.locationNameSnapshot === null || typeof value.locationNameSnapshot === 'string') && isInstant(value.firstSubmittedAt) && isInstant(value.createdAt) && isVersion(value.version)
    && Array.isArray(value.allowedActions) && value.allowedActions.every(a => typeof a === 'string') && (value.workOrder === null || (object(value.workOrder) && isId(value.workOrder.id) && typeof value.workOrder.workOrderNo === 'string' && typeof value.workOrder.status === 'string'))
}
export function isRequestSubmission(value: unknown): value is RequestSubmission {
  return object(value) && isId(value.id) && isVersion(value.submissionNo) && value.submissionNo > 0 && ['equipmentName', 'addressText', 'description', 'contactName', 'contactPhone'].every(key => typeof value[key] === 'string')
    && (value.equipmentCategory === null || typeof value.equipmentCategory === 'string') && ['STRUCTURED', 'LEGACY_TEXT'].includes(String(value.locationMode)) && (value.location === null || isLocationReference(value.location))
    && (value.locationNameSnapshot === null || typeof value.locationNameSnapshot === 'string') && Array.isArray(value.attachments) && value.attachments.every(isAttachment) && isInstant(value.submittedAt)
}
function isRequestDetail(value: unknown): value is RequestDetail { return isRequestSummary(value) && object(value) && isAccountReference(value.reporter) && isRequestSubmission(value.currentSubmission) && Array.isArray(value.submissions) && value.submissions.every(isRequestSubmission)
  && Array.isArray(value.events) && value.events.every(e => object(e) && isId(e.id) && typeof e.type === 'string' && (e.fromStatus === null || Object.keys(requestStatusNames).includes(String(e.fromStatus))) && Object.keys(requestStatusNames).includes(String(e.toStatus)) && isAccountReference(e.actor) && (e.reason === null || typeof e.reason === 'string') && isInstant(e.occurredAt))
  && (value.matchedEquipment === null || isEquipmentReference(value.matchedEquipment)) && isInstant(value.queriedAt) }
export interface RequestQuery { page?: number; pageSize?: number; keyword?: string; status?: string; locationId?: Id }
export const fetchRequests = (query: RequestQuery) => businessRead(`/api/repair-requests?${queryString({ ...query })}`, (value): value is import('./common').PageResult<RequestSummary> => isPage(value, isRequestSummary))
export const fetchAdminRequests = (query: RequestQuery) => businessRead(`/api/admin/repair-requests?${queryString({ ...query })}`, (value): value is import('./common').PageResult<RequestSummary> => isPage(value, isRequestSummary))
export const fetchRequest = (id: Id, options: RequestOptions = {}) => businessRead(`/api/repair-requests/${encodeURIComponent(id)}`, isRequestDetail, options)
export const createRequest = (body: Record<string, unknown>) => businessMutation('/api/repair-requests', 'POST', body, 'REPAIR_REQUEST')
export const mutateRequest = (id: Id, action: 'supplement' | 'withdraw' | 'return' | 'approve-and-assign' | 'duplicate-close', body: Record<string, unknown>) => businessMutation(`/api/repair-requests/${encodeURIComponent(id)}/${action}`, 'POST', body, 'REPAIR_REQUEST', id)
