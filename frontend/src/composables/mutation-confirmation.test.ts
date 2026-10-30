import { expect, it, vi } from 'vitest'
import { freezeSubmission, settleSubmission, applyConfirmedOperation } from './useMutationConfirmation'
import type { PendingSubmission } from '../stores/drafts'
const op = 'd17d8d3e-9dad-4939-87e9-37b3e429d2b9'
const pending = (): PendingSubmission => ({ operationId: op, operation: 'CREATE_REQUEST', path: '/api/repair-requests', method: 'POST', payload: { operationId: op, description: 'original', attachmentIds: ['44'] }, resourceType: 'REPAIR_REQUEST', phase: 'SENDING' })
it('only_a_confirmed_original_receipt_invalidates_campus_reads',()=> {
  const browser=new EventTarget(),changed=vi.fn();vi.stubGlobal('window',browser);browser.addEventListener('campus:business-confirmed',changed)
  const draft={pending:pending() as PendingSubmission|null}
  settleSubmission(draft,{kind:'UNKNOWN',operationId:op,message:'lost'},op);applyConfirmedOperation(draft,{kind:'NOT_CONFIRMED',receipt:null},op)
  expect(changed).not.toHaveBeenCalled()
  const receipt={operationId:op,resourceType:'REPAIR_REQUEST',resourceId:'44',resultVersion:0,resultRound:null,committedAt:'2026-10-18T13:00:00.000Z',relatedResourceType:null,relatedResourceId:null}
  expect(applyConfirmedOperation(draft,{kind:'SUCCEEDED',receipt},op)).toBe(true);expect(changed).toHaveBeenCalledTimes(1)
  expect(applyConfirmedOperation(draft,{kind:'SUCCEEDED',receipt},op)).toBe(false);expect(changed).toHaveBeenCalledTimes(1)
  vi.unstubAllGlobals()
})
it('unknown_keeps_original_payload_identifier_and_freezes_new_submission', () => {
  const draft = { pending: null as PendingSubmission | null }, input = pending(); freezeSubmission(draft, input)
  settleSubmission(draft, { kind: 'UNKNOWN', operationId: op, message: '待确认' }, op)
  expect(draft.pending?.operationId).toBe(op); expect(draft.pending?.phase).toBe('UNKNOWN')
  expect(() => freezeSubmission(draft, { ...pending(), operationId: crypto.randomUUID() })).toThrow()
})
it('frozen_payload_does_not_follow_later_input_changes', () => {
  const draft = { pending: null as PendingSubmission | null }, input = pending(); freezeSubmission(draft, input)
  input.payload.description = 'changed'; (input.payload.attachmentIds as string[]).push('45')
  expect(draft.pending?.payload.description).toBe('original'); expect(draft.pending?.payload.attachmentIds).toEqual(['44'])
})
it('not_confirmed_is_not_failure_and_does_not_allow_a_new_identifier', () => {
  const draft = { pending: pending() }; draft.pending.phase = 'UNKNOWN'
  applyConfirmedOperation(draft, { kind: 'NOT_CONFIRMED', receipt: null }, op)
  expect(draft.pending?.operationId).toBe(op)
})
it('confirmed_http_rejection_unfreezes_correction_but_a_stale_response_does_not_clear_new_pending', () => {
  const draft = { pending: pending() as PendingSubmission | null }
  settleSubmission(draft, { kind: 'HTTP_ERROR', status: 409, response: { code: 'VERSION_CONFLICT', message: '变化', data: null, requestId: 'fixture' } }, op)
  expect(draft.pending).toBeNull(); freezeSubmission(draft, { ...pending(), operationId: 'a59130a8-78c7-4333-b854-ef1703313e2a' })
  settleSubmission(draft, { kind: 'UNKNOWN', operationId: op, message: 'late' }, op)
  expect(draft.pending?.operationId).toBe('a59130a8-78c7-4333-b854-ef1703313e2a'); expect(draft.pending?.phase).toBe('SENDING')
})
it('a_receipt_with_wrong_version_or_missing_approved_order_keeps_the_original_submission_frozen', () => {
  const draft = { pending: pending() as PendingSubmission | null }
  const receipt = { operationId: op, resourceType: 'REPAIR_REQUEST', resourceId: '44', resultVersion: 9, resultRound: null, committedAt: '2026-10-13T00:00:00.000Z', relatedResourceType: null, relatedResourceId: null }
  expect(applyConfirmedOperation(draft, { kind: 'SUCCEEDED', receipt }, op)).toBe(false)
  expect(draft.pending?.operationId).toBe(op)
  draft.pending = { ...pending(), operation: 'APPROVE_ASSIGN', resourceId: '44', payload: { operationId: op, version: 0 } }
  expect(applyConfirmedOperation(draft, { kind: 'SUCCEEDED', receipt: { ...receipt, resultVersion: 1 } }, op)).toBe(false)
  expect(draft.pending?.operationId).toBe(op)
})
