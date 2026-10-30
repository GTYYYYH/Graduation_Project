import { apiMutation, apiRead } from './client'
import { isId } from './account'
import type { BusinessReceipt, MutationResult, ReadResult, OperationResultVo, PageResult, RequestOptions } from './common'
export const object = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null
export const isInstant = (value: unknown): value is string => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(value) && Number.isFinite(Date.parse(value))
export const isVersion = (value: unknown): value is number => typeof value === 'number' && Number.isInteger(value) && value >= 0 && value <= 2147483647
export function isReceipt(value: unknown): value is BusinessReceipt {
  return object(value) && typeof value.operationId === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(value.operationId)
    && ['ACCOUNT', 'REGISTRATION_REVIEW', 'EQUIPMENT', 'REPAIR_REQUEST', 'WORK_ORDER', 'REPAIR_PROGRESS', 'REPAIR_ROUND'].includes(String(value.resourceType)) && isId(value.resourceId)
    && (value.resultVersion === null || isVersion(value.resultVersion)) && (value.resultRound === null || isVersion(value.resultRound)) && isInstant(value.committedAt)
    && ((value.relatedResourceType === null && value.relatedResourceId === null) || (['ACCOUNT', 'REGISTRATION_REVIEW', 'EQUIPMENT', 'REPAIR_REQUEST', 'WORK_ORDER', 'REPAIR_PROGRESS', 'REPAIR_ROUND'].includes(String(value.relatedResourceType)) && isId(value.relatedResourceId)))
}
export function isPage<T>(value: unknown, item: (value: unknown) => value is T): value is PageResult<T> {
  return object(value) && Array.isArray(value.records) && value.records.every(item) && typeof value.total === 'number' && Number.isSafeInteger(value.total) && value.total >= 0 && isVersion(value.page) && value.page > 0 && isVersion(value.pageSize) && value.pageSize > 0 && value.pageSize <= 100
}
export function queryString(query: Record<string, string | number | boolean | null | undefined>) { return new URLSearchParams(Object.entries(query).filter(([, value]) => value !== null && value !== undefined && value !== '').map(([key, value]) => [key, String(value)])).toString() }
export function expectedOrderRound(path: string, body: Record<string, unknown>): number | null {
  if (!path.startsWith('/api/work-orders/')) return null
  if (path.endsWith('/accept')) return 1
  if (path.endsWith('/refuse') || path.endsWith('/assign')) return 0
  const round = body.round
  if (!isVersion(round) || round < 1) return null
  return path.endsWith('/acceptance') && body.passed === false ? round + 1 : round
}
export async function businessRead<T>(path: string, valid: (value: unknown) => value is T, options: RequestOptions = {}): Promise<ReadResult<T>> {
  const result = await apiRead<unknown>(path, options)
  if (result.kind !== 'SUCCESS') return result
  return valid(result.data) ? { ...result, data: result.data } : { kind: 'READ_FAILED', message: '数据格式异常，请重试并核对请求编号' }
}
export async function businessMutation(path: string, method: 'POST' | 'PATCH' | 'DELETE', body: Record<string, unknown>, resourceType: string, resourceId?: string): Promise<MutationResult<BusinessReceipt>> {
  const result = await apiMutation<unknown>(path, method, body)
  if (result.kind !== 'SUCCESS') return result
  const value = result.data
  if (isReceipt(value) && value.operationId === body.operationId && value.resourceType === resourceType && (!resourceId || value.resourceId === resourceId)
    && isVersion(value.resultVersion) && (body.version === undefined ? value.resultVersion === 0 : value.resultVersion === Number(body.version) + 1)
    && (resourceType !== 'WORK_ORDER' || (expectedOrderRound(path, body) !== null && value.resultRound === expectedOrderRound(path, body)))
    && (!path.endsWith('/approve-and-assign') || (value.relatedResourceType === 'WORK_ORDER' && isId(value.relatedResourceId)))) return { ...result, data: value }
  return { kind: 'UNKNOWN', operationId: typeof body.operationId === 'string' ? body.operationId : null, message: '成功回执无法确认，请保留原标识与输入，核对原操作结果' }
}
export const fetchOperation = (operationId: string, operation: string) => businessRead(`/api/operations/${encodeURIComponent(operationId)}?${queryString({ operation })}`, (value: unknown): value is OperationResultVo => object(value)
  && ((value.kind === 'NOT_CONFIRMED' && value.receipt === null) || (value.kind === 'SUCCEEDED' && isReceipt(value.receipt) && value.receipt.operationId === operationId)))
export function feedback(result: { kind: string; message?: string; response?: { message: string } }) { return result.response?.message ?? result.message ?? '请重新核对' }
