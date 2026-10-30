import type { ApiResponse, MutationResult, ReadResult, RequestOptions } from './common'
import { useAuthStore } from '../stores/auth'

function isEnvelope(value: unknown): value is ApiResponse<unknown> {
  if (typeof value !== 'object' || value === null) return false
  const item = value as Record<string, unknown>
  return typeof item.code === 'string' && item.code.length > 0 && typeof item.message === 'string'
    && typeof item.requestId === 'string' && item.requestId.length > 0 && 'data' in item
    && (item.errors === undefined || (Array.isArray(item.errors) && item.errors.every(error =>
      typeof error === 'object' && error !== null && typeof error.field === 'string'
      && typeof error.code === 'string' && typeof error.message === 'string')))
}

function updateIdentity(path: string, status: number, response: ApiResponse<unknown>, epoch: number) {
  const publicPath = ['/api/auth/login', '/api/auth/register', '/api/auth/csrf'].includes(path.split('?')[0] ?? '')
  if (useAuthStore().identityEpoch === epoch && !publicPath && status === 401 && ['AUTH_REQUIRED', 'SESSION_EXPIRED', 'SESSION_REVOKED', 'ACCOUNT_DISABLED'].includes(response.code)) {
    useAuthStore().clearIdentity()
  }
}

function operationFrom(body: unknown): string | null {
  const value = body instanceof FormData ? body.get('operationId')
    : typeof body === 'object' && body !== null ? (body as Record<string, unknown>).operationId : null
  return typeof value === 'string' ? value : null
}

async function request(path: string, init: RequestInit, options: RequestOptions, timeoutMs: number) {
  // 公共客户端只向同源API发请求，避免向外部地址发送CSRF。
  if (!path.startsWith('/api/') || /[\\\r\n]/.test(path)) throw new Error('invalid API path')
  const controller = new AbortController()
  const cancel = () => controller.abort()
  const timeout = setTimeout(cancel, options.timeoutMs ?? timeoutMs)
  if (options.signal?.aborted) cancel()
  options.signal?.addEventListener('abort', cancel, { once: true })
  try {
    const response = await fetch(path, { ...init, credentials: 'same-origin', cache: 'no-store', signal: controller.signal })
    if (!response.headers.get('Content-Type')?.toLowerCase().includes('application/json')) throw new Error('invalid response type')
    const body: unknown = await response.json()
    if (!isEnvelope(body)) throw new Error('invalid response')
    return { response, body }
  } finally {
    clearTimeout(timeout)
    options.signal?.removeEventListener('abort', cancel)
  }
}

export async function apiRead<T>(path: string, options: RequestOptions = {}): Promise<ReadResult<T>> {
  const epoch = useAuthStore().identityEpoch
  try {
    const { response, body } = await request(path, { method: 'GET' }, options, 8000)
    if (response.ok && body.code === 'OK') return { kind: 'SUCCESS', data: body.data as T, requestId: body.requestId }
    if (!response.ok && body.code !== 'OK') {
      updateIdentity(path, response.status, body, epoch)
      return { kind: 'HTTP_ERROR', status: response.status, response: body }
    }
  } catch { /* 读取失败不改变登录身份。 */ }
  return { kind: 'READ_FAILED', message: '读取失败，请检查连接后重试' }
}
export async function apiImage(path: string, options: RequestOptions = {}): Promise<ReadResult<Blob>> {
  const epoch = useAuthStore().identityEpoch, controller = new AbortController(), cancel = () => controller.abort()
  const timeout = setTimeout(cancel, options.timeoutMs ?? 8000)
  if (options.signal?.aborted) cancel()
  options.signal?.addEventListener('abort', cancel, { once: true })
  try {
    if (!/^\/api\/attachments\/[1-9][0-9]{0,18}\/content$/.test(path)) throw new Error('invalid image path')
    const response = await fetch(path, { method: 'GET', credentials: 'same-origin', cache: 'no-store', signal: controller.signal })
    const mime = response.headers.get('Content-Type')?.split(';')[0]?.trim(), requestId = response.headers.get('X-Request-Id')
    if (response.ok && (mime === 'image/png' || mime === 'image/jpeg') && requestId) {
      const blob = await response.blob()
      if (blob.size > 0 && blob.size <= 5_000_000) return { kind: 'SUCCESS', data: blob, requestId }
    } else if (!response.ok && mime === 'application/json') {
      const body: unknown = await response.json()
      if (isEnvelope(body) && body.code !== 'OK') { updateIdentity(path, response.status, body, epoch); return { kind: 'HTTP_ERROR', status: response.status, response: body } }
    }
  } catch { /* No private preview is retained on an invalid binary response. */ }
  finally { clearTimeout(timeout); options.signal?.removeEventListener('abort', cancel) }
  return { kind: 'READ_FAILED', message: '图片读取失败，请重试并核对权限' }
}

export async function apiMutation<T>(path: string, method: 'POST' | 'PATCH' | 'DELETE', body: unknown, options: RequestOptions = {}): Promise<MutationResult<T>> {
  const epoch = useAuthStore().identityEpoch
  const operationId = operationFrom(body) ?? options.operationId ?? null
  try {
    const headers = new Headers()
    const csrf = useAuthStore().csrf
    if (csrf) headers.set(csrf.headerName, csrf.token)
    if (options.operationId) headers.set('X-Operation-Id', options.operationId)
    const multipart = body instanceof FormData
    if (!multipart && body !== undefined) headers.set('Content-Type', 'application/json')
    const { response, body: envelope } = await request(path, {
      method, headers, body: body === undefined ? undefined : multipart ? body : JSON.stringify(body),
    }, options, 20000)
    if (response.ok && envelope.code === 'OK') return { kind: 'SUCCESS', data: envelope.data as T, requestId: envelope.requestId }
    if ([400, 401, 403, 404, 409, 413].includes(response.status) && envelope.code !== 'OK') {
      updateIdentity(path, response.status, envelope, epoch)
      return { kind: 'HTTP_ERROR', status: response.status, response: envelope }
    }
  } catch { /* 请求可能已经提交；不自动重试、不清输入、不新建标识。 */ }
  return { kind: 'UNKNOWN', operationId, message: '提交结果待确认，请保留当前内容并核对后再操作' }
}
