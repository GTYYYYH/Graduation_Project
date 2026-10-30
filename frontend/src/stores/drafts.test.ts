import { beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useDraftStore } from './drafts'
import { useAuthStore } from './auth'
import type { CurrentUserVo } from '../api/common'
beforeEach(() => setActivePinia(createPinia()))
const user: CurrentUserVo = { id: '9007199254740993', loginName: 'fixture', name: '演示', phone: '13800000000', role: 'REPORTER', enabled: true, reviewStatus: null, version: 0, capabilities: { canBrowseCampus: true, canCreateRequest: true, canWorkOnTasks: false, canManage: false } }
it('editing_location_a_does_not_change_b', () => {
  const store = useDraftStore(), a = store.newRequest(user.id, '9007199254740994'), draftB = store.newRequest(user.id, '9007199254740995')
  draftB.description = 'B'; a.description = 'A'
  expect(draftB.description).toBe('B'); expect(store.newRequest(user.id, '9007199254740994').description).toBe('A')
})
it('users_do_not_share_drafts_and_success_only_clears_the_submitted_key', () => {
  const store = useDraftStore(), a = store.newRequest(user.id, '1'), b = store.newRequest(user.id, '2'), other = store.newRequest('9007199254740996', '1')
  a.description = 'A'; b.description = 'B'; other.description = 'other'; store.clear(a.key)
  expect(store.newRequest(user.id, '1').description).toBe(''); expect(store.newRequest(user.id, '2').description).toBe('B'); expect(other.description).toBe('other')
})
it('supplement_location_change_stays_with_original_request_and_read_refresh_does_not_overwrite_editing', () => {
  const store = useDraftStore(); const draft = store.supplement(user.id, '44', { locationId: '1', description: 'original', version: 3 })
  draft.description = 'editing'; draft.locationId = '2'
  const refreshed = store.supplement(user.id, '44', { locationId: '3', description: 'server changed', version: 4 })
  expect(refreshed.key).toBe(draft.key); expect(refreshed.description).toBe('editing'); expect(refreshed.locationId).toBe('2'); expect(refreshed.version).toBe(3)
  expect(store.newRequest(user.id, '2').description).toBe('')
})
it('identity_change_and_same_account_new_login_release_old_drafts', () => {
  const auth = useAuthStore(); auth.setConfirmedUser(user); const store = useDraftStore()
  store.newRequest(user.id, '1').description = 'private'; auth.clearIdentity(); auth.setConfirmedUser({ ...user, id: '9007199254740996' })
  expect(store.newRequest(user.id, '1').description).toBe('')
  store.newRequest('9007199254740996', '1').description = 'new session draft'
  auth.acceptSession({ user: { ...user, id: '9007199254740996' }, expiresAt: '2026-10-13T08:00:00.000Z' })
  expect(store.newRequest('9007199254740996', '1').description).toBe('')
})
it('loss_of_one_record_only_clears_that_record', () => {
  const store = useDraftStore(), a = store.supplement(user.id, '44', { description: 'A' }), b = store.supplement(user.id, '45', { description: 'B' })
  store.clear(a.key); expect(store.supplement(user.id, '44', {}).description).toBe(''); expect(b.description).toBe('B')
})
it('pending_actions_are_isolated_by_user_and_record_and_cleared_on_identity_loss', () => {
  const auth = useAuthStore(); auth.setConfirmedUser(user); const store = useDraftStore()
  const a = store.action(user.id, 'WITHDRAW_REQUEST', '44'), other = store.action('9007199254740996', 'WITHDRAW_REQUEST', '44')
  a.pending = { operationId: 'd17d8d3e-9dad-4939-87e9-37b3e429d2b9', operation: 'WITHDRAW_REQUEST', path: '/api/repair-requests/44/withdraw', method: 'POST', payload: { version: 0 }, resourceType: 'REPAIR_REQUEST', resourceId: '44', phase: 'UNKNOWN' }
  expect(other.pending).toBeNull(); expect(store.action(user.id, 'WITHDRAW_REQUEST', '45').pending).toBeNull()
  auth.clearIdentity(); expect(store.action(user.id, 'WITHDRAW_REQUEST', '44').pending).toBeNull()
})
it('order_permission_loss_releases_editor_files_but_preserves_unknown_receipt_recovery', () => {
  const store = useDraftStore(), revoke = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
  const edit = store.action(user.id, 'REPAIR_RESULT', '71/1'), other = store.action(user.id, 'REPAIR_RESULT', '72/1')
  edit.result = 'private draft'; edit.images.push({ file: new File(['image'], 'repair.png', { type: 'image/png' }), previewUrl: 'blob:old-order', temporaryId: null, uploadOperationId: crypto.randomUUID() })
  other.result = 'keep other order'
  const frozen = store.action(user.id, 'RESULT_SUBMITTED', '71')
  frozen.pending = { operationId: crypto.randomUUID(), operation: 'RESULT_SUBMITTED', path: '/api/work-orders/71/submit-result', method: 'POST', payload: { version: 1, round: 1, result: 'submitted', attachmentIds: ['81'] }, resourceType: 'WORK_ORDER', resourceId: '71', phase: 'UNKNOWN' }
  const original = frozen.pending
  store.clearOrderEditors(user.id, '71')
  expect(edit.images).toHaveLength(0)
  expect(revoke).toHaveBeenCalledWith('blob:old-order')
  expect(store.action(user.id, 'REPAIR_RESULT', '71/1').result).toBe('')
  expect(other.result).toBe('keep other order')
  expect(frozen.pending).toBe(original)
  expect(frozen.pending?.payload.result).toBe('submitted')
  revoke.mockRestore()
})
