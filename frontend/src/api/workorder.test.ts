import { expect, it } from 'vitest'
import { isOrderSummary } from './workorder'

const summary = { id: '71', workOrderNo: 'WO-71', requestId: '11', requestNo: 'RR-11', reportedEquipmentName: '空调', equipment: null, status: 'PENDING_ACCEPT', locationMode: 'STRUCTURED', location: { id: '31', code: 'wb1-f1-r101', name: '101', floorId: '30' }, locationNameSnapshot: '文博1·首层·101', createdAt: '2026-10-15T00:00:00.000Z', assignedTechnician: { id: '51', name: '维修人员A' }, acceptedTechnician: null, version: 0, round: 0, isRework: false, allowedActions: [], historyScope: 'FULL' }
it('accepts an explicitly unlinked order but rejects an omitted or malformed equipment reference', () => {
  expect(isOrderSummary(summary)).toBe(true)
  expect(isOrderSummary({ ...summary, equipment: undefined })).toBe(false)
  expect(isOrderSummary({ ...summary, equipment: { id: 41 } })).toBe(false)
  expect(isOrderSummary({ ...summary, equipment: { id: '41', equipmentNo: 'EQ-41', name: '空调', locationId: '31' } })).toBe(true)
})
