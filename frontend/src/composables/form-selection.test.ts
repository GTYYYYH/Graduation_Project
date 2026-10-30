import { createSSRApp, defineComponent } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createPinia, setActivePinia } from 'pinia'
import { expect, it } from 'vitest'
import { useRequestForm } from './useRequestForm'
import { useAuthStore } from '../stores/auth'
import type { CatalogVo } from '../api/account'
import type { RequestDetail } from '../api/repair-request'
it('changing_supplement_building_requires_a_new_explicit_location_without_moving_the_request_draft', async () => {
  const pinia = createPinia(); setActivePinia(pinia)
  const auth = useAuthStore(); auth.setConfirmedUser({ id: '44', loginName: 'fixture', name: '演示', phone: '13800000000', role: 'REPORTER', enabled: true, reviewStatus: null, version: 0, capabilities: { canBrowseCampus: true, canCreateRequest: true, canWorkOnTasks: false, canManage: false } })
  const catalog: CatalogVo = { configVersion: 'campus-v1', buildings: [{ id: '1', code: 'wb1', name: '文博1', type: 'DORMITORY', displayOrder: 0 }, { id: '2', code: 'wb2', name: '文博2', type: 'DORMITORY', displayOrder: 1 }], floors: [{ id: '3', code: 'wb1-f1', buildingId: '1', level: 1, name: '首层', displayOrder: 0 }, { id: '4', code: 'wb2-f1', buildingId: '2', level: 1, name: '首层', displayOrder: 0 }], locations: [{ id: '5', code: 'wb1-f1-r101', floorId: '3', type: 'ROOM', use: 'DORM_ROOM', name: '101', reportable: true, canCreateRequest: true, displayOrder: 0 }, { id: '6', code: 'wb2-f1-r101', floorId: '4', type: 'ROOM', use: 'DORM_ROOM', name: '101', reportable: true, canCreateRequest: true, displayOrder: 0 }] }
  const submission = { id: '9', submissionNo: 1, equipmentName: '空调', equipmentCategory: null, locationMode: 'STRUCTURED' as const, location: { id: '5', code: 'wb1-f1-r101', floorId: '3', name: '101' }, locationNameSnapshot: '文博1 · 首层 · 101', addressText: '原文', description: 'original', contactName: '演示', contactPhone: '13800000000', attachments: [], submittedAt: '2026-10-13T00:00:00.000Z' }
  const detail: RequestDetail = { id: '8', requestNo: 'RR-8', equipmentName: '空调', status: 'NEEDS_SUPPLEMENT', locationMode: 'STRUCTURED', location: submission.location, locationNameSnapshot: submission.locationNameSnapshot, firstSubmittedAt: submission.submittedAt, createdAt: submission.submittedAt, version: 1, workOrder: null, allowedActions: ['SUPPLEMENT_REQUEST', 'WITHDRAW_REQUEST'], reporter: { id: '44', name: '演示' }, currentSubmission: submission, submissions: [submission], events: [], matchedEquipment: null, queriedAt: submission.submittedAt }
  let form!: ReturnType<typeof useRequestForm>
  const app = createSSRApp(defineComponent({ setup() { form = useRequestForm({ catalog, detail }, () => {}); return () => null } })).use(pinia)
  await renderToString(app)
  form.draft.value!.description = 'editing'; const key = form.draft.value!.key
  form.chooseBuilding('2')
  expect(form.canSubmit.value).toBe(false); expect(form.draft.value!.locationId).toBe(''); expect(form.draft.value!.description).toBe('editing')
  form.chooseLocation('6'); expect(form.canSubmit.value).toBe(true); expect(form.draft.value!.key).toBe(key); expect(form.draft.value!.locationId).toBe('6')
})
