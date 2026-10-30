import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
import { apiRead } from '../api/client'
import type { CurrentUserVo } from '../api/common'

const user: CurrentUserVo = { id: '9007199254740993', loginName: '演示', name: '演示', phone: '13800000000', role: 'REPORTER', enabled: true,
  reviewStatus: null, version: 0, capabilities: { canBrowseCampus: true, canCreateRequest: true, canWorkOnTasks: false, canManage: false } }
function response(status: number, code: string, data: unknown = null) {
  return new Response(JSON.stringify({ code, message: '反馈', data, requestId: 'test-id' }), { status, headers: { 'Content-Type': 'application/json' } })
}
beforeEach(() => setActivePinia(createPinia()))
afterEach(() => vi.unstubAllGlobals())
describe('真实会话恢复门禁', () => {
  it('network_failure_does_not_grant_business_access', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network')))
    const auth = useAuthStore()
    // 接口桩之前无restore；测试以明确false失败而不是未定义方法异常。
    const restored = typeof auth.restore === 'function' ? await auth.restore() : true
    const businessRouteGranted = typeof auth.businessRouteGranted === 'boolean' ? auth.businessRouteGranted : restored
    expect(businessRouteGranted).toBe(false)
  })
  it('valid_restore_and_retry_preserve_server_identity_and_fixed_expiry', async () => {
    const auth = useAuthStore()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(200, 'OK', { user, expiresAt: '2026-10-12T08:00:00.000Z' })))
    expect(typeof auth.restore).toBe('function')
    expect(await auth.restore()).toBe(true)
    expect(auth.currentUser?.id).toBe(user.id)
    expect(auth.businessRouteGranted).toBe(true)
    expect(auth.expiresAt).toBe('2026-10-12T08:00:00.000Z')
  })
  it('malformed_user_does_not_confirm_identity', async () => {
    const auth = useAuthStore()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(200, 'OK', { user: { ...user, id: 9007199254740992 }, expiresAt: '2026-10-12T08:00:00.000Z' })))
    expect(typeof auth.restore).toBe('function')
    expect(await auth.restore()).toBe(false)
    expect(auth.businessRouteGranted).toBe(false)
    expect(auth.currentUser).toBeNull()
  })
  it('network_recovery_failure_keeps_existing_identity_but_blocks_route_until_retry', async () => {
    const auth = useAuthStore(); auth.setConfirmedUser(user)
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network')))
    expect(typeof auth.restore).toBe('function'); await auth.restore()
    expect(auth.currentUser?.id).toBe(user.id)
    expect(auth.businessRouteGranted).toBe(false)
  })
  it('late_401_from_previous_identity_does_not_clear_new_identity', async () => {
    const auth = useAuthStore(); auth.setConfirmedUser(user)
    let resolve!: (r: Response) => void
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => new Promise<Response>(r => { resolve = r })))
    const oldRead = apiRead('/api/auth/me')
    auth.clearIdentity(); auth.setConfirmedUser({ ...user, id: '9007199254740994', loginName: '新账号' })
    resolve(response(401, 'SESSION_EXPIRED')); await oldRead
    expect(auth.currentUser?.id).toBe('9007199254740994')
  })
  it('403_keeps_confirmed_identity_401_clears_it', async () => {
    const auth = useAuthStore(); auth.setConfirmedUser(user)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(403, 'FORBIDDEN')))
    await apiRead('/api/admin/registration-reviews'); expect(auth.currentUser?.id).toBe(user.id)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(401, 'SESSION_REVOKED')))
    await apiRead('/api/auth/me'); expect(auth.currentUser).toBeNull()
  })
  it('late_login_cannot_overwrite_a_new_identity_and_requires_cookie_recheck', async () => {
    const auth = useAuthStore(); auth.csrf = { token: 'x'.repeat(64), headerName: 'X-CSRF-TOKEN' }
    let resolve!: (r: Response) => void
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => new Promise<Response>(r => { resolve = r })))
    const oldLogin = auth.login({ loginName: '旧账号', password: 'Synthetic1!' }); await Promise.resolve()
    auth.clearIdentity(); auth.setConfirmedUser({ ...user, id: '9007199254740994', loginName: '新账号' })
    resolve(response(200, 'OK', { user, expiresAt: '2026-10-12T08:00:00.000Z' }));
    // 若错误实现继续获取CSRF，提供响应让失败及时结束。
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(200, 'OK', { token: 'x'.repeat(64), headerName: 'X-CSRF-TOKEN' })))
    await oldLogin
    expect(auth.currentUser?.id).toBe('9007199254740994')
    expect(auth.businessRouteGranted).toBe(false)
  })
  it('late_logout_cannot_clear_a_new_identity_without_cookie_recheck', async () => {
    const auth = useAuthStore(); auth.setConfirmedUser(user); auth.csrf = { token: 'x'.repeat(64), headerName: 'X-CSRF-TOKEN' }
    let resolve!: (r: Response) => void
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => new Promise<Response>(r => { resolve = r })))
    const oldLogout = auth.logout(); await Promise.resolve()
    auth.clearIdentity(); auth.setConfirmedUser({ ...user, id: '9007199254740994', loginName: '新账号' })
    resolve(response(200, 'OK', null))
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(200, 'OK', { token: 'x'.repeat(64), headerName: 'X-CSRF-TOKEN' })))
    await oldLogout
    expect(auth.currentUser?.id).toBe('9007199254740994')
    expect(auth.businessRouteGranted).toBe(false)
  })
  it('unknown_login_blocks_existing_identity_until_me_confirms_the_cookie', async () => {
    const auth = useAuthStore(); auth.setConfirmedUser(user); auth.csrf = { token: 'x'.repeat(64), headerName: 'X-CSRF-TOKEN' }
    const input = { loginName: '另一个账号', password: 'Synthetic1!' }
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('truncated login response')))
    expect((await auth.login(input)).kind).toBe('UNKNOWN')
    expect(auth.currentUser?.id).toBe(user.id)
    expect(auth.businessRouteGranted).toBe(false)
    expect(auth.csrf).toBeNull()
    expect(input).toEqual({ loginName: '另一个账号', password: 'Synthetic1!' })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(200, 'OK', { user: { ...user, id: '9007199254740994' }, expiresAt: '2026-10-12T08:00:00.000Z' })))
    expect(await auth.restore()).toBe(true)
    expect(auth.currentUser?.id).toBe('9007199254740994')
    expect(auth.businessRouteGranted).toBe(true)
  })
  it('unknown_logout_blocks_business_and_failed_recheck_cannot_reopen_it', async () => {
    const auth = useAuthStore(); auth.setConfirmedUser(user); auth.csrf = { token: 'x'.repeat(64), headerName: 'X-CSRF-TOKEN' }
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network')))
    expect((await auth.logout()).kind).toBe('UNKNOWN')
    expect(auth.currentUser?.id).toBe(user.id)
    expect(auth.businessRouteGranted).toBe(false)
    expect(auth.csrf).toBeNull()
    expect(await auth.restore()).toBe(false)
    expect(auth.businessRouteGranted).toBe(false)
  })
  it('same_account_new_login_ignores_the_previous_session_late_401', async () => {
    const auth = useAuthStore(); auth.setConfirmedUser(user)
    let resolve!: (r: Response) => void
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => new Promise<Response>(r => { resolve = r })))
    const oldRead = apiRead('/api/auth/me'), oldEpoch = auth.identityEpoch
    auth.acceptSession({ user, expiresAt: '2026-10-12T08:00:00.000Z' })
    expect(auth.identityEpoch).toBeGreaterThan(oldEpoch)
    resolve(response(401, 'SESSION_REVOKED')); await oldRead
    expect(auth.currentUser?.id).toBe(user.id)
    expect(auth.businessRouteGranted).toBe(true)
  })
  it('same_account_new_login_ignores_old_successful_restore_but_normal_me_keeps_epoch', async () => {
    const auth = useAuthStore(); auth.setConfirmedUser(user)
    let resolve!: (r: Response) => void
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => new Promise<Response>(r => { resolve = r })))
    const oldRead = auth.restore(), oldEpoch = auth.identityEpoch
    auth.acceptSession({ user: { ...user, version: 1 }, expiresAt: '2026-10-12T08:00:00.000Z' })
    expect(auth.identityEpoch).toBeGreaterThan(oldEpoch)
    resolve(response(200, 'OK', { user: { ...user, version: 99 }, expiresAt: '2026-10-12T07:00:00.000Z' }))
    expect(await oldRead).toBe(false)
    expect(auth.currentUser?.version).toBe(1)
    expect(auth.expiresAt).toBe('2026-10-12T08:00:00.000Z')
    const confirmedEpoch = auth.identityEpoch
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(200, 'OK', { user: { ...user, version: 2 }, expiresAt: '2026-10-12T08:00:00.000Z' })))
    expect(await auth.restore()).toBe(true)
    expect(auth.identityEpoch).toBe(confirmedEpoch)
    expect(auth.currentUser?.version).toBe(2)
  })
})
