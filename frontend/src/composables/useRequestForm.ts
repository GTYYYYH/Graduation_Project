import { computed, shallowRef, watch, onUnmounted } from 'vue'
import { fetchCatalog } from '../api/account'
import type { CatalogVo } from '../api/account'
import type { RequestDetail } from '../api/repair-request'
import type { BusinessReceipt } from '../api/common'
import { businessMutation, fetchOperation, feedback } from '../api/business'
import { uploadTemporary } from '../api/attachment'
import { useAuthStore } from '../stores/auth'
import { useDraftStore } from '../stores/drafts'
import type { RequestDraft, PendingSubmission } from '../stores/drafts'
import { ensureTemporaryUploaded, resetExpiredSelection } from './useImageSelection'
import type { UploadContext } from './useImageSelection'
import { freezeSubmission, settleSubmission, applyConfirmedOperation } from './useMutationConfirmation'

export function useRequestForm(options: { initialLocationId?: string; detail?: RequestDetail; catalog?: CatalogVo }, saved: (receipt: BusinessReceipt) => void, confirm: (message: string) => Promise<boolean> = async message => window.confirm(message)) {
  const auth = useAuthStore(), store = useDraftStore()
  const catalog = shallowRef<CatalogVo | null>(options.catalog ?? null), draft = shallowRef<RequestDraft | null>(null)
  const buildingId = shallowRef(''), floorId = shallowRef(''), locationId = shallowRef(''), message = shallowRef(''), busy = shallowRef(''), directoryLoading = shallowRef(false)
  let alive = true, directoryGeneration = 0
  const floors = computed(() => catalog.value?.floors.filter(f => f.buildingId === buildingId.value) ?? [])
  const locations = computed(() => catalog.value?.locations.filter(l => l.floorId === floorId.value) ?? [])
  const selected = computed(() => catalog.value?.locations.find(l => l.id === locationId.value))
  const legacy = computed(() => options.detail?.locationMode === 'LEGACY_TEXT')
  const canSubmit = computed(() => Boolean(draft.value && auth.businessRouteGranted && auth.currentUser?.capabilities.canCreateRequest && (!options.detail || (options.detail.allowedActions.includes('SUPPLEMENT_REQUEST') && draft.value.version === options.detail.version)) && (legacy.value || selected.value?.canCreateRequest)))
  const context = computed<UploadContext>(() => options.detail ? { purpose: 'REQUEST_SUPPLEMENT', repairRequestId: options.detail.id } : { purpose: 'REQUEST_FAULT', batchId: draft.value?.batchId })
  const historicalAttachments = computed(() => options.detail?.submissions.flatMap(s => s.attachments) ?? [])
  function address(id: string) {
    const l = catalog.value?.locations.find(l => l.id === id), f = catalog.value?.floors.find(f => f.id === l?.floorId), b = catalog.value?.buildings.find(b => b.id === f?.buildingId)
    return [b?.name, f?.name, l?.name].filter(Boolean).join(' · ')
  }
  function coordinates(id: string) { const l = catalog.value?.locations.find(l => l.id === id), f = catalog.value?.floors.find(f => f.id === l?.floorId); if (l && f) { buildingId.value = f.buildingId; floorId.value = f.id } }
  function chooseLocation(id: string) {
    if (busy.value) return
    if (options.detail) {
      if (legacy.value || !draft.value) return
      if (!id) { locationId.value = ''; draft.value.locationId = ''; return }
      if (!catalog.value?.locations.some(l => l.id === id && l.canCreateRequest)) { message.value = '请选择可报修位置'; return }
      locationId.value = id; coordinates(id); draft.value.locationId = id; return
    }
    locationId.value = id
    const user = auth.currentUser, l = catalog.value?.locations.find(l => l.id === id)
    if (!user || !l?.canCreateRequest) { draft.value = null; if (id) message.value = '该位置或当前身份不能新建报修'; return }
    coordinates(id); draft.value = store.newRequest(user.id, id, { addressText: address(id), contactName: user.name, contactPhone: user.phone }); message.value = ''
  }
  function chooseBuilding(id: string) { if (busy.value) return; buildingId.value = id; floorId.value = floors.value[0]?.id ?? ''; chooseLocation('') }
  function chooseFloor(id: string) { if (busy.value) return; floorId.value = id; chooseLocation('') }
  async function loadDirectory() {
    if (directoryLoading.value) return; const generation = ++directoryGeneration, epoch = auth.identityEpoch; directoryLoading.value = true
    const result = options.catalog ? { kind: 'SUCCESS' as const, data: options.catalog } : await fetchCatalog()
    if (!alive || generation !== directoryGeneration || epoch !== auth.identityEpoch) return
    directoryLoading.value = false
    if (result.kind !== 'SUCCESS') { message.value = feedback(result); return }
    catalog.value = result.data
    buildingId.value = result.data.buildings[0]?.id ?? ''; floorId.value = floors.value[0]?.id ?? ''
    if (options.detail && auth.currentUser) {
      const s = options.detail.currentSubmission
      draft.value = store.supplement(auth.currentUser.id, options.detail.id, { locationId: s.location?.id ?? '', equipmentName: s.equipmentName, equipmentCategory: s.equipmentCategory ?? '', addressText: s.addressText, description: s.description, contactName: s.contactName, contactPhone: s.contactPhone, version: options.detail.version })
      locationId.value = draft.value.locationId; coordinates(locationId.value)
    } else chooseLocation(options.initialLocationId ?? '')
  }
  async function sendPending(d: RequestDraft, pending: PendingSubmission, epoch: number) {
    const result = await businessMutation(pending.path, pending.method, pending.payload, pending.resourceType, pending.resourceId)
    if (!alive || epoch !== auth.identityEpoch || d.pending?.operationId !== pending.operationId) return
    settleSubmission(d, result, pending.operationId)
    if (result.kind === 'SUCCESS') { store.clear(d.key); saved(result.data); return }
    if (draft.value?.key === d.key) message.value = feedback(result)
    if (result.kind === 'HTTP_ERROR' && ['ATTACHMENT_EXPIRED', 'ATTACHMENT_ALREADY_BOUND', 'ATTACHMENT_CONTEXT_MISMATCH'].includes(result.response.code)) resetExpiredSelection(d.images)
    if (result.kind === 'HTTP_ERROR' && result.response.code === 'CSRF_INVALID') await auth.ensureCsrf(true)
  }
  async function submit() {
    const d = draft.value; if (!d || busy.value || d.pending || !canSubmit.value) return
    const epoch = auth.identityEpoch, key = d.key, current = () => alive && epoch === auth.identityEpoch && draft.value?.key === key
    busy.value = '上传图片'; message.value = ''
    try {
      if (!await auth.ensureCsrf() || !current()) { if (current()) message.value = '请求校验信息读取失败，请重试'; return }
      if (!options.detail && !d.images.length) { message.value = '首次报修至少选择一张故障图片'; return }
      const uploaded = await ensureTemporaryUploaded(d.images, context.value, uploadTemporary, current)
      if (!current()) return
      if (uploaded.kind !== 'SUCCESS') { message.value = uploaded.message; return }
      const operationId = crypto.randomUUID(), body: Record<string, unknown> = { operationId, equipmentName: d.equipmentName, equipmentCategory: d.equipmentCategory || null, addressText: d.addressText, description: d.description, contactName: d.contactName, contactPhone: d.contactPhone, attachmentIds: uploaded.ids }
      if (!legacy.value) body.locationId = d.locationId
      if (options.detail) body.version = d.version
      const p: PendingSubmission = { operationId, operation: options.detail ? 'SUPPLEMENT_REQUEST' : 'CREATE_REQUEST', path: options.detail ? `/api/repair-requests/${options.detail.id}/supplement` : '/api/repair-requests', method: 'POST', payload: body, resourceType: 'REPAIR_REQUEST', phase: 'SENDING' }
      if (options.detail) p.resourceId = options.detail.id
      freezeSubmission(d, p); busy.value = '提交申请'; await sendPending(d, d.pending!, epoch)
    } finally { busy.value = '' }
  }
  async function checkPending() {
    const d = draft.value, p = d?.pending; if (!d || !p || busy.value) return
    const epoch = auth.identityEpoch; busy.value = '核对结果'
    try {
      const result = await fetchOperation(p.operationId, p.operation)
      if (!alive || epoch !== auth.identityEpoch || d.pending?.operationId !== p.operationId) return
      if (result.kind === 'SUCCESS' && applyConfirmedOperation(d, result.data, p.operationId)) { store.clear(d.key); if (result.data.kind === 'SUCCEEDED') saved(result.data.receipt) }
      else if (draft.value?.key === d.key) message.value = result.kind === 'SUCCESS' ? '尚未确认原提交结果，不能判断事务失败。保留原标识，可继续核对或用原标识重试。' : feedback(result)
    } finally { busy.value = '' }
  }
  async function retryPending() {
    const d = draft.value, p = d?.pending; if (!d || !p || busy.value) return
    const epoch = auth.identityEpoch; busy.value = '原标识重试'
    try { if (!await auth.ensureCsrf() || epoch !== auth.identityEpoch) return; p.phase = 'SENDING'; await sendPending(d, p, epoch) } finally { busy.value = '' }
  }
  function acceptCurrentVersion() { if (draft.value && options.detail?.allowedActions.includes('SUPPLEMENT_REQUEST') && !draft.value.pending) { draft.value.version = options.detail.version; message.value = '已核对当前版本，保留你的编辑内容。' } }
  async function discard() { const selected = draft.value, epoch = auth.identityEpoch; if (!selected || selected.pending || busy.value) return; if (await confirm('放弃这份未提交草稿和本次图片选择？历史申请和图片保留。') && alive && epoch === auth.identityEpoch && draft.value === selected && !selected.pending && !busy.value) { store.clear(selected.key); draft.value = null; void loadDirectory() } }
  watch(() => [options.initialLocationId, options.detail?.id], () => { if (catalog.value && !options.detail) chooseLocation(options.initialLocationId ?? '') }, { immediate: false })
  watch(() => auth.identityEpoch, () => { directoryGeneration++; draft.value = null; catalog.value = null; message.value = ''; directoryLoading.value = false }, { flush: 'sync' })
  onUnmounted(() => { alive = false; directoryGeneration++ })
  void loadDirectory()
  return { catalog, draft, buildingId, floorId, locationId, message, busy, directoryLoading, floors, locations, legacy, canSubmit, context, historicalAttachments, chooseLocation, chooseBuilding, chooseFloor, loadDirectory, submit, checkPending, retryPending, acceptCurrentVersion, discard }
}
