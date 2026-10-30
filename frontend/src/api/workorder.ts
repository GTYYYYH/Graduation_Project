import { businessRead, businessMutation, object, isInstant, isPage, isVersion, queryString } from './business'
import { isId } from './account'
import { isAttachment } from './attachment'
import type { AttachmentVo } from './attachment'
import { isAccountReference, isEquipmentReference, isLocationReference, isRequestSubmission } from './repair-request'
import type { AccountReference, EquipmentReference, LocationReference, RequestSubmission } from './repair-request'
import type { Id, PageResult, RequestOptions } from './common'
export type OrderStatus = 'PENDING_ACCEPT' | 'REASSIGN_PENDING' | 'IN_PROGRESS' | 'PENDING_ACCEPTANCE' | 'COMPLETED'
export const orderStatusNames: Record<OrderStatus, string> = { PENDING_ACCEPT: '待接单', REASSIGN_PENDING: '待重派', IN_PROGRESS: '处理中', PENDING_ACCEPTANCE: '待验收', COMPLETED: '已完成' }
export interface WorkOrderNavigationCount { userId: Id; role: 'ADMIN' | 'TECHNICIAN' | 'REPORTER'; count: number }
export const fetchWorkOrderNavigationCount = () => businessRead('/api/work-orders/navigation-count', (value): value is WorkOrderNavigationCount => object(value)
  && isId(value.userId) && ['ADMIN', 'TECHNICIAN', 'REPORTER'].includes(String(value.role)) && typeof value.count === 'number' && Number.isSafeInteger(value.count) && value.count >= 0)
export interface OrderSummary { id: Id; workOrderNo: string; requestId: Id; requestNo: string; reportedEquipmentName: string; equipment: EquipmentReference | null; status: OrderStatus; locationMode: 'STRUCTURED' | 'LEGACY_TEXT'; location: LocationReference | null; locationNameSnapshot: string | null; createdAt: string; assignedTechnician: AccountReference | null; acceptedTechnician: AccountReference | null; version: number | null; round: number | null; isRework: boolean | null; allowedActions: string[]; historyScope: 'FULL' | 'ASSIGNMENT_ONLY' }
export interface Assignment { id: Id; assignmentNo: number; technician: AccountReference; assignedBy: AccountReference; visibleSubmissionId: Id; assignedAt: string; endedAt: string | null; outcome: 'PENDING' | 'ACCEPTED' | 'REFUSED' | 'REPLACED'; reason: string | null; decisionAt: string | null }
export interface Progress { id: Id; round: number; technician: AccountReference; content: string; recordedAt: string }
export interface RepairRound { id: Id; round: number; technician: AccountReference; startedAt: string; submittedAt: string | null; result: string | null; attachments: AttachmentVo[]; acceptance: { passed: boolean; reason: string | null; reporter: AccountReference; acceptedAt: string } | null; progress: Progress[] }
export interface OrderEvent { id: Id; type: string; fromStatus: OrderStatus | null; toStatus: OrderStatus; actor: AccountReference; reason: string | null; occurredAt: string }
export interface OrderDetail extends OrderSummary { requestMaterial: RequestSubmission; reporter: AccountReference; assignments: Assignment[]; rounds: RepairRound[]; events: OrderEvent[]; firstSubmittedAt?: string | null; acceptedAt: string | null; completedAt: string | null; queriedAt: string }
const optionalInstant = (v: unknown) => v === null || isInstant(v), optionalText = (v: unknown) => v === null || typeof v === 'string'
export function isOrderSummary(v: unknown): v is OrderSummary {
  return object(v) && isId(v.id) && isId(v.requestId) && typeof v.workOrderNo === 'string' && typeof v.requestNo === 'string' && typeof v.reportedEquipmentName === 'string' && (v.equipment === null || isEquipmentReference(v.equipment)) && Object.keys(orderStatusNames).includes(String(v.status)) && ['STRUCTURED', 'LEGACY_TEXT'].includes(String(v.locationMode))
    && (v.location === null || isLocationReference(v.location)) && optionalText(v.locationNameSnapshot) && isInstant(v.createdAt) && (v.assignedTechnician === null || isAccountReference(v.assignedTechnician)) && (v.acceptedTechnician === null || isAccountReference(v.acceptedTechnician))
    && Array.isArray(v.allowedActions) && v.allowedActions.every(a => typeof a === 'string') && ((v.historyScope === 'FULL' && isVersion(v.version) && isVersion(v.round) && typeof v.isRework === 'boolean') || (v.historyScope === 'ASSIGNMENT_ONLY' && v.version === null && v.round === null && v.isRework === null && v.assignedTechnician === null && v.acceptedTechnician === null && v.allowedActions.length === 0))
}
function isAssignment(v: unknown): v is Assignment { return object(v) && isId(v.id) && isVersion(v.assignmentNo) && v.assignmentNo > 0 && isAccountReference(v.technician) && isAccountReference(v.assignedBy) && isId(v.visibleSubmissionId) && isInstant(v.assignedAt) && optionalInstant(v.endedAt) && ['PENDING', 'ACCEPTED', 'REFUSED', 'REPLACED'].includes(String(v.outcome)) && optionalText(v.reason) && optionalInstant(v.decisionAt) }
function isRound(v: unknown): v is RepairRound { return object(v) && isId(v.id) && isVersion(v.round) && v.round > 0 && isAccountReference(v.technician) && isInstant(v.startedAt) && optionalInstant(v.submittedAt) && optionalText(v.result) && Array.isArray(v.attachments) && v.attachments.every(isAttachment)
  && (v.acceptance === null || (object(v.acceptance) && typeof v.acceptance.passed === 'boolean' && optionalText(v.acceptance.reason) && isAccountReference(v.acceptance.reporter) && isInstant(v.acceptance.acceptedAt))) && Array.isArray(v.progress) && v.progress.every(p => object(p) && isId(p.id) && isVersion(p.round) && p.round > 0 && isAccountReference(p.technician) && typeof p.content === 'string' && isInstant(p.recordedAt)) }
export function isOrderDetail(v: unknown): v is OrderDetail { return isOrderSummary(v) && object(v) && isRequestSubmission(v.requestMaterial) && isAccountReference(v.reporter) && Array.isArray(v.assignments) && v.assignments.every(isAssignment) && Array.isArray(v.rounds) && v.rounds.every(isRound)
  && Array.isArray(v.events) && v.events.every(e => object(e) && isId(e.id) && typeof e.type === 'string' && (e.fromStatus === null || Object.keys(orderStatusNames).includes(String(e.fromStatus))) && Object.keys(orderStatusNames).includes(String(e.toStatus)) && isAccountReference(e.actor) && optionalText(e.reason) && isInstant(e.occurredAt))
  && (v.firstSubmittedAt === undefined || optionalInstant(v.firstSubmittedAt)) && optionalInstant(v.acceptedAt) && optionalInstant(v.completedAt) && isInstant(v.queriedAt) && (v.historyScope !== 'ASSIGNMENT_ONLY' || (v.rounds.length === 0 && v.acceptedAt === null && v.completedAt === null)) }
export interface OrderQuery { page?: number; pageSize?: number; keyword?: string; status?: string; scope?: 'CURRENT' | 'HISTORY' | 'ALL'; locationId?: Id; technicianId?: Id }
export const fetchOrders = (query: OrderQuery) => businessRead(`/api/work-orders?${queryString({ ...query })}`, (v): v is PageResult<OrderSummary> => isPage(v, isOrderSummary))
export const fetchOrder = (id: Id, options: RequestOptions = {}) => businessRead(`/api/work-orders/${encodeURIComponent(id)}`, isOrderDetail, options)
export type OrderAction = 'accept' | 'refuse' | 'assign' | 'progress' | 'submit-result' | 'acceptance'
export const mutateOrder = (id: Id, action: OrderAction, body: Record<string, unknown>) => businessMutation(`/api/work-orders/${encodeURIComponent(id)}/${action}`, 'POST', body, 'WORK_ORDER', id)

export interface EligibleTechnician { id: Id; name: string; enabled: boolean; reviewStatus: 'APPROVED'; eligible: boolean; refusal: { assignmentId: Id; reason: string; refusedAt: string }[] }
export interface AssignmentEligible { candidates: PageResult<EligibleTechnician>; eligibleTotal: number; queriedAt: string }
export function isEligibleTechnician(v: unknown): v is EligibleTechnician {
  return object(v) && isId(v.id) && typeof v.name === 'string' && typeof v.enabled === 'boolean' && v.reviewStatus === 'APPROVED' && typeof v.eligible === 'boolean'
    && Array.isArray(v.refusal) && v.refusal.every(r => object(r) && isId(r.assignmentId) && typeof r.reason === 'string' && isInstant(r.refusedAt))
}
export const fetchAssignmentEligible = (requestId: Id, page = 1, keyword = '', options: RequestOptions = {}, pageSize = 20) => businessRead(`/api/admin/assignment-eligible?${queryString({ context: 'FIRST_ASSIGN', requestId, page, pageSize, keyword })}`,
  (v): v is AssignmentEligible => object(v) && isPage(v.candidates, isEligibleTechnician) && Number.isSafeInteger(v.eligibleTotal) && Number(v.eligibleTotal) >= v.candidates.total && isInstant(v.queriedAt), options)
export const fetchOrderEligible = (workOrderId: Id, context: 'CHANGE_BEFORE_ACCEPT' | 'REASSIGN', page = 1, keyword = '', options: RequestOptions = {}, pageSize = 20) => businessRead(`/api/admin/assignment-eligible?${queryString({ context, workOrderId, page, pageSize, keyword })}`,
  (v): v is AssignmentEligible => object(v) && isPage(v.candidates, isEligibleTechnician) && Number.isSafeInteger(v.eligibleTotal) && Number(v.eligibleTotal) >= v.candidates.total && isInstant(v.queriedAt), options)
export type AssignmentTarget = { requestId: Id; workOrderId?: never; context?: never } | { requestId?: never; workOrderId: Id; context: 'CHANGE_BEFORE_ACCEPT' | 'REASSIGN' }
export interface AssignmentCandidate extends EligibleTechnician { W: number; I: number; R: number; A: number; load: number; todayCompleted: number; lastAssignedAt: string | null; reason: string }
export interface AssignmentCandidates { top5: AssignmentCandidate[]; candidates: PageResult<AssignmentCandidate>; eligibleTotal: number; preferredTotal: number; queriedAt: string; businessDate: string }
const count = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0
export function isAssignmentCandidate(v: unknown): v is AssignmentCandidate {
  return isEligibleTechnician(v) && object(v) && count(v.W) && count(v.I) && count(v.R) && count(v.A) && count(v.todayCompleted)
    && v.load === v.W + 2*v.I + 3*v.R + 0.5*v.A && optionalInstant(v.lastAssignedAt) && typeof v.reason === 'string'
}
export const fetchAssignmentCandidates = (target: AssignmentTarget, page = 1, keyword = '', options: RequestOptions = {}) => businessRead(`/api/work-orders/assignment-candidates?${queryString({ ...target, context: target.context ?? 'FIRST_ASSIGN', page, pageSize: 20, keyword })}`,
  (v): v is AssignmentCandidates => object(v) && Array.isArray(v.top5) && v.top5.length <= 5 && v.top5.every(isAssignmentCandidate) && isPage(v.candidates, isAssignmentCandidate)
    && count(v.eligibleTotal) && v.eligibleTotal >= v.candidates.total && count(v.preferredTotal) && v.preferredTotal <= v.eligibleTotal && isInstant(v.queriedAt) && typeof v.businessDate === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v.businessDate), options)
