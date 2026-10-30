export type Id = string
export type UUID = string
export type Role = 'REPORTER' | 'TECHNICIAN' | 'ADMIN'
export type ReviewStatus = 'PENDING' | 'REJECTED' | 'APPROVED'
export interface FieldError { field: string; code: string; message: string }
export interface ApiResponse<T> { code: string; message: string; data: T | null; requestId: string; errors?: FieldError[] }
export interface PageResult<T> { records: T[]; total: number; page: number; pageSize: number }
export interface CurrentUserVo {
  id: Id; loginName: string; name: string; phone: string; role: Role; enabled: boolean
  reviewStatus: ReviewStatus | null; version: number
  capabilities: { canBrowseCampus: boolean; canCreateRequest: boolean; canWorkOnTasks: boolean; canManage: boolean }
}
export interface CsrfVo { token: string; headerName: 'X-CSRF-TOKEN' }
export interface RequestOptions { signal?: AbortSignal; timeoutMs?: number; operationId?: UUID }
export interface BusinessReceipt { operationId: UUID; resourceType: string; resourceId: Id; resultVersion: number | null; resultRound: number | null; committedAt: string; relatedResourceType: string | null; relatedResourceId: Id | null }
export type OperationResultVo = { kind: 'SUCCEEDED'; receipt: BusinessReceipt } | { kind: 'NOT_CONFIRMED'; receipt: null }
export type ReadResult<T> =
  | { kind: 'SUCCESS'; data: T; requestId: string }
  | { kind: 'HTTP_ERROR'; status: number; response: ApiResponse<unknown> }
  | { kind: 'READ_FAILED'; message: string }
export type MutationResult<T> =
  | { kind: 'SUCCESS'; data: T; requestId: string }
  | { kind: 'HTTP_ERROR'; status: number; response: ApiResponse<unknown> }
  | { kind: 'UNKNOWN'; operationId: UUID | null; message: string }
