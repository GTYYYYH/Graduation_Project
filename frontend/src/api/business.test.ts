import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { businessMutation } from './business'
import { apiImage, apiMutation } from './client'
import { useAuthStore } from '../stores/auth'
const op = 'd17d8d3e-9dad-4939-87e9-37b3e429d2b9'
const receipt = { operationId: op, resourceType: 'REPAIR_REQUEST', resourceId: '9007199254740993', resultVersion: 0, resultRound: null, committedAt: '2026-10-13T00:00:00.000Z', relatedResourceType: null, relatedResourceId: null }
function envelope(status: number, data: unknown, code = 'OK') { return new Response(JSON.stringify({ code, message: '反馈', data, requestId: 'fixture' }), { status, headers: { 'Content-Type': 'application/json' } }) }
beforeEach(() => { setActivePinia(createPinia()); useAuthStore().csrf = { token: 'csrf-fixture', headerName: 'X-CSRF-TOKEN' } })
afterEach(() => vi.unstubAllGlobals())
it('invalid_success_receipt_stays_unknown_and_keeps_original_operation', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(envelope(201, { ...receipt, resourceId: 9007199254740992 })))
  const r = await businessMutation('/api/repair-requests', 'POST', { operationId: op }, 'REPAIR_REQUEST')
  expect(r.kind).toBe('UNKNOWN'); if (r.kind === 'UNKNOWN') expect(r.operationId).toBe(op)
})
it('receipt_of_another_operation_or_resource_is_not_accepted', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(envelope(200, { ...receipt, operationId: crypto.randomUUID() })))
  expect((await businessMutation('/api/repair-requests/44/withdraw', 'POST', { operationId: op }, 'REPAIR_REQUEST', '44')).kind).toBe('UNKNOWN')
})
it('valid_receipt_preserves_string_id_and_confirms_success', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(envelope(201, receipt)))
  const r = await businessMutation('/api/repair-requests', 'POST', { operationId: op }, 'REPAIR_REQUEST')
  expect(r.kind).toBe('SUCCESS'); if (r.kind === 'SUCCESS') expect(r.data.resourceId).toBe('9007199254740993')
})
it('cancel_has_csrf_and_original_operation_header_without_a_fake_json_body', async () => {
  const fetcher = vi.fn().mockResolvedValue(envelope(200, null)); vi.stubGlobal('fetch', fetcher)
  const r = await apiMutation('/api/attachments/44/temp', 'DELETE', undefined, { operationId: op })
  expect(r.kind).toBe('SUCCESS'); const request = fetcher.mock.calls[0]?.[1] as RequestInit
  expect(new Headers(request.headers).get('X-Operation-Id')).toBe(op); expect(new Headers(request.headers).get('X-CSRF-TOKEN')).toBe('csrf-fixture'); expect(request.body).toBeUndefined()
})
it('protected_image_uses_cookie_no_store_and_returns_a_binary_blob', async () => {
  const fetcher = vi.fn().mockResolvedValue(new Response(new Uint8Array([1, 2]), { headers: { 'Content-Type': 'image/png', 'X-Request-Id': 'fixture' } })); vi.stubGlobal('fetch', fetcher)
  const r = await apiImage('/api/attachments/44/content'); expect(r.kind).toBe('SUCCESS')
  if (r.kind === 'SUCCESS') expect(r.data.type).toBe('image/png')
  expect(fetcher.mock.calls[0]?.[1]).toMatchObject({ credentials: 'same-origin', cache: 'no-store' })
})
it('protected_image_permission_error_keeps_identity_and_does_not_become_a_blob', async () => {
  const auth = useAuthStore(); auth.currentUser = { id: '44' } as typeof auth.currentUser
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(envelope(403, null, 'RECORD_FORBIDDEN')))
  const r = await apiImage('/api/attachments/44/content'); expect(r.kind).toBe('HTTP_ERROR'); expect(auth.currentUser?.id).toBe('44')
})
it('work_order_receipt_from_old_round_stays_unknown', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(envelope(200, { ...receipt, resourceType: 'WORK_ORDER', resourceId: '44', resultVersion: 4, resultRound: 1 })))
  const result = await businessMutation('/api/work-orders/44/submit-result', 'POST', { operationId: op, version: 3, round: 2 }, 'WORK_ORDER', '44')
  expect(result.kind).toBe('UNKNOWN')
})
