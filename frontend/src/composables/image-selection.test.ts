import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { addImageFiles, disposeImageSelection, ensureTemporaryUploaded } from './useImageSelection'
beforeEach(() => vi.stubGlobal('fetch', vi.fn()))
afterEach(() => vi.unstubAllGlobals())
const photo = (name = 'fault.png', size = 3) => new File([new Uint8Array(size)], name, { type: 'image/png' })
const context = { purpose: 'REQUEST_FAULT' as const, batchId: '770b5470-ccae-4449-a012-3b9db65b7611' }
it('selection_does_not_upload', () => {
  const selected = addImageFiles([], [photo()]).selection
  const uploadCallsAfterSelection = vi.mocked(fetch).mock.calls.length
  expect(uploadCallsAfterSelection).toBe(0)
  expect(selected).toHaveLength(1)
  expect(selected[0]?.temporaryId).toBeNull()
  expect(selected[0]?.file.name).toBe('fault.png')
  disposeImageSelection(selected)
})
it('rejects_sixth_zero_oversized_and_mime_extension_mismatch', () => {
  const first = addImageFiles([], Array.from({ length: 6 }, () => photo()))
  expect(first.selection).toHaveLength(5); expect(first.errors.length).toBeGreaterThan(0)
  const invalid = addImageFiles([], [photo('empty.png', 0), photo('too-large.png', 5_000_001), photo('wrong.jpg')])
  expect(invalid.selection).toHaveLength(0); expect(invalid.errors).toHaveLength(3)
  disposeImageSelection(first.selection)
})
it('partial_failure_keeps_files_and_confirmed_ids_and_retry_only_uploads_the_unconfirmed_file', async () => {
  const selected = addImageFiles([], [photo('a.png'), photo('b.png')]).selection
  let calls = 0
  const upload = async () => {
    calls++
    if (calls === 2) return { kind: 'HTTP_ERROR' as const, status: 413, response: { code: 'ATTACHMENT_TOO_LARGE', message: '过大', data: null, requestId: 'fixture' } }
    return { kind: 'SUCCESS' as const, data: { id: calls === 1 ? '9007199254740993' : '9007199254740994', originalName: 'fault.png', mediaType: 'image/png' as const, byteSize: 3, purpose: 'REQUEST_FAULT' as const, state: 'TEMPORARY' as const, uploadedAt: '2026-10-13T00:00:00.000Z', expiresAt: '2026-10-14T00:00:00.000Z', contentUrl: '/api/attachments/9007199254740993/content' }, requestId: 'fixture' }
  }
  const first = await ensureTemporaryUploaded(selected, context, upload)
  expect(first.kind).toBe('FAILED'); expect(selected[0]?.temporaryId).toBe('9007199254740993'); expect(selected[1]?.file.name).toBe('b.png')
  const second = await ensureTemporaryUploaded(selected, context, upload)
  expect(second.kind).toBe('SUCCESS'); expect(calls).toBe(3)
  if (second.kind === 'SUCCESS') expect(second.ids).toEqual(['9007199254740993', '9007199254740994'])
  disposeImageSelection(selected)
})
it('unknown_upload_preserves_original_operation_and_requires_same_context_on_retry', async () => {
  const selected = addImageFiles([], [photo()]).selection
  const operation = selected[0]?.uploadOperationId
  const result = await ensureTemporaryUploaded(selected, context, async () => ({ kind: 'UNKNOWN', operationId: operation ?? null, message: '结果待确认' }))
  expect(result.kind).toBe('FAILED'); expect(selected[0]?.file.name).toBe('fault.png'); expect(selected[0]?.uploadOperationId).toBe(operation)
  const wrong = await ensureTemporaryUploaded(selected, { ...context, batchId: crypto.randomUUID() }, async () => { throw new Error('must not upload for another target') })
  expect(wrong.kind).toBe('FAILED'); disposeImageSelection(selected)
})
it('disposal_releases_each_preview_url', () => {
  const revoked: string[] = [], urls = { createObjectURL: () => 'blob:fixture', revokeObjectURL: (value: string) => revoked.push(value) }
  const selected = addImageFiles([], [photo()], urls).selection
  disposeImageSelection(selected, urls); expect(revoked).toEqual(['blob:fixture'])
})
it('identity_change_during_upload_discards_late_reference_and_never_uploads_another_file_as_the_new_account', async () => {
  const selected = addImageFiles([], [photo('a.png'), photo('b.png')]).selection
  let current = true, calls = 0, resolve!: (value: Awaited<ReturnType<import('./useImageSelection').Upload>>) => void
  const late = { kind: 'SUCCESS' as const, requestId: 'fixture', data: { id: '44', originalName: 'a.png', mediaType: 'image/png' as const, byteSize: 3, purpose: 'REQUEST_FAULT', state: 'TEMPORARY', uploadedAt: '2026-10-13T00:00:00.000Z', expiresAt: '2026-10-14T00:00:00.000Z', contentUrl: '/api/attachments/44/content' } }
  const promise = ensureTemporaryUploaded(selected, context, async () => { calls++; return calls > 1 ? late : new Promise(r => { resolve = r }) }, () => current)
  current = false
  resolve({ kind: 'SUCCESS', requestId: 'fixture', data: { id: '44', originalName: 'a.png', mediaType: 'image/png', byteSize: 3, purpose: 'REQUEST_FAULT', state: 'TEMPORARY', uploadedAt: '2026-10-13T00:00:00.000Z', expiresAt: '2026-10-14T00:00:00.000Z', contentUrl: '/api/attachments/44/content' } })
  const result = await promise
  expect(result.kind).toBe('FAILED'); expect(calls).toBe(1); expect(selected[0]?.temporaryId).toBeNull(); disposeImageSelection(selected)
})
