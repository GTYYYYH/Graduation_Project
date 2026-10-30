import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, ref } from 'vue'
import type { EffectScope, MaybeRefOrGetter, Ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../stores/auth'
import type { CurrentUserVo } from '../api/common'

interface Notifications {
  count: Readonly<Ref<number | null>>
  stale: Readonly<Ref<boolean>>
  message: Readonly<Ref<string>>
  badgeVisible: Readonly<Ref<boolean>>
}
// The absent feature is an assertion failure in RED, rather than an import error.
const modules = import.meta.glob<{
  useReviewNotifications: (path: MaybeRefOrGetter<string>, kind?: 'requests' | 'personnel' | 'tasks' | 'orders' | 'management') => Notifications
  requestReviewCountRefresh: () => void
}>('./useReviewNotifications.ts', { eager: true })
const notificationsModule = modules['./useReviewNotifications.ts']
const admin: CurrentUserVo = {
  id: '91', loginName: 'synthetic_admin', name: '合成管理员', phone: '13800000000',
  role: 'ADMIN', enabled: true, reviewStatus: null, version: 0,
  capabilities: { canBrowseCampus: true, canCreateRequest: true, canWorkOnTasks: false, canManage: true },
}
class VisiblePage extends EventTarget { visibilityState: DocumentVisibilityState = 'visible' }
let page: VisiblePage
let browser: EventTarget
const scopes: EffectScope[] = []
function json(status: number, code: string, data: unknown = null) {
  return new Response(JSON.stringify({ code, message: '合成反馈', data, requestId: 'synthetic-review-count' }), {
    status, headers: { 'Content-Type': 'application/json' },
  })
}
function pendingPage(total: number) {
  return json(200, 'OK', { records: total ? [{
    id: '92', requestNo: 'RR-92', equipmentName: '空调', status: 'PENDING_REVIEW',
    locationMode: 'LEGACY_TEXT', location: null, locationNameSnapshot: null,
    firstSubmittedAt: '2026-10-14T00:00:00.000Z', createdAt: '2026-10-14T00:00:00.000Z',
    version: 2, workOrder: null, allowedActions: ['RETURN_REQUEST', 'APPROVE_AND_ASSIGN'],
  }] : [], total, page: 1, pageSize: 1 })
}
function setup(kind: 'requests' | 'personnel' | 'tasks' | 'orders' | 'management' = 'requests') {
  const path = ref('/campus'), scope = effectScope()
  let state: Notifications | undefined
  scope.run(() => { state = notificationsModule?.useReviewNotifications(path, kind) })
  scopes.push(scope)
  return { path, scope, state }
}
async function settle() {
  await nextTick(); await vi.advanceTimersByTimeAsync(0); await nextTick()
}
beforeEach(() => {
  vi.useFakeTimers(); setActivePinia(createPinia())
  useAuthStore().setConfirmedUser(structuredClone(admin))
  page = new VisiblePage(); browser = new EventTarget()
  vi.stubGlobal('document', page); vi.stubGlobal('window', browser)
  vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(pendingPage(7))))
})

describe('本人待处理工单导航数量', () => {
  function signIn(role: 'TECHNICIAN' | 'REPORTER', id = '91') {
    useAuthStore().setConfirmedUser({ ...admin, id, role, reviewStatus: role === 'TECHNICIAN' ? 'APPROVED' : null,
      capabilities: { canBrowseCampus: true, canManage: false, canCreateRequest: role === 'REPORTER', canWorkOnTasks: role === 'TECHNICIAN' } })
  }
  function counted(count: number, role = useAuthStore().currentUser!.role, userId = '91') { return json(200, 'OK', { userId, role, count }) }
  it.each(['TECHNICIAN', 'REPORTER'] as const)('reads only the %s current-user count, and changes after a confirmed refresh', async role => {
    signIn(role)
    const reads = vi.fn().mockResolvedValueOnce(counted(4)).mockImplementation(() => Promise.resolve(counted(1)))
    vi.stubGlobal('fetch', reads)
    const mine = setup(role === 'TECHNICIAN' ? 'tasks' : 'orders'), other = setup(role === 'TECHNICIAN' ? 'orders' : 'tasks')
    await settle(); expect(mine.state?.count.value).toBe(4); expect(mine.state?.badgeVisible.value).toBe(true)
    expect(other.state?.count.value).toBeNull(); expect(reads).toHaveBeenCalledTimes(1)
    expect(String(reads.mock.calls[0]?.[0])).toBe('/api/work-orders/navigation-count')
    notificationsModule?.requestReviewCountRefresh(); await settle(); expect(mine.state?.count.value).toBe(1)
  })
  it('hides zero, keeps a stale known quantity on network failure and does not clear just on entering the list', async () => {
    signIn('REPORTER'); const reads = vi.fn().mockResolvedValueOnce(counted(3)).mockRejectedValueOnce(new TypeError('network')).mockResolvedValue(counted(0))
    vi.stubGlobal('fetch', reads); const mine = setup('orders'); await settle()
    mine.path.value = '/work-orders'; await settle(); expect(mine.state?.count.value).toBe(3); expect(mine.state?.stale.value).toBe(true)
    notificationsModule?.requestReviewCountRefresh(); await settle(); expect(mine.state?.count.value).toBe(0); expect(mine.state?.badgeVisible.value).toBe(false)
  })
  it('clears an old quantity if another tab switches the Cookie to a different user with the same role', async () => {
    signIn('TECHNICIAN'); vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(counted(4)).mockResolvedValue(counted(9, 'TECHNICIAN', '92')))
    const mine = setup('tasks'); await settle(); expect(mine.state?.count.value).toBe(4)
    browser.dispatchEvent(new Event('focus')); await settle(); expect(mine.state?.count.value).toBeNull(); expect(mine.state?.badgeVisible.value).toBe(false)
  })
  it.each([-1, 1.5, '4', Number.MAX_SAFE_INTEGER + 1])('rejects malformed work-order count %s rather than displaying it', async count => {
    signIn('REPORTER'); vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(200, 'OK', { userId: '91', role: 'REPORTER', count })))
    const mine = setup('orders'); await settle(); expect(mine.state?.count.value).toBeNull(); expect(mine.state?.badgeVisible.value).toBe(false)
    expect(mine.state?.message.value).not.toBe('')
  })
  it('does not read work-order quantities for an admin or a technician without task capability', async () => {
    const reads = vi.fn(); vi.stubGlobal('fetch', reads); const task = setup('tasks'), orders = setup('orders'); await settle()
    expect(reads).not.toHaveBeenCalled(); signIn('TECHNICIAN')
    useAuthStore().currentUser!.capabilities.canWorkOnTasks = false; await settle()
    expect(reads).not.toHaveBeenCalled(); expect(task.state?.count.value).toBeNull(); expect(orders.state?.count.value).toBeNull()
  })
  it('polls visible tasks at thirty seconds and immediately on return from a hidden page', async () => {
    signIn('TECHNICIAN'); const reads = vi.fn().mockImplementation(() => Promise.resolve(counted(2))); vi.stubGlobal('fetch', reads)
    setup('tasks'); await settle(); await vi.advanceTimersByTimeAsync(30_000); expect(reads).toHaveBeenCalledTimes(2)
    page.visibilityState = 'hidden'; page.dispatchEvent(new Event('visibilitychange')); await vi.advanceTimersByTimeAsync(60_000)
    expect(reads).toHaveBeenCalledTimes(2); page.visibilityState = 'visible'; page.dispatchEvent(new Event('visibilitychange')); await settle()
    expect(reads).toHaveBeenCalledTimes(3)
  })
})

describe('管理员人员管理导航数量', () => {
  function people(total: number) { return json(200, 'OK', { records: [], total, page: 1, pageSize: 20 }) }
  it('reads the unfiltered pending technician total independently from repair requests', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) => Promise.resolve(url.startsWith('/api/admin/registration-reviews?') ? people(42) : pendingPage(7))))
    const requests = setup(), personnel = setup('personnel'); await settle()
    expect(requests.state?.count.value).toBe(7); expect(personnel.state?.count.value).toBe(42)
    expect(personnel.state?.badgeVisible.value).toBe(true)
    expect(vi.mocked(fetch).mock.calls.some(([url]) => url === '/api/admin/registration-reviews?page=1&pageSize=20&keyword=')).toBe(true)
  })
  it('refreshes personnel after a confirmed decision and hides its zero badge', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementationOnce(() => Promise.resolve(people(2))).mockImplementation(() => Promise.resolve(people(0))))
    const { state } = setup('personnel'); await settle(); expect(state?.count.value).toBe(2)
    notificationsModule?.requestReviewCountRefresh(); await settle()
    expect(state?.count.value).toBe(0); expect(state?.badgeVisible.value).toBe(false)
  })
  it('polls personnel only while visible and refreshes immediately on return', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(people(3))))
    const { state } = setup('personnel'); await settle()
    await vi.advanceTimersByTimeAsync(30_000); expect(fetch).toHaveBeenCalledTimes(2)
    page.visibilityState = 'hidden'; page.dispatchEvent(new Event('visibilitychange')); await settle()
    await vi.advanceTimersByTimeAsync(60_000); expect(fetch).toHaveBeenCalledTimes(2)
    page.visibilityState = 'visible'; page.dispatchEvent(new Event('visibilitychange')); await settle()
    expect(fetch).toHaveBeenCalledTimes(3); expect(state?.count.value).toBe(3)
  })
  it('clears personnel counts after losing administrator permission', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementationOnce(() => Promise.resolve(people(2))).mockImplementation(() => Promise.resolve(json(403, 'FORBIDDEN'))))
    const { state } = setup('personnel'); await settle(); expect(state?.count.value).toBe(2)
    browser.dispatchEvent(new Event('focus')); await settle()
    expect(state?.count.value).toBeNull(); expect(state?.badgeVisible.value).toBe(false)
  })
})
afterEach(() => {
  for (const scope of scopes.splice(0)) scope.stop()
  vi.clearAllTimers(); vi.useRealTimers(); vi.unstubAllGlobals()
})

describe('管理员待审核导航数量', () => {
  it('uses_the_pending_review_total_instead_of_the_single_page_record_count', async () => {
    const { state } = setup(); await settle()
    expect(state?.count.value).toBe(7)
    expect(state?.badgeVisible.value).toBe(true)
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(vi.mocked(fetch).mock.calls[0]?.[0]).toBe('/api/admin/repair-requests?page=1&pageSize=1&status=PENDING_REVIEW')
    expect(vi.mocked(fetch).mock.calls[0]?.[1]?.method).toBe('GET')
  })
  it('hides_the_badge_when_a_successful_read_confirms_zero', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(pendingPage(0))))
    const { state } = setup(); await settle()
    expect(state?.count.value).toBe(0); expect(state?.badgeVisible.value).toBe(false)
    expect(state?.stale.value).toBe(false); expect(state?.message.value).toBe('')
  })
  it.each(['REPORTER', 'TECHNICIAN'] as const)('never_reads_the_global_count_for_%s', async role => {
    useAuthStore().setConfirmedUser({ ...admin, role, capabilities: { ...admin.capabilities, canManage: false } })
    const { state } = setup(); await settle(); await vi.advanceTimersByTimeAsync(90_000)
    browser.dispatchEvent(new Event('focus')); notificationsModule?.requestReviewCountRefresh(); await settle()
    expect(fetch).not.toHaveBeenCalled(); expect(state?.count.value).toBeNull()
    expect(state?.badgeVisible.value).toBe(false)
  })
  it('does_not_read_before_the_existing_session_is_confirmed', async () => {
    const auth = useAuthStore(); auth.recovery = 'RESTORING'
    const { state } = setup(); await settle(); await vi.advanceTimersByTimeAsync(30_000)
    expect(fetch).not.toHaveBeenCalled(); expect(state?.count.value).toBeNull()
    auth.recovery = 'CONFIRMED'; await settle()
    expect(state?.count.value).toBe(7); expect(fetch).toHaveBeenCalledTimes(1)
  })
  it('refreshes_after_thirty_visible_seconds', async () => {
    const reads = vi.fn().mockImplementationOnce(() => Promise.resolve(pendingPage(7)))
      .mockImplementation(() => Promise.resolve(pendingPage(9)))
    vi.stubGlobal('fetch', reads); const { state } = setup(); await settle()
    await vi.advanceTimersByTimeAsync(29_999); expect(reads).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1); await settle()
    expect(state?.count.value).toBe(9); expect(reads).toHaveBeenCalledTimes(2)
  })
  it('refreshes_on_route_change_and_window_focus', async () => {
    const reads = vi.fn().mockImplementationOnce(() => Promise.resolve(pendingPage(7)))
      .mockImplementationOnce(() => Promise.resolve(pendingPage(9)))
      .mockImplementation(() => Promise.resolve(pendingPage(4)))
    vi.stubGlobal('fetch', reads); const { path, state } = setup(); await settle()
    path.value = '/admin/requests'; await settle(); expect(state?.count.value).toBe(9)
    browser.dispatchEvent(new Event('focus')); await settle()
    expect(state?.count.value).toBe(4); expect(reads).toHaveBeenCalledTimes(3)
  })
  it('refreshes_when_a_confirmed_review_operation_announces_success', async () => {
    const reads = vi.fn().mockImplementationOnce(() => Promise.resolve(pendingPage(7)))
      .mockImplementation(() => Promise.resolve(pendingPage(6)))
    vi.stubGlobal('fetch', reads); const { state } = setup(); await settle()
    notificationsModule?.requestReviewCountRefresh(); await settle()
    expect(state?.count.value).toBe(6); expect(reads).toHaveBeenCalledTimes(2)
    expect(reads.mock.calls.every(call => call[1]?.method === 'GET')).toBe(true)
  })
  it('stops_polling_while_hidden_and_refreshes_immediately_on_visibility_restore', async () => {
    const reads = vi.fn().mockImplementationOnce(() => Promise.resolve(pendingPage(7)))
      .mockImplementation(() => Promise.resolve(pendingPage(3)))
    vi.stubGlobal('fetch', reads); const { state } = setup(); await settle()
    page.visibilityState = 'hidden'; page.dispatchEvent(new Event('visibilitychange')); await settle()
    await vi.advanceTimersByTimeAsync(90_000)
    browser.dispatchEvent(new Event('focus')); notificationsModule?.requestReviewCountRefresh(); await settle()
    expect(reads).toHaveBeenCalledTimes(1); expect(state?.count.value).toBe(7); expect(state?.stale.value).toBe(true)
    page.visibilityState = 'visible'; page.dispatchEvent(new Event('visibilitychange')); await settle()
    expect(state?.count.value).toBe(3); expect(state?.stale.value).toBe(false); expect(reads).toHaveBeenCalledTimes(2)
  })
  it('does_not_start_a_read_in_an_initially_hidden_page', async () => {
    page.visibilityState = 'hidden'
    const { state } = setup(); await settle(); await vi.advanceTimersByTimeAsync(90_000)
    expect(fetch).not.toHaveBeenCalled(); expect(state?.count.value).toBeNull()
    page.visibilityState = 'visible'; page.dispatchEvent(new Event('visibilitychange')); await settle()
    expect(state?.count.value).toBe(7); expect(fetch).toHaveBeenCalledTimes(1)
  })
  it('preserves_an_explicitly_stale_previous_count_after_a_network_failure', async () => {
    const reads = vi.fn().mockImplementationOnce(() => Promise.resolve(pendingPage(7)))
      .mockRejectedValue(new TypeError('synthetic network'))
    vi.stubGlobal('fetch', reads); const { path, state } = setup(); await settle()
    path.value = '/admin/requests'; await settle()
    expect(state?.count.value).toBe(7); expect(state?.badgeVisible.value).toBe(true)
    expect(state?.stale.value).toBe(true); expect(state?.message.value).not.toBe('')
  })
  it('keeps_a_first_network_failure_unknown_instead_of_inventing_zero', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('synthetic network')))
    const { state } = setup(); await settle()
    expect(state?.count.value).toBeNull(); expect(state?.badgeVisible.value).toBe(false)
    expect(state?.stale.value).toBe(false); expect(state?.message.value).not.toBe('')
  })
  it('keeps_an_invalid_success_envelope_unknown_instead_of_using_its_total', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(json(200, 'OK', { records: [], total: '7', page: 1, pageSize: 1 }))))
    const { state } = setup(); await settle()
    expect(state?.count.value).toBeNull(); expect(state?.message.value).not.toBe('')
  })
  it.each([401, 403])('clears_the_previous_count_after_%s', async status => {
    const reads = vi.fn().mockImplementationOnce(() => Promise.resolve(pendingPage(7)))
      .mockImplementation(() => Promise.resolve(json(status, status === 401 ? 'SESSION_REVOKED' : 'FORBIDDEN')))
    vi.stubGlobal('fetch', reads); const { path, state } = setup(); await settle()
    expect(state?.count.value).toBe(7)
    path.value = '/admin/requests'; await settle()
    expect(state?.count.value).toBeNull(); expect(state?.badgeVisible.value).toBe(false)
    expect(state?.stale.value).toBe(false)
    expect(useAuthStore().authenticated).toBe(status === 403)
  })
  it('clears_the_global_count_when_another_tab_replaces_the_admin_cookie_with_a_reporter_session', async () => {
    let cookieRole: 'ADMIN' | 'REPORTER' = 'ADMIN'
    const reads = vi.fn((path: string) => Promise.resolve(cookieRole === 'ADMIN' ? pendingPage(7)
      : path.startsWith('/api/admin/repair-requests?') ? json(403, 'FORBIDDEN') : pendingPage(1)))
    vi.stubGlobal('fetch', reads); const { state } = setup(); await settle()
    expect(state?.count.value).toBe(7)
    // Browser cookies are shared across tabs; this tab's memory identity is still ADMIN.
    cookieRole = 'REPORTER'; browser.dispatchEvent(new Event('focus')); await settle()
    expect(useAuthStore().currentUser?.role).toBe('ADMIN')
    expect(state?.count.value).toBeNull(); expect(state?.badgeVisible.value).toBe(false)
    expect(state?.stale.value).toBe(false); expect(state?.message.value).not.toBe('')
    expect(reads).toHaveBeenCalledTimes(2)
    expect(reads.mock.calls[1]?.[0]).toBe('/api/admin/repair-requests?page=1&pageSize=1&status=PENDING_REVIEW')
  })
  it('clears_a_count_synchronously_when_session_confirmation_is_lost', async () => {
    const { state } = setup(); await settle(); expect(state?.count.value).toBe(7)
    useAuthStore().recovery = 'RESTORING'
    expect(state?.count.value).toBeNull(); expect(state?.badgeVisible.value).toBe(false)
    await settle(); await vi.advanceTimersByTimeAsync(30_000); expect(fetch).toHaveBeenCalledTimes(1)
  })
  it('discards_a_late_response_after_the_user_loses_the_admin_role', async () => {
    let finish!: (response: Response) => void
    const reads = vi.fn().mockImplementation(() => new Promise<Response>(resolve => { finish = resolve }))
    vi.stubGlobal('fetch', reads); const { state } = setup(); await settle()
    useAuthStore().setConfirmedUser({ ...admin, role: 'REPORTER', capabilities: { ...admin.capabilities, canManage: false } })
    finish?.(pendingPage(13)); await settle()
    expect(state?.count.value).toBeNull(); expect(state?.badgeVisible.value).toBe(false)
    await vi.advanceTimersByTimeAsync(30_000); expect(reads).toHaveBeenCalledTimes(1)
  })
  it('isolates_a_new_identity_and_waits_for_the_old_transport_without_overlap', async () => {
    let finishOld!: (response: Response) => void, finishNew!: (response: Response) => void
    const reads = vi.fn().mockImplementationOnce(() => new Promise<Response>(resolve => { finishOld = resolve }))
      .mockImplementation(() => new Promise<Response>(resolve => { finishNew = resolve }))
    vi.stubGlobal('fetch', reads); const { state } = setup(); await settle()
    const auth = useAuthStore(); auth.clearIdentity(); auth.setConfirmedUser({ ...admin, id: '93', loginName: 'synthetic_new_admin' })
    expect(state?.count.value).toBeNull(); await settle(); expect(reads).toHaveBeenCalledTimes(1)
    finishOld?.(pendingPage(13)); await settle()
    expect(state?.count.value).toBeNull(); expect(reads).toHaveBeenCalledTimes(2)
    finishNew?.(pendingPage(3)); await settle(); expect(state?.count.value).toBe(3)
  })
  it('clears_a_same_account_new_session_count_when_identity_epoch_changes', async () => {
    const reads = vi.fn().mockImplementationOnce(() => Promise.resolve(pendingPage(7)))
      .mockImplementation(() => Promise.resolve(pendingPage(2)))
    vi.stubGlobal('fetch', reads); const { state } = setup(); await settle()
    useAuthStore().acceptSession({ user: structuredClone(admin), expiresAt: '2026-10-14T08:00:00.000Z' })
    expect(state?.count.value).toBeNull(); await settle()
    expect(state?.count.value).toBe(2); expect(reads).toHaveBeenCalledTimes(2)
  })
  it('coalesces_refresh_triggers_while_a_read_is_in_flight', async () => {
    let finish!: (response: Response) => void
    const reads = vi.fn().mockImplementationOnce(() => new Promise<Response>(resolve => { finish = resolve }))
      .mockImplementation(() => Promise.resolve(pendingPage(9)))
    vi.stubGlobal('fetch', reads); const { path, state } = setup(); await settle()
    path.value = '/admin/requests'; browser.dispatchEvent(new Event('focus'))
    notificationsModule?.requestReviewCountRefresh(); await settle(); expect(reads).toHaveBeenCalledTimes(1)
    finish?.(pendingPage(7)); await settle()
    expect(state?.count.value).toBe(9); expect(reads).toHaveBeenCalledTimes(2)
  })
  it('releases_timers_and_listeners_and_discards_pending_results_after_unmount', async () => {
    let finish!: (response: Response) => void
    const reads = vi.fn().mockImplementation(() => new Promise<Response>(resolve => { finish = resolve }))
    vi.stubGlobal('fetch', reads); const { scope, path, state } = setup(); await settle()
    scope.stop(); finish?.(pendingPage(13)); await settle()
    path.value = '/admin/requests'; browser.dispatchEvent(new Event('focus'))
    page.dispatchEvent(new Event('visibilitychange')); notificationsModule?.requestReviewCountRefresh()
    await settle(); await vi.advanceTimersByTimeAsync(90_000)
    expect(state?.count.value).toBeNull(); expect(reads).toHaveBeenCalledTimes(1)
    expect(vi.getTimerCount()).toBe(0)
  })
})

describe('管理员未完成工单导航数量', () => {
  const counted = (count: number) => json(200, 'OK', { userId: '91', role: 'ADMIN', count })
  it('keeps unfinished orders visible on list entry and refreshes until completion hides zero', async () => {
    const reads = vi.fn().mockResolvedValueOnce(counted(3)).mockResolvedValueOnce(counted(3)).mockResolvedValueOnce(counted(2)).mockResolvedValue(counted(0))
    vi.stubGlobal('fetch', reads); const mine = setup('management'); await settle()
    expect(String(reads.mock.calls[0]?.[0])).toBe('/api/work-orders/navigation-count')
    expect(mine.state?.count.value).toBe(3); expect(mine.state?.badgeVisible.value).toBe(true)
    mine.path.value = '/admin/work-orders'; await settle(); expect(mine.state?.count.value).toBe(3)
    notificationsModule?.requestReviewCountRefresh(); await settle(); expect(mine.state?.count.value).toBe(2)
    notificationsModule?.requestReviewCountRefresh(); await settle(); expect(mine.state?.count.value).toBe(0); expect(mine.state?.badgeVisible.value).toBe(false)
  })
  it.each(['TECHNICIAN', 'REPORTER'] as const)('does not expose the management quantity to %s', async role => {
    useAuthStore().setConfirmedUser({ ...admin, role, capabilities: { ...admin.capabilities, canManage: false } })
    setup('management'); await settle(); expect(fetch).not.toHaveBeenCalled()
  })
})
