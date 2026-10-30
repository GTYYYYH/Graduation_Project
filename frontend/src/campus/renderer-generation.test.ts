import { describe, expect, it, vi } from 'vitest'
import { LoadCoordinator, withLoadTimeout } from './LoadCoordinator'
import { ResourceCache } from './ResourceCache'
import { ref } from 'vue'
import { clearSceneRetry, isCurrentSceneRetry, ObservationIntent, saveSceneRetry, takeSceneRetry, useEntryPermission } from './observation'
import type { RendererState } from './types'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<T>((a, b) => { resolve = a; reject = b })
  return { promise, resolve, reject }
}
describe('render target lifecycle', () => {
  it('explicit_module_retry_keeps_public_target_codes_once_across_page_reload', () => {
    const values = new Map<string, string>()
    const storage = { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => { values.set(key, value) }, removeItem: (key: string) => { values.delete(key) } }
    const target = { configVersion: 'campus-v1', kind: 'LOCATION' as const, code: 'wb1-f1-r101', enter: true }
    saveSceneRetry(storage, target, 'a'.repeat(64), 1000)
    expect(takeSceneRetry(storage, 2000)).toEqual({ ...target, sessionScope: 'a'.repeat(64), expiresAt: 61000 })
    expect(takeSceneRetry(storage, 2000)).toBeNull()
    expect(values.size).toBe(0)
  })
  it('expired_or_malformed_reload_intent_is_removed_and_cannot_enter', () => {
    let value: string | null = null
    const storage = { getItem: () => value, setItem: (_key: string, next: string) => { value = next }, removeItem: () => { value = null } }
    saveSceneRetry(storage, { configVersion: 'campus-v1', kind: 'LOCATION', code: 'wb1-f1-r101', enter: true }, 'a'.repeat(64), 1000)
    expect(takeSceneRetry(storage, 61000)).toBeNull(); expect(value).toBeNull()
    value = '{bad json'; expect(takeSceneRetry(storage, 2000)).toBeNull(); expect(value).toBeNull()
    value = JSON.stringify({ configVersion: 'campus-v1', kind: 'OVERVIEW', code: '', enter: true, expiresAt: 60000 })
    expect(takeSceneRetry(storage, 2000)).toBeNull(); expect(value).toBeNull()
  })
  it('reload_enter_requires_the_same_session_and_live_deadline_at_application_time', () => {
    let value: string | null = null
    const storage = { getItem: () => value, setItem: (_key: string, next: string) => { value = next }, removeItem: () => { value = null } }
    saveSceneRetry(storage, { configVersion: 'campus-v1', kind: 'LOCATION', code: 'wb1-f1-r101', enter: true }, 'a'.repeat(64), 1000)
    const pending = takeSceneRetry(storage, 2000)
    expect(isCurrentSceneRetry(pending, 'a'.repeat(64), 3000)).toBe(true)
    expect(isCurrentSceneRetry(pending, 'b'.repeat(64), 3000)).toBe(false)
    expect(isCurrentSceneRetry(pending, 'a'.repeat(64), 61000)).toBe(false)
    expect(isCurrentSceneRetry(null, 'a'.repeat(64), 3000)).toBe(false)
  })
  it('failed_session_recovery_or_public_route_discards_the_pending_reload_intent', () => {
    let value: string | null = null
    const storage = { getItem: () => value, setItem: (_key: string, next: string) => { value = next }, removeItem: () => { value = null } }
    saveSceneRetry(storage, { configVersion: 'campus-v1', kind: 'LOCATION', code: 'wb1-f1-r101', enter: true }, 'a'.repeat(64), 1000)
    clearSceneRetry(storage)
    expect(takeSceneRetry(storage, 2000)).toBeNull()
  })
  it('initialization_deadline_reports_even_after_manifest_resolves_while_module_is_pending', async () => {
    vi.useFakeTimers()
    try {
      const module = deferred<string>(), feedback = vi.fn()
      const pending = withLoadTimeout(() => Promise.all([module.promise, Promise.resolve('manifest')]), feedback)
      await vi.advanceTimersByTimeAsync(14999); expect(feedback).not.toHaveBeenCalled()
      await vi.advanceTimersByTimeAsync(1); expect(feedback).toHaveBeenCalledOnce()
      module.resolve('renderer'); expect(await pending).toEqual(['renderer', 'manifest'])
      await vi.advanceTimersByTimeAsync(15000); expect(feedback).toHaveBeenCalledOnce()
    } finally { vi.useRealTimers() }
  })
  it('completed_initialization_cancels_its_timeout_feedback', async () => {
    vi.useFakeTimers()
    try {
      const feedback = vi.fn()
      expect(await withLoadTimeout(async () => 'ready', feedback)).toBe('ready')
      await vi.advanceTimersByTimeAsync(15000); expect(feedback).not.toHaveBeenCalled()
    } finally { vi.useRealTimers() }
  })
  it('reselecting_same_room_or_aba_selection_cancels_old_restore_enter', async () => {
    for (const selections of [['101'], ['102', '101']]) {
      const intent = new ObservationIntent(), mounting = deferred<void>(), entered: string[] = []
      let currentLocation = '101'
      intent.enter('101'); const restore = intent.capture()
      const pending = mounting.promise.then(() => { if (intent.canResume(restore, currentLocation)) entered.push(currentLocation) })
      for (const id of selections) { currentLocation = id; intent.select() }
      mounting.resolve(); await pending
      expect(currentLocation).toBe('101'); expect(entered).toEqual([])
      expect(intent.capture()).toBeNull()
    }
  })
  it('failed_enter_retains_the_explicit_interior_intent_for_retry', async () => {
    const intent = new ObservationIntent()
    intent.enter('101')
    await expect(Promise.reject(new Error('inner module unavailable'))).rejects.toThrow('unavailable')
    const retry = intent.capture()
    expect(intent.canResume(retry, '101')).toBe(true)
    expect(intent.canResume(retry, '102')).toBe(false)
    intent.select(); expect(intent.canResume(retry, '101')).toBe(false)
  })
  it('new_enter_or_return_intent_invalidates_an_old_restore', () => {
    const intent = new ObservationIntent()
    intent.enter('101'); const old = intent.capture()
    intent.enter('106'); expect(intent.canResume(old, '101')).toBe(false)
    expect(intent.canResume(intent.capture(), '106')).toBe(true)
    const latest = intent.capture(); intent.select()
    expect(intent.canResume(latest, '106')).toBe(false)
  })
  it('entry_unlocks_after_async_mount_and_only_for_current_ready_door', () => {
    const ready = ref(false), error = ref(''), state = ref<RendererState>({ level: 'DOOR', locationId: '101', loading: false, error: '' }), location = ref('101')
    const allowed = useEntryPermission(ready, error, state, location)
    expect(allowed.value).toBe(false)
    ready.value = true; expect(allowed.value).toBe(true)
    state.value.loading = true; expect(allowed.value).toBe(false)
    state.value.loading = false; location.value = '102'; expect(allowed.value).toBe(false)
    location.value = '101'; error.value = 'init failed'; expect(allowed.value).toBe(false)
    error.value = ''; ready.value = false; expect(allowed.value).toBe(false)
    ready.value = true; state.value.error = 'WebGL context lost'; expect(allowed.value).toBe(false)
  })
  it('last_target_wins', async () => {
    const loads = new LoadCoordinator(), old = deferred<string>(), latest = deferred<string>()
    let displayedTargetId = ''
    const release = vi.fn(), lastSelectedTargetId = 'new-location'
    const first = loads.run(loads.begin(1, 'old-location'), () => old.promise, v => { displayedTargetId = v }, release)
    const second = loads.run(loads.begin(2, lastSelectedTargetId), () => latest.promise, v => { displayedTargetId = v }, release)
    latest.resolve(lastSelectedTargetId); await second
    old.resolve('old-location'); expect(await first).toBe(false)
    expect(displayedTargetId).toBe(lastSelectedTargetId)
    expect(release).toHaveBeenCalledWith('old-location')
  })
  it('late_result_after_identity_change_or_dispose_cannot_restore_scene', async () => {
    const loads = new LoadCoordinator(), old = deferred<string>(), visible: string[] = [], released: string[] = []
    const pending = loads.run(loads.begin(1, '101'), () => old.promise, v => visible.push(v), v => released.push(v))
    loads.dispose(); old.resolve('101'); await pending
    expect(visible).toEqual([]); expect(released).toEqual(['101'])
  })
  it('old_retry_generation_cannot_cancel_latest_target', async () => {
    const loads = new LoadCoordinator(), visible: string[] = []
    const current = loads.begin(4, '110')
    const obsolete = loads.begin(3, '101')
    await loads.run(obsolete, async () => '101', v => visible.push(v), () => {})
    await loads.run(current, async () => '110', v => visible.push(v), () => {})
    expect(visible).toEqual(['110'])
  })
  it('obsolete_failure_does_not_replace_current_feedback', async () => {
    const loads = new LoadCoordinator(), old = deferred<string>()
    const pending = loads.run(loads.begin(1, '101'), () => old.promise, () => {}, () => {})
    loads.begin(2, '102'); old.reject(new Error('obsolete failure'))
    expect(await pending).toBe(false)
  })
  it('return_or_reset_invalidates_pending_load_without_reusing_it', async () => {
    const loads = new LoadCoordinator(), old = deferred<string>(), shown: string[] = []
    const pending = loads.run(loads.begin(1, '101'), () => old.promise, v => shown.push(v), () => {})
    loads.cancel(); old.resolve('101'); await pending
    expect(shown).toEqual([])
    await loads.run(loads.begin(2, '102'), async () => '102', v => shown.push(v), () => {})
    expect(shown).toEqual(['102'])
  })
})
describe('shared resource ownership', () => {
  it('releasing_one_room_preserves_shared_resources_until_last_reference', () => {
    const pool = new ResourceCache(), freed: string[] = []
    const factory = () => ({ dispose: () => freed.push('wood') })
    const a = pool.acquire('wood', factory, 40), b = pool.acquire('wood', factory, 40)
    expect(a.value).toBe(b.value)
    a.release(); a.release(); expect(freed).toEqual([])
    expect(pool.stats()).toEqual({ entries: 1, references: 1, estimatedBytes: 40 })
    b.release(); expect(freed).toEqual(['wood']); expect(pool.stats().entries).toBe(0)
  })
  it('release_all_is_idempotent_and_late_lease_release_cannot_double_free', () => {
    const pool = new ResourceCache(), freed: string[] = []
    const lease = pool.acquire('mesh', () => ({ dispose: () => freed.push('mesh') }), 12)
    pool.dispose(); pool.dispose(); lease.release()
    expect(freed).toEqual(['mesh']); expect(pool.stats().estimatedBytes).toBe(0)
    expect(() => pool.acquire('late', () => ({ dispose() {} }), 1)).toThrow()
  })
})
