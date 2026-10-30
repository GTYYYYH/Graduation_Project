import { defineStore } from 'pinia'
import { reactive, watch, computed } from 'vue'
import type { ImageSelection } from '../composables/useImageSelection'
import { disposeImageSelection } from '../composables/useImageSelection'
import { useAuthStore } from './auth'
export interface PendingSubmission { operationId: string; operation: string; path: string; method: 'POST' | 'PATCH'; payload: Record<string, unknown>; resourceType: string; resourceId?: string; phase: 'SENDING' | 'UNKNOWN' }
export interface RequestDraft { key: string; locationId: string; equipmentName: string; equipmentCategory: string; addressText: string; description: string; contactName: string; contactPhone: string; version: number; batchId: string; images: ImageSelection[]; pending: PendingSubmission | null; initialFields: string }
export interface ActionDraft { key: string; pending: PendingSubmission | null; reason: string; result: string; progress: string; images: ImageSelection[]; selectedId: string; confirmedRefusal: boolean }
function fields(draft: RequestDraft) { return JSON.stringify([draft.locationId, draft.equipmentName, draft.equipmentCategory, draft.addressText, draft.description, draft.contactName, draft.contactPhone]) }
export const useDraftStore = defineStore('drafts', () => {
  const records = reactive(new Map<string, RequestDraft>()), auth = useAuthStore()
  const pendingActions = reactive(new Map<string, ActionDraft>())
  function action(user: string, kind: string, id: string) { const key = `${kind}/${user}/${id}`; if (!pendingActions.has(key)) pendingActions.set(key, { key, pending: null, reason: '', result: '', progress: '', images: [], selectedId: '', confirmedRefusal: false }); return pendingActions.get(key)! }
  function obtain(key: string, initial: Partial<RequestDraft>) {
    let draft = records.get(key)
    if (!draft) {
      const created: RequestDraft = { key, locationId: '', equipmentName: '', equipmentCategory: '', addressText: '', description: '', contactName: '', contactPhone: '', version: 0, batchId: crypto.randomUUID(), images: [], pending: null, initialFields: '', ...initial }
      created.key = key; created.initialFields = fields(created); records.set(key, created); draft = records.get(key)!
    }
    return draft
  }
  const newRequest = (user: string, location: string, initial: Partial<RequestDraft> = {}) => obtain(`CREATE_REQUEST/${user}/${location}`, { ...initial, locationId: location })
  const supplement = (user: string, request: string, initial: Partial<RequestDraft>) => obtain(`SUPPLEMENT_REQUEST/${user}/${request}`, initial)
  function clear(key: string) { const draft = records.get(key); if (draft) { disposeImageSelection(draft.images); draft.images.length = 0; draft.pending = null; records.delete(key) } }
  function clearAction(key: string) { const draft = pendingActions.get(key); if (draft) { disposeImageSelection(draft.images); draft.images.length = 0; draft.pending = null; pendingActions.delete(key) } }
  function clearOrderEditors(user: string, order: string) {
    const editors = new Set(['REFUSAL_DRAFT', 'PROGRESS_DRAFT', 'REPAIR_RESULT', 'ACCEPTANCE_DRAFT', 'ASSIGN_DRAFT'])
    for (const [key, draft] of [...pendingActions]) {
      const [kind, owner, resource] = key.split('/')
      if (!kind || owner !== user || resource !== order || !editors.has(kind)) continue
      // Frozen operations use separate keys; their original payload remains recoverable.
      disposeImageSelection(draft.images); draft.images.length = 0
      draft.reason = ''; draft.result = ''; draft.progress = ''; draft.selectedId = ''; draft.confirmedRefusal = false
      if (!draft.pending) pendingActions.delete(key)
    }
  }
  function clearAll() { for (const key of [...records.keys()]) clear(key); for (const key of [...pendingActions.keys()]) clearAction(key) }
  const hasUnsaved = computed(() => [...records.values()].some(draft => draft.pending !== null || draft.images.length > 0 || fields(draft) !== draft.initialFields) || [...pendingActions.values()].some(draft => draft.pending !== null || draft.reason !== '' || draft.result !== '' || draft.progress !== '' || draft.images.length > 0 || draft.selectedId !== ''))
  watch(() => auth.identityEpoch, clearAll, { flush: 'sync' })
  return { newRequest, supplement, clear, clearAction, clearOrderEditors, clearAll, hasUnsaved, action }
})
