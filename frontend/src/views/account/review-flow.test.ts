import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createReviewFlow } from './review-flow'
import type { ReviewSnapshot } from './review-flow'
import { submitReviewDecision } from '../../api/account'
import type { ReviewDecisionDto } from '../../api/account'
import { useAuthStore } from '../../stores/auth'

const operationId = 'c1b8b90e-ec64-4f46-8df2-711ce0387f2a'
const input: ReviewDecisionDto = { operationId, version: 2, attempt: 1, reviewVersion: 0, passed: false, reason: '资料需重新核对' }
const snapshot: ReviewSnapshot = { targetId: '9007199254740993', identityEpoch: 1, version: 2, attempt: 1, reviewVersion: 0, allowed: true }
function success() { return new Response(JSON.stringify({ code: 'OK', message: '完成', requestId: 'test-request', data: {
  operationId, resourceType: 'ACCOUNT', resourceId: snapshot.targetId, resultVersion: 3, resultRound: null, committedAt: '2026-10-12T00:00:00.000Z', relatedResourceType: 'REGISTRATION_REVIEW', relatedResourceId: '9007199254740995',
} }), { status: 200, headers: { 'Content-Type': 'application/json' } }) }
beforeEach(() => { setActivePinia(createPinia()); useAuthStore().csrf = { token: 'csrf-for-fixture', headerName: 'X-CSRF-TOKEN' } })
afterEach(() => vi.unstubAllGlobals())
describe('审核确认与不确定结果', () => {
  it('confirmation_precedes_submission', async () => {
    const fetch = vi.fn().mockResolvedValue(success()); vi.stubGlobal('fetch', fetch)
    const flow = createReviewFlow<ReviewDecisionDto>(dto => submitReviewDecision(snapshot.targetId, dto), () => ({ ...snapshot }))
    flow.prepare({ ...input }); const reviewRequestsBeforeConfirmation = fetch.mock.calls.length
    expect(reviewRequestsBeforeConfirmation).toBe(0)
    await flow.confirm(); expect(flow.state.phase).toBe('SUCCEEDED'); expect(fetch).toHaveBeenCalledTimes(1)
  })
  it('cancel_and_return_to_edit_preserve_saved_state_and_reason', () => {
    const fetch = vi.fn(); vi.stubGlobal('fetch', fetch)
    const flow = createReviewFlow<ReviewDecisionDto>(dto => submitReviewDecision(snapshot.targetId, dto), () => ({ ...snapshot }))
    flow.prepare({ ...input }); flow.backToEdit()
    expect(flow.state.draft?.reason).toBe('资料需重新核对')
    expect(flow.state.phase).toBe('EDITING')
    flow.prepare({ ...input }); flow.cancel(); expect(flow.state.phase).toBe('IDLE'); expect(fetch).not.toHaveBeenCalled()
  })
  it('stale_application_stops_before_any_write', async () => {
    const fetch = vi.fn(); vi.stubGlobal('fetch', fetch); let current = { ...snapshot }
    const flow = createReviewFlow<ReviewDecisionDto>(dto => submitReviewDecision(snapshot.targetId, dto), () => current)
    flow.prepare({ ...input }); current = { ...current, version: 3, attempt: 2 }
    await flow.confirm(); expect(flow.state.phase).toBe('STALE'); expect(fetch).not.toHaveBeenCalled()
  })
  it('unknown_keeps_original_payload_and_retries_only_on_explicit_confirmation', async () => {
    const fetch = vi.fn().mockRejectedValueOnce(new TypeError('network')).mockResolvedValueOnce(success()); vi.stubGlobal('fetch', fetch)
    let current = { ...snapshot }; const flow = createReviewFlow<ReviewDecisionDto>(dto => submitReviewDecision(snapshot.targetId, dto), () => current)
    const draft = { ...input }; flow.prepare(draft); await flow.confirm()
    expect(flow.state.phase).toBe('UNKNOWN'); expect(flow.state.pending?.operationId).toBe(operationId); expect(fetch).toHaveBeenCalledTimes(1)
    draft.reason = '不能替换原提交'; current = { ...current, version: 3, allowed: false }
    await flow.confirm(); expect(flow.state.phase).toBe('SUCCEEDED'); expect(fetch).toHaveBeenCalledTimes(2)
    const first = JSON.parse((fetch.mock.calls[0]?.[1] as RequestInit).body as string)
    const second = JSON.parse((fetch.mock.calls[1]?.[1] as RequestInit).body as string)
    expect(second).toEqual(first); expect(second.reason).toBe('资料需重新核对')
  })
  it('disabled_or_lost_identity_cannot_confirm', async () => {
    const fetch = vi.fn(); vi.stubGlobal('fetch', fetch); let current: ReviewSnapshot | null = { ...snapshot }
    const flow = createReviewFlow<ReviewDecisionDto>(dto => submitReviewDecision(snapshot.targetId, dto), () => current)
    flow.prepare({ ...input }); current = null; await flow.confirm()
    expect(flow.state.phase).toBe('STALE'); expect(fetch).not.toHaveBeenCalled()
  })
  it('late_response_after_target_change_does_not_report_success', async () => {
    let resolve!: (r: Response) => void
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => new Promise<Response>(r => { resolve = r })))
    let current = { ...snapshot }; const flow = createReviewFlow<ReviewDecisionDto>(dto => submitReviewDecision(snapshot.targetId, dto), () => current)
    flow.prepare({ ...input }); const sent = flow.confirm(); current = { ...current, targetId: '9007199254740994' }
    if (resolve) resolve(success()); await sent
    expect(flow.state.phase).toBe('STALE')
  })
})
