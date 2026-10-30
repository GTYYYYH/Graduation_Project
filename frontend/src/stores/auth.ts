import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { CurrentUserVo, CsrfVo } from '../api/common'
import { fetchMe, fetchCsrf, submitLogin, logoutSession } from '../api/account'
import type { AuthSessionVo, LoginDto } from '../api/account'

export const useAuthStore = defineStore('auth', () => {
  const currentUser = ref<CurrentUserVo | null>(null)
  const passwordChangedNotice = ref(false)
  const csrf = ref<CsrfVo | null>(null)
  const recovery = ref<'IDLE' | 'RESTORING' | 'CONFIRMED' | 'ANONYMOUS' | 'ERROR'>('IDLE')
  const recoveryError = ref('')
  const expiresAt = ref<string | null>(null)
  const authenticationPending = ref(false)
  const identityEpoch = ref(0)
  let generation = 0
  const authenticated = computed(() => currentUser.value !== null)
  const businessRouteGranted = computed(() => authenticated.value && recovery.value === 'CONFIRMED' && !authenticationPending.value)
  function setConfirmedUser(user: CurrentUserVo) {
    if (currentUser.value?.id !== user.id) { identityEpoch.value++; csrf.value = null }
    if (currentUser.value?.id === user.id && currentUser.value.version > user.version) return
    currentUser.value = user
    recovery.value = 'CONFIRMED'
    recoveryError.value = ''
  }
  function clearIdentity() {
    passwordChangedNotice.value=false
    identityEpoch.value++; generation++
    currentUser.value = null
    csrf.value = null
    expiresAt.value = null
    authenticationPending.value = false
    recovery.value = 'ANONYMOUS'
    recoveryError.value = ''
  }
  async function restore(): Promise<boolean> {
    const ownGeneration = ++generation, epoch = identityEpoch.value
    recovery.value = 'RESTORING'; recoveryError.value = ''
    const result = await fetchMe()
    if (epoch !== identityEpoch.value || ownGeneration !== generation) return false
    if (result.kind === 'SUCCESS') {
      authenticationPending.value = false
      setConfirmedUser(result.data.user); expiresAt.value = result.data.expiresAt
      return true
    }
    if (result.kind === 'HTTP_ERROR' && result.status === 401) { clearIdentity(); return false }
    recovery.value = 'ERROR'
    recoveryError.value = result.kind === 'HTTP_ERROR' ? result.response.message : result.message
    return false
  }
  async function ensureCsrf(force = false): Promise<boolean> {
    if (csrf.value && !force) return true
    const epoch = identityEpoch.value
    const result = await fetchCsrf()
    if (epoch !== identityEpoch.value) return false
    if (result.kind === 'SUCCESS') { csrf.value = result.data; return true }
    csrf.value = null; return false
  }
  function acceptSession(session: AuthSessionVo) {
    passwordChangedNotice.value=false
    generation++; csrf.value = null; authenticationPending.value = false
    const previousEpoch = identityEpoch.value
    setConfirmedUser(session.user)
    // 每次新登录都隔离旧会话的业务请求，普通me资料刷新不推进代次。
    if (previousEpoch === identityEpoch.value) identityEpoch.value++
    expiresAt.value = session.expiresAt
  }
  function requireSessionRecheck(message: string) {
    identityEpoch.value++; generation++
    csrf.value = null; authenticationPending.value = true
    recovery.value = 'ERROR'; recoveryError.value = message
  }
  function staleAuthentication() {
    // 迟到认证响应可能已改变浏览器Cookie，保留展示身份但先停止业务，直到me重新核实。
    requireSessionRecheck('登录身份在请求期间发生变化，请重新核对当前会话')
    return { kind: 'STALE' as const, message: recoveryError.value }
  }
  async function login(dto: LoginDto) {
    if (authenticationPending.value) return { kind: 'NOT_SENT' as const, message: '请先核对当前会话，再重新登录' }
    const ownGeneration = ++generation, epoch = identityEpoch.value
    if (!await ensureCsrf()) return { kind: 'NOT_SENT' as const, message: '校验信息获取失败，请检查连接后重试' }
    if (ownGeneration !== generation || epoch !== identityEpoch.value) return staleAuthentication()
    const result = await submitLogin(dto)
    if (ownGeneration !== generation || epoch !== identityEpoch.value) return staleAuthentication()
    if (result.kind === 'SUCCESS') {
      acceptSession(result.data); const confirmedEpoch = identityEpoch.value, confirmedGeneration = generation
      await ensureCsrf(true)
      if (confirmedEpoch !== identityEpoch.value || confirmedGeneration !== generation) return staleAuthentication()
    }
    else if (result.kind === 'UNKNOWN') requireSessionRecheck(result.message)
    else if (result.kind === 'HTTP_ERROR' && result.response.code === 'CSRF_INVALID') await ensureCsrf(true)
    return result
  }
  async function logout() {
    if (authenticationPending.value) return { kind: 'NOT_SENT' as const, message: '请先核对当前会话，再退出登录' }
    const ownGeneration = ++generation, epoch = identityEpoch.value
    if (!await ensureCsrf()) return { kind: 'NOT_SENT' as const, message: '校验信息获取失败，退出尚未发出，请重试' }
    if (ownGeneration !== generation || epoch !== identityEpoch.value) return staleAuthentication()
    const result = await logoutSession()
    if (ownGeneration !== generation || epoch !== identityEpoch.value) return staleAuthentication()
    if (result.kind === 'SUCCESS') { clearIdentity(); await ensureCsrf(true) }
    else if (result.kind === 'UNKNOWN') requireSessionRecheck(result.message)
    else if (result.kind === 'HTTP_ERROR' && result.response.code === 'CSRF_INVALID') await ensureCsrf(true)
    return result
  }
  return { passwordChangedNotice, currentUser, csrf, recovery, recoveryError, expiresAt, identityEpoch, authenticationPending, authenticated, businessRouteGranted,
    setConfirmedUser, clearIdentity, restore, ensureCsrf, acceptSession, login, logout }
})

