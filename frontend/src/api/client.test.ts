import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { apiRead, apiMutation } from './client'
import { useAuthStore } from '../stores/auth'
import type { CurrentUserVo } from './common'

const identity: CurrentUserVo = {
  id: '9007199254740993', loginName: '测试用户', name: '测试', phone: '13800000000', role: 'REPORTER',
  enabled: true, reviewStatus: null, version: 0,
  capabilities: { canBrowseCampus: true, canCreateRequest: true, canWorkOnTasks: false, canManage: false },
}
const operationId = 'c1b8b90e-ec64-4f46-8df2-711ce0387f2a'
function json(status: number, code = 'OK', data: unknown = null) {
  return new Response(JSON.stringify({ code, message: '服务端反馈', data, requestId: 'request-123' }), { status, headers: { 'Content-Type': 'application/json' } })
}
beforeEach(() => { setActivePinia(createPinia()); useAuthStore().setConfirmedUser(structuredClone(identity)) })
afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers() })

describe('公共请求与身份', () => {
  it('permission_error_keeps_identity', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(403, 'RECORD_FORBIDDEN')))
    const result = await apiRead('/api/work-orders/1')
    const authenticatedAfter403 = useAuthStore().authenticated
    expect(authenticatedAfter403).toBe(true)
    expect(result.kind).toBe('HTTP_ERROR')
    expect(useAuthStore().currentUser?.id).toBe(identity.id)
  })
  it('protected_401_clears_identity_and_csrf', async () => {
    useAuthStore().csrf = { token: 'csrf-test', headerName: 'X-CSRF-TOKEN' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(401, 'SESSION_EXPIRED')))
    await apiRead('/api/auth/me')
    expect(useAuthStore().authenticated).toBe(false)
    expect(useAuthStore().csrf).toBeNull()
  })
  it('public_login_401_preserves_existing_identity', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(401, 'AUTH_INVALID_CREDENTIALS')))
    expect((await apiMutation('/api/auth/login', 'POST', { loginName: 'other', password: 'input' })).kind).toBe('HTTP_ERROR')
    expect(useAuthStore().authenticated).toBe(true)
  })
  it('read_network_error_is_distinct_and_keeps_identity', async () => {
    const fetch = vi.fn().mockRejectedValue(new TypeError('network'))
    vi.stubGlobal('fetch', fetch)
    expect((await apiRead('/api/work-orders')).kind).toBe('READ_FAILED')
    expect(useAuthStore().authenticated).toBe(true)
    expect(fetch).toHaveBeenCalledTimes(1)
  })
  it('conflict_is_explicit_and_does_not_modify_input', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(409, 'VERSION_CONFLICT')))
    const input = { operationId, version: 4, description: '原草稿' }
    const result = await apiMutation('/api/work-orders/1/accept', 'POST', input)
    expect(result.kind).toBe('HTTP_ERROR')
    if (result.kind === 'HTTP_ERROR') expect(result.status).toBe(409)
    expect(input).toEqual({ operationId, version: 4, description: '原草稿' })
  })
  it('mutation_timeout_keeps_operation_id_and_input_without_retry', async () => {
    vi.useFakeTimers()
    const fetch = vi.fn((_path, init: RequestInit) => new Promise((_resolve, reject) => {
      init.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')), { once: true })
    }))
    vi.stubGlobal('fetch', fetch)
    const input = { operationId, description: '不要丢失' }
    const pending = apiMutation('/api/repair-requests', 'POST', input, { timeoutMs: 25 })
    await vi.advanceTimersByTimeAsync(30)
    const result = await pending
    expect(result.kind).toBe('UNKNOWN')
    if (result.kind === 'UNKNOWN') expect(result.operationId).toBe(operationId)
    expect(input).toEqual({ operationId, description: '不要丢失' })
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(useAuthStore().authenticated).toBe(true)
  })
  it.each([500, 502, 503])('mutation_%s_is_unknown', async status => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(status, 'INTERNAL_ERROR')))
    const result = await apiMutation('/api/repair-requests', 'POST', { operationId })
    expect(result.kind).toBe('UNKNOWN')
    if (result.kind === 'UNKNOWN') expect(result.operationId).toBe(operationId)
  })
  it('malformed_success_is_unknown_for_mutation', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>proxy</html>', { status: 200 })))
    expect((await apiMutation('/api/repair-requests', 'POST', { operationId })).kind).toBe('UNKNOWN')
  })
  it('read_success_preserves_large_string_id', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(200, 'OK', { id: identity.id })))
    const result = await apiRead<{ id: string }>('/api/work-orders/1')
    expect(result.kind).toBe('SUCCESS')
    if (result.kind === 'SUCCESS') expect(result.data.id).toBe(identity.id)
  })
  it('write_sends_csrf_and_same_origin_credentials', async () => {
    useAuthStore().csrf = { token: 'csrf-test', headerName: 'X-CSRF-TOKEN' }
    const fetch = vi.fn().mockResolvedValue(json(200))
    vi.stubGlobal('fetch', fetch)
    await apiMutation('/api/repair-requests', 'POST', { operationId })
    expect(fetch).toHaveBeenCalledTimes(1)
    const init = fetch.mock.calls[0]?.[1] as RequestInit
    expect(init.credentials).toBe('same-origin')
    expect(new Headers(init.headers).get('X-CSRF-TOKEN')).toBe('csrf-test')
  })
  it('multipart_preserves_browser_boundary_and_operation_id', async () => {
    const fetch = vi.fn().mockRejectedValue(new TypeError('network'))
    vi.stubGlobal('fetch', fetch)
    const form = new FormData(); form.append('operationId', operationId); form.append('file', new Blob(['image']), 'a.png')
    const result = await apiMutation('/api/attachments/temp', 'POST', form)
    if (result.kind === 'UNKNOWN') expect(result.operationId).toBe(operationId)
    expect(new Headers((fetch.mock.calls[0]?.[1] as RequestInit).headers).has('Content-Type')).toBe(false)
  })
  it('cross_origin_paths_cannot_receive_csrf', async () => {
    const fetch = vi.fn(); vi.stubGlobal('fetch', fetch)
    expect((await apiRead('https://example.com/api/auth/me')).kind).toBe('READ_FAILED')
    expect(fetch).not.toHaveBeenCalled()
  })
})
