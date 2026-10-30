import { describe, expect, it, vi } from 'vitest'
import { prepareReviewOperation, reviewBlock } from './review-flow'
import type { ReviewSelection } from './review-flow'

const operationId = '11111111-1111-4111-8111-111111111111'
function selection(): ReviewSelection {
  return {
    request: { id: '11', status: 'PENDING_REVIEW', version: 0, locationMode: 'STRUCTURED', location: { id: '31', code: 'wb1-f1-101', name: '101', floorId: '30' } },
    equipment: { id: '41', equipmentNo: 'EQ-41', name: '马桶', locationId: '31', enabled: true, currentWorkOrder: null },
    technician: { id: '51', name: '合格维修人员', enabled: true, reviewStatus: 'APPROVED', eligible: true, refusal: [] },
    reason: '', ready: true,
  }
}
describe('admin request review', () => {
  it('first assignment needs only a qualified technician and never sends an equipment selection', () => {
    const fields = selection(); fields.equipment = null
    expect(reviewBlock('approve-and-assign', fields)).toBe('')
    expect(prepareReviewOperation('approve-and-assign', fields, operationId)?.payload).toEqual({ version: 0, operationId, technicianId: '51', confirmPreviouslyRefused: false })
  })
  it('duplicate closure still blocks a mismatched device location', async () => {
    const fields = selection(); fields.equipment!.locationId = '32'; fields.reason = '同一具体设备重复申请'
    const send = vi.fn()
    const pending = prepareReviewOperation('duplicate-close', fields, operationId)
    if (pending) await send(pending)
    const approveRequestsWithMismatchedLocation = send.mock.calls.length
    expect(approveRequestsWithMismatchedLocation).toBe(0)
    expect(reviewBlock('duplicate-close', fields)).toContain('位置')
  })
  it('missing or changed technician eligibility cannot submit', () => {
    const fields = selection(); fields.technician = null
    expect(prepareReviewOperation('approve-and-assign', fields, operationId)).toBeNull()
    fields.technician = { ...selection().technician!, enabled: false }
    expect(prepareReviewOperation('approve-and-assign', fields, operationId)).toBeNull()
  })
  it('irrelevant device status or location does not affect technician-only approval', () => {
    const fields = selection(); fields.equipment!.enabled = false
    expect(prepareReviewOperation('approve-and-assign', fields, operationId)?.payload).not.toHaveProperty('equipmentId')
    fields.equipment = { ...selection().equipment!, locationId: '32', currentWorkOrder: { id: '61' } }
    expect(reviewBlock('approve-and-assign', fields)).toBe('')
  })
  it('return and duplicate closure require actual reasons and equipment', () => {
    const fields = selection(); fields.reason = '   '
    expect(prepareReviewOperation('return', fields, operationId)).toBeNull()
    expect(prepareReviewOperation('duplicate-close', fields, operationId)).toBeNull()
    fields.reason = '同一设备已有报修'; fields.equipment = null
    expect(prepareReviewOperation('duplicate-close', fields, operationId)).toBeNull()
    expect(prepareReviewOperation('return', fields, operationId)?.payload).toMatchObject({ reason: fields.reason })
  })
  it('failed mutation retains selections and original frozen operation', async () => {
    const fields = selection(), saved = structuredClone(fields)
    const pending = prepareReviewOperation('approve-and-assign', fields, operationId)!
    await vi.fn().mockResolvedValue({ kind: 'HTTP_ERROR', status: 409 })(pending)
    expect(fields).toEqual(saved)
    fields.equipment!.id = '42'; fields.technician!.id = '52'
    expect(pending.payload).toEqual({ version: 0, operationId, technicianId: '51', confirmPreviouslyRefused: false })
  })
  it('legacy material can use manual equipment without inventing location', () => {
    const fields = selection(); fields.request = { ...fields.request!, locationMode: 'LEGACY_TEXT', location: null }
    fields.equipment!.locationId = null
    expect(prepareReviewOperation('approve-and-assign', fields, operationId)?.payload).not.toHaveProperty('locationId')
  })
  it('read failure, changed version/status and missing structured position block writes', () => {
    const fields = selection(); fields.ready = false
    expect(prepareReviewOperation('approve-and-assign', fields, operationId)).toBeNull()
    fields.ready = true; fields.request!.status = 'APPROVED'
    expect(prepareReviewOperation('return', fields, operationId)).toBeNull()
    fields.request!.status = 'PENDING_REVIEW'; fields.request!.location = null
    expect(prepareReviewOperation('approve-and-assign', fields, operationId)).toBeNull()
  })
})
