import { computed, onScopeDispose, readonly, shallowRef, toValue, watch } from 'vue'
import type { MaybeRefOrGetter } from 'vue'
import { fetchAdminRequests } from '../api/repair-request'
import { fetchPendingReviews } from '../api/account'
import { fetchWorkOrderNavigationCount } from '../api/workorder'
import { feedback } from '../api/business'
import { useAuthStore } from '../stores/auth'

const refreshEvent = 'campus:review-count-refresh'

/** Announce a confirmed business change; navigation counts are reread from the server. */
export function requestNavigationCountRefresh() {
  if (typeof window !== 'undefined') window.dispatchEvent(new Event(refreshEvent))
}
export const requestReviewCountRefresh = requestNavigationCountRefresh

export function useReviewNotifications(routePath: MaybeRefOrGetter<string>, kind: 'requests' | 'personnel' | 'tasks' | 'orders' | 'management' = 'requests') {
  const auth = useAuthStore()
  const count = shallowRef<number | null>(null), stale = shallowRef(false)
  const loading = shallowRef(false), message = shallowRef('')
  const hasBrowser = typeof window !== 'undefined' && typeof document !== 'undefined'
  const visible = shallowRef(hasBrowser && document.visibilityState !== 'hidden')
  const eligible = computed(() => {
    const user = auth.currentUser
    if (!auth.businessRouteGranted || !user?.enabled) return false
    if (kind === 'tasks') return user.role === 'TECHNICIAN' && user.capabilities.canWorkOnTasks
    if (kind === 'orders') return user.role === 'REPORTER'
    return user.role === 'ADMIN' && user.capabilities.canManage
  })
  const badgeVisible = computed(() => eligible.value && count.value !== null && count.value > 0)
  let generation = 0, inFlight = false, refreshQueued = false, disposed = false
  let timer: ReturnType<typeof setInterval> | null = null

  function stopPolling() {
    if (timer !== null) clearInterval(timer)
    timer = null
  }
  function clear() {
    generation++; refreshQueued = false; stopPolling()
    count.value = null; stale.value = false; loading.value = false; message.value = ''
  }
  async function refresh() {
    if (disposed || !eligible.value || !visible.value) return
    if (inFlight) { refreshQueued = true; return }
    inFlight = true; loading.value = true
    const ownGeneration = generation, epoch = auth.identityEpoch
    try {
      const result = kind === 'tasks' || kind === 'orders' || kind === 'management' ? await fetchWorkOrderNavigationCount()
        : kind === 'personnel' ? await fetchPendingReviews(1, '')
        : await fetchAdminRequests({ page: 1, pageSize: 1, status: 'PENDING_REVIEW' })
      if (disposed || ownGeneration !== generation || epoch !== auth.identityEpoch || !eligible.value || !visible.value) return
      if (result.kind === 'SUCCESS') {
        if ('count' in result.data) {
          if (result.data.userId !== auth.currentUser?.id || result.data.role !== auth.currentUser?.role) {
            count.value = null; stale.value = false; message.value = '会话身份已变化，请重新核对会话'; return
          }
          count.value = result.data.count
        } else count.value = result.data.total
        stale.value = false; message.value = ''
      } else {
        if (result.kind === 'HTTP_ERROR' && [401, 403].includes(result.status)) {
          count.value = null; stale.value = false
        } else stale.value = count.value !== null
        message.value = feedback(result)
      }
    } finally {
      // Keep the transport locked across identity changes until its read settles.
      inFlight = false
      if (ownGeneration === generation) loading.value = false
      if (refreshQueued) { refreshQueued = false; void refresh() }
    }
  }
  function resume() { void refresh() }
  function visibilityChanged() {
    visible.value = document.visibilityState !== 'hidden'
    if (!visible.value) {
      generation++; refreshQueued = false; loading.value = false
      stale.value = count.value !== null; stopPolling()
    }
  }

  // Clear synchronously; start a new read after the store's coupled changes settle.
  watch([() => auth.identityEpoch, () => auth.currentUser?.id, () => auth.currentUser?.role,
    () => auth.businessRouteGranted, () => auth.currentUser?.enabled, () => auth.currentUser?.capabilities.canManage,
    () => auth.currentUser?.capabilities.canWorkOnTasks], clear, { flush: 'sync' })
  watch([() => auth.identityEpoch, eligible, visible, () => toValue(routePath)], () => {
    stopPolling()
    if (disposed || !eligible.value || !visible.value) return
    timer = setInterval(() => { if (!inFlight) void refresh() }, 30_000)
    void refresh()
  }, { immediate: true })

  if (hasBrowser) {
    window.addEventListener('focus', resume)
    window.addEventListener(refreshEvent, resume)
    document.addEventListener('visibilitychange', visibilityChanged)
  }
  onScopeDispose(() => {
    disposed = true; clear()
    if (hasBrowser) {
      window.removeEventListener('focus', resume)
      window.removeEventListener(refreshEvent, resume)
      document.removeEventListener('visibilitychange', visibilityChanged)
    }
  })
  return { count: readonly(count), stale: readonly(stale), loading: readonly(loading),
    message: readonly(message), badgeVisible }
}
