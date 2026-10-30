import { apiRead, apiMutation } from './client'
import { businessRead, isReceipt, isPage, isInstant, queryString } from './business'
import type { BusinessReceipt } from './common'
import type { CurrentUserVo, CsrfVo, Id, MutationResult, PageResult, ReadResult, ReviewStatus } from './common'

export interface AuthSessionVo { user: CurrentUserVo; expiresAt: string }
export interface LoginDto { loginName: string; password: string }
export interface RegisterDto extends LoginDto { role: 'REPORTER' | 'TECHNICIAN'; name: string; phone: string; confirmPassword: string }
export interface RegistrationVo { id: Id; loginName: string; role: 'REPORTER' | 'TECHNICIAN'; reviewStatus: ReviewStatus | null }
export interface ReviewSubmissionVo {
  id: Id; attempt: number; nameSnapshot: string; phoneSnapshot: string; submittedAt: string; status: ReviewStatus; version: number
  reviewedBy: { id: Id; name: string } | null; reviewedAt: string | null; reason: string | null
}
export interface TechnicianReviewVo {
  account: CurrentUserVo & { allowedActions: string[] }; current: ReviewSubmissionVo; history: ReviewSubmissionVo[]; allowedActions: string[]; queriedAt: string
}
export interface ReviewMeta { operationId: string; version: number; attempt: number; reviewVersion: number }
export interface ResubmitReviewDto extends ReviewMeta { name: string; phone: string }
export interface ReviewDecisionDto extends ReviewMeta { passed: boolean; reason: string | null }
export interface MutationReceiptVo {
  operationId: string; resourceType: 'ACCOUNT'; resourceId: Id; resultVersion: number; resultRound: null; committedAt: string
  relatedResourceType: 'REGISTRATION_REVIEW'; relatedResourceId: Id
}
export interface PendingReviewVo { id: Id; loginName: string; name: string; phone: string; enabled: boolean; reviewAttempt: number; submittedAt: string }
export interface CatalogVo {
  configVersion: string
  buildings: { id: Id; code: string; name: string; type: string; displayOrder: number }[]
  floors: { id: Id; code: string; buildingId: Id; level: number; name: string; displayOrder: number }[]
  locations: { id: Id; code: string; floorId: Id; type: string; use: string; name: string; reportable: boolean; canCreateRequest: boolean; displayOrder: number }[]
}
function object(v: unknown): v is Record<string, unknown> { return typeof v === 'object' && v !== null }
export function isId(v: unknown): v is Id {
  return typeof v === 'string' && /^[1-9][0-9]{0,18}$/.test(v) && BigInt(v) <= 9223372036854775807n
}
function instant(v: unknown): v is string { return typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(v) && Number.isFinite(Date.parse(v)) }
function version(v: unknown): v is number { return typeof v === 'number' && Number.isInteger(v) && v >= 0 && v <= 2147483647 }
function reviewStatus(v: unknown): v is ReviewStatus { return v === 'PENDING' || v === 'REJECTED' || v === 'APPROVED' }
export function isCurrentUser(v: unknown): v is CurrentUserVo {
  if (!object(v) || !isId(v.id) || typeof v.loginName !== 'string' || typeof v.name !== 'string' || typeof v.phone !== 'string'
    || !/^1[3-9][0-9]{9}$/.test(v.phone) || !['REPORTER', 'TECHNICIAN', 'ADMIN'].includes(String(v.role)) || typeof v.enabled !== 'boolean'
    || !version(v.version) || !object(v.capabilities)) return false
  if (v.role === 'TECHNICIAN' ? !reviewStatus(v.reviewStatus) : v.reviewStatus !== null) return false
  return ['canBrowseCampus', 'canCreateRequest', 'canWorkOnTasks', 'canManage'].every(k => typeof v.capabilities === 'object' && v.capabilities !== null && typeof (v.capabilities as Record<string, unknown>)[k] === 'boolean')
}
function session(v: unknown): v is AuthSessionVo { return object(v) && isCurrentUser(v.user) && v.user.enabled && instant(v.expiresAt) }
function csrf(v: unknown): v is CsrfVo { return object(v) && v.headerName === 'X-CSRF-TOKEN' && typeof v.token === 'string' && v.token.length >= 32 && v.token.length <= 1024 }
function registration(v: unknown): v is RegistrationVo {
  return object(v) && isId(v.id) && typeof v.loginName === 'string'
    && ((v.role === 'REPORTER' && v.reviewStatus === null) || (v.role === 'TECHNICIAN' && v.reviewStatus === 'PENDING'))
}
function submission(v: unknown): v is ReviewSubmissionVo {
  return object(v) && isId(v.id) && version(v.version) && version(v.attempt) && v.attempt >= 1 && typeof v.nameSnapshot === 'string'
    && typeof v.phoneSnapshot === 'string' && instant(v.submittedAt) && reviewStatus(v.status)
    && (v.reviewedBy === null || (object(v.reviewedBy) && isId(v.reviewedBy.id) && typeof v.reviewedBy.name === 'string'))
    && (v.reviewedAt === null || instant(v.reviewedAt)) && (v.reason === null || typeof v.reason === 'string')
}
const accountActions = ['EDIT_PROFILE', 'CHANGE_PASSWORD', 'RESUBMIT_REVIEW', 'REVIEW_APPROVE', 'REVIEW_REJECT', 'ENABLE_ACCOUNT', 'DISABLE_ACCOUNT', 'RESET_PASSWORD']
function actions(v: unknown): v is string[] { return Array.isArray(v) && v.every(a => typeof a === 'string' && accountActions.includes(a)) }
function review(v: unknown): v is TechnicianReviewVo {
  return object(v) && isCurrentUser(v.account) && object(v.account) && actions(v.account.allowedActions) && submission(v.current)
    && Array.isArray(v.history) && v.history.every(submission) && v.history[0]?.id === v.current.id && actions(v.allowedActions) && instant(v.queriedAt)
}
function receipt(v: unknown): v is MutationReceiptVo {
  return object(v) && typeof v.operationId === 'string' && v.resourceType === 'ACCOUNT' && isId(v.resourceId) && version(v.resultVersion)
    && v.resultRound === null && instant(v.committedAt) && v.relatedResourceType === 'REGISTRATION_REVIEW' && isId(v.relatedResourceId)
}
function pending(v: unknown): v is PageResult<PendingReviewVo> {
  return object(v) && typeof v.total === 'number' && Number.isSafeInteger(v.total) && v.total >= 0 && version(v.page) && v.page > 0 && version(v.pageSize) && v.pageSize > 0 && v.pageSize <= 100
    && Array.isArray(v.records) && v.records.every(r => object(r) && isId(r.id) && typeof r.loginName === 'string' && typeof r.name === 'string'
      && typeof r.phone === 'string' && typeof r.enabled === 'boolean' && version(r.reviewAttempt) && r.reviewAttempt > 0 && instant(r.submittedAt))
}
function catalog(v: unknown): v is CatalogVo {
  return object(v) && typeof v.configVersion === 'string'
    && Array.isArray(v.buildings) && v.buildings.every(b => object(b) && isId(b.id) && typeof b.code === 'string' && typeof b.name === 'string' && ['DORMITORY', 'TEACHING', 'CANTEEN', 'GYM'].includes(String(b.type)) && version(b.displayOrder))
    && Array.isArray(v.floors) && v.floors.every(f => object(f) && isId(f.id) && isId(f.buildingId) && typeof f.code === 'string' && typeof f.name === 'string' && version(f.level) && f.level > 0 && version(f.displayOrder))
    && Array.isArray(v.locations) && v.locations.every(l => object(l) && isId(l.id) && isId(l.floorId) && typeof l.code === 'string' && typeof l.name === 'string'
      && ['ROOM', 'AREA'].includes(String(l.type)) && typeof l.use === 'string' && typeof l.reportable === 'boolean' && typeof l.canCreateRequest === 'boolean' && version(l.displayOrder))
}
async function read<T>(path: string, valid: (v: unknown) => v is T): Promise<ReadResult<T>> {
  const r = await apiRead<unknown>(path)
  if (r.kind !== 'SUCCESS') return r
  return valid(r.data) ? { ...r, data: r.data } : { kind: 'READ_FAILED', message: '服务端数据格式不正确，请重试并核对请求编号' }
}
async function write<T>(path: string, body: unknown, valid: (v: unknown) => v is T): Promise<MutationResult<T>> {
  const r = await apiMutation<unknown>(path, 'POST', body)
  if (r.kind !== 'SUCCESS') return r
  return valid(r.data) ? { ...r, data: r.data } : { kind: 'UNKNOWN', operationId: object(body) && typeof body.operationId === 'string' ? body.operationId : null, message: '提交结果格式异常，请保留输入并核对' }
}
export const fetchMe = () => read('/api/auth/me', session)
export const fetchCsrf = () => read('/api/auth/csrf', csrf)
export const submitLogin = (dto: LoginDto) => write('/api/auth/login', dto, session)
export const registerAccount = (dto: RegisterDto) => write('/api/auth/register', dto, registration)
export const logoutSession = () => write('/api/auth/logout', undefined, (v: unknown): v is null => v === null)
export const fetchOwnReview = () => read('/api/account/review', review)
export const fetchAdminReview = (id: Id) => read(`/api/admin/accounts/${encodeURIComponent(id)}/registration-review`, review)
export const submitReviewDecision = (id: Id, dto: ReviewDecisionDto) => write(`/api/admin/accounts/${encodeURIComponent(id)}/registration-review`, dto, receipt)
export const resubmitReview = (dto: ResubmitReviewDto) => write('/api/account/review/resubmit', dto, receipt)
export const fetchPendingReviews = (page: number, keyword: string) => read(`/api/admin/registration-reviews?${new URLSearchParams({ page: String(page), pageSize: '20', keyword })}`, pending)
export const fetchCatalog = () => read('/api/campus/catalog', catalog)
export type AccountProfileVo = CurrentUserVo & { allowedActions: string[] }
export const fetchProfile = () => read('/api/account/profile', (v: unknown): v is AccountProfileVo => isCurrentUser(v) && object(v) && actions(v.allowedActions))
export interface PersonnelVo extends AccountProfileVo {reviewAttempt:number;counts:Record<string,string|number>|null}
export interface PersonnelListVo {records:PersonnelVo[];page:number;pageSize:number;listTotal:number;allPeopleTotal:number;categoryCounts:Record<string,number>;pendingReviewCount:number;queriedAt:string}
export interface BusinessHistoryItemVo {resourceType:'REPAIR_REQUEST'|'WORK_ORDER';id:string;businessNo:string;equipmentName:string;status:string;createdAt:string;relations:string[];canViewDetail:boolean}
export interface PersonnelDetailVo {person:PersonnelVo;availableCategories:string[];categoryTotals:Record<string,number>;statusCounts:Record<string,number>;records:PageResult<BusinessHistoryItemVo>;queriedAt:string}
export interface PasswordResetResultVo {kind:'NEW_SECRET'|'ALREADY_COMMITTED';receipt:BusinessReceipt;account:{id:string;name:string};loginName:string;newPassword:string|null}
const numbers=(v:unknown):v is Record<string,number>=>object(v)&&Object.values(v).every(n=>typeof n==='number'&&Number.isSafeInteger(n)&&n>=0)
function person(v:unknown):v is PersonnelVo {return isCurrentUser(v)&&object(v)&&actions(v.allowedActions)&&version(v.reviewAttempt)&&(v.role==='ADMIN'?v.counts===null:object(v.counts)&&v.counts.role===v.role&&['completedOrders',...(v.role==='REPORTER'?['pendingRequests','unfinishedOrders']:['unfinishedTasks'])].every(k=>typeof (v.counts as Record<string,unknown>)[k]==='number'&&version((v.counts as Record<string,unknown>)[k])))}
function personnelList(v:unknown):v is PersonnelListVo {return object(v)&&Array.isArray(v.records)&&v.records.every(person)&&version(v.page)&&version(v.pageSize)&&numbers(v.categoryCounts)&&version(v.listTotal)&&version(v.allPeopleTotal)&&version(v.pendingReviewCount)&&isInstant(v.queriedAt)}
function historyItem(v:unknown):v is BusinessHistoryItemVo {return object(v)&&['REPAIR_REQUEST','WORK_ORDER'].includes(String(v.resourceType))&&isId(v.id)&&typeof v.businessNo==='string'&&typeof v.equipmentName==='string'&&typeof v.status==='string'&&isInstant(v.createdAt)&&Array.isArray(v.relations)&&v.relations.every(r=>['SUBMITTER','CURRENT_ASSIGNEE','ACTUAL_REPAIRER','COMPLETED_BY_ME','REFUSED','REPLACED'].includes(String(r)))&&typeof v.canViewDetail==='boolean'}
export function isPersonnelDetail(v:unknown):v is PersonnelDetailVo {return object(v)&&person(v.person)&&Array.isArray(v.availableCategories)&&v.availableCategories.every(c=>['REQUESTS','RELATED_ORDERS','CURRENT_TASKS','HISTORICAL_ORDERS'].includes(String(c)))&&numbers(v.categoryTotals)&&numbers(v.statusCounts)&&isPage(v.records,historyItem)&&isInstant(v.queriedAt)}
export const fetchPersonnel=(query:Record<string,string|number|boolean|null|undefined>)=>businessRead(`/api/admin/personnel?${queryString(query)}`,personnelList)
export const fetchPersonnelHistory=(id:string,query:Record<string,string|number|boolean|null|undefined>={})=>businessRead(`/api/admin/personnel/${encodeURIComponent(id)}/history?${queryString(query)}`,isPersonnelDetail)
export async function resetAccountPassword(id:string,body:{version:number;operationId:string}):Promise<MutationResult<PasswordResetResultVo>> {
  const r=await apiMutation<unknown>(`/api/admin/accounts/${encodeURIComponent(id)}/reset-password`,'POST',body)
  if(r.kind!=='SUCCESS')return r
  const v=r.data
  if(object(v)&&isReceipt(v.receipt)&&v.receipt.resourceType==='ACCOUNT'&&v.receipt.resourceId===id&&v.receipt.operationId===body.operationId&&v.receipt.resultVersion===body.version+1&&object(v.account)&&v.account.id===id&&typeof v.account.name==='string'&&typeof v.loginName==='string'&&((v.kind==='NEW_SECRET'&&typeof v.newPassword==='string'&&v.newPassword.length>=8&&v.newPassword.length<=32&&/[A-Za-z]/.test(v.newPassword)&&/[0-9]/.test(v.newPassword))||(v.kind==='ALREADY_COMMITTED'&&v.newPassword===null)))return {...r,data:v as unknown as PasswordResetResultVo}
  return {kind:'UNKNOWN',operationId:body.operationId,message:'重置结果格式无法确认，请保留原操作标识；不自动再次重置。'}
}
