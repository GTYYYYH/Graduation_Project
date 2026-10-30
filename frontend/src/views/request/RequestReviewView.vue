<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { businessReturnTo } from '../../router/businessNavigation'
import { fetchRequest } from '../../api/repair-request'
import { fetchEquipment, fetchEquipmentDetail, facilityNames } from '../../api/equipment'
import type { Equipment } from '../../api/equipment'
import type { EligibleTechnician } from '../../api/workorder'
import type { BusinessReceipt } from '../../api/common'
import { useAuthStore } from '../../stores/auth'
import { useDraftStore } from '../../stores/drafts'
import { useBusinessRead } from '../../composables/useBusinessRead'
import { useBusinessAction } from '../../composables/useBusinessAction'
import { useBusinessConfirmation } from '../../composables/useBusinessConfirmation'
import { requestReviewCountRefresh } from '../../composables/useReviewNotifications'
import { prepareReviewOperation, reviewBlock } from './review-flow'
import type { ReviewAction, ReviewSelection } from './review-flow'
import AssignmentPanel from '../../components/workorder/AssignmentPanel.vue'
import RequestMaterialContent from '../../components/request/RequestMaterialContent.vue'
import InlineConfirmation from '../../components/common/InlineConfirmation.vue'

const route = useRoute(), auth = useAuthStore(), drafts = useDraftStore(), id = computed(() => String(route.params.id))
const selectedEquipment = shallowRef<Equipment | null>(null), selectedTechnician = shallowRef<EligibleTechnician | null>(null)
const equipmentId = shallowRef(''), technicianId = shallowRef(''), reason = shallowRef(''), qualifiedReady = shallowRef(false)
const equipmentPage = shallowRef(1), equipmentKeyword = shallowRef(''), appliedEquipmentKeyword = shallowRef('')
const localMessage = shallowRef(''), receipt = shallowRef<BusinessReceipt | null>(null)
function clearSelections() { selectedEquipment.value = null; selectedTechnician.value = null; equipmentId.value = ''; technicianId.value = ''; reason.value = ''; qualifiedReady.value = false; localMessage.value = ''; receipt.value = null }
const { data, loading, message, stale, lastSuccessAt, clear, load } = useBusinessRead(signal => fetchRequest(id.value, {signal}), () => { clearSelections(); drafts.action(auth.currentUser?.id ?? '', 'REQUEST_REVIEW', id.value).pending = null }, { target: () => `review/${id.value}`, poll: true })
const equipmentRead = useBusinessRead(() => fetchEquipment({ page: equipmentPage.value, pageSize: 20, keyword: appliedEquipmentKeyword.value, locationId: data.value?.location?.id }), () => { selectedEquipment.value = null; equipmentId.value = '' })
const action = useBusinessAction('REQUEST_REVIEW', () => id.value, async confirmed => { receipt.value = confirmed; reason.value = ''; requestReviewCountRefresh(); await load() })
const recoveryEquipmentId = computed(() => String(action.pending.value?.payload.equipmentId ?? (typeof route.query.createdEquipmentId === 'string' ? route.query.createdEquipmentId : '')))
const returnedEquipment = useBusinessRead(() => fetchEquipmentDetail(recoveryEquipmentId.value), undefined, { target: () => /^[1-9][0-9]{0,18}$/.test(recoveryEquipmentId.value) ? `review-equipment/${recoveryEquipmentId.value}` : null })
const equipmentOptions = computed(() => {
  const records = equipmentRead.data.value?.records ?? []
  return selectedEquipment.value && !records.some(e => e.id === selectedEquipment.value?.id) ? [selectedEquipment.value, ...records] : records
})
const confirmation = useBusinessConfirmation()
const locked = computed(() => action.busy.value || !!action.pending.value || !!confirmation.message.value)
const returnTo = computed(() => businessReturnTo(route.query.returnTo, '/admin/requests'))
const workOrderId = computed(() => data.value?.workOrder?.id ?? (receipt.value?.relatedResourceType === 'WORK_ORDER' ? receipt.value.relatedResourceId : null))
function selection(kind: ReviewAction): ReviewSelection {
  return { request: data.value, equipment: selectedEquipment.value, technician: qualifiedReady.value ? selectedTechnician.value : null, reason: reason.value,
    ready: auth.businessRouteGranted && !!auth.currentUser?.capabilities.canManage && !!data.value && !loading.value && !stale.value
      && (kind !== 'duplicate-close' || (!!equipmentRead.data.value && !equipmentRead.loading.value && !equipmentRead.stale.value)) }
}
const approveBlock = computed(() => reviewBlock('approve-and-assign', selection('approve-and-assign')))
const duplicateBlock = computed(() => reviewBlock('duplicate-close', selection('duplicate-close')))
const returnBlock = computed(() => reviewBlock('return', selection('return')))
function chooseEquipment(value: string) { if (locked.value) return; equipmentId.value = value; selectedEquipment.value = equipmentOptions.value.find(e => e.id === value) ?? null }
function chooseTechnician(person: EligibleTechnician | null) { selectedTechnician.value = person; technicianId.value = person?.id ?? '' }
async function searchEquipment(page = 1) { if (locked.value || equipmentRead.loading.value) return; equipmentPage.value = page; appliedEquipmentKeyword.value = equipmentKeyword.value; await equipmentRead.load() }
watch(equipmentRead.data, value => {
  if (!value) return
  const chosen = value.records.find(e => e.id === equipmentId.value)
  if (chosen) selectedEquipment.value = chosen
  else if (equipmentPage.value === 1 && !appliedEquipmentKeyword.value && value.total <= value.pageSize && equipmentId.value && equipmentId.value !== returnedEquipment.data.value?.equipment.id && equipmentId.value !== recoveryEquipmentId.value) { selectedEquipment.value = null; equipmentId.value = '' }
})
watch(() => `${data.value?.id}/${data.value?.location?.id}/${data.value?.status}`, () => { equipmentRead.clear(); equipmentPage.value = 1; if (data.value?.status === 'PENDING_REVIEW') void equipmentRead.load() })
function restorePendingSelection() {
  const pending = action.pending.value; if (!pending) return
  equipmentId.value = String(pending.payload.equipmentId ?? ''); technicianId.value = String(pending.payload.technicianId ?? '')
  reason.value = typeof pending.payload.reason === 'string' ? pending.payload.reason : ''
}
watch(() => action.pending.value, restorePendingSelection)
watch(() => `${id.value}/${auth.identityEpoch}`, () => { confirmation.answer(false); clearSelections(); restorePendingSelection(); equipmentRead.clear(); returnedEquipment.clear(); clear(); void load() }, { immediate: true })
watch(() => `${data.value?.id}/${data.value?.location?.id}/${recoveryEquipmentId.value}`, () => {
  returnedEquipment.clear()
  if (data.value && /^[1-9][0-9]{0,18}$/.test(recoveryEquipmentId.value)) void returnedEquipment.load()
})
watch(returnedEquipment.data, value => {
  if (!value || !data.value) return
  const e = value.equipment
  if (data.value.locationMode === 'STRUCTURED' && e.locationId !== data.value.location?.id) { localMessage.value = '返回的台账与申请位置不一致，请核对或退回补充。'; return }
  if (!equipmentId.value || equipmentId.value === e.id) { equipmentId.value = e.id; selectedEquipment.value = e }
})
async function submit(kind: ReviewAction) {
  if (locked.value) return
  const request = data.value, device = selectedEquipment.value, person = selectedTechnician.value, originalReason = reason.value
  const blocked = reviewBlock(kind, selection(kind)); if (blocked) { localMessage.value = blocked; return }
  const label = kind === 'approve-and-assign' ? `确认通过申请 ${request!.requestNo}，派给 ${person!.name}？工单将进入待接单。`
    : kind === 'return' ? `将原申请 ${request!.requestNo} 退回待补充，保留原文、图片及历史。原因：${originalReason}` : `按具体台账 ${device!.equipmentNo} 关闭重复申请 ${request!.requestNo}，保留原文、图片及历史。原因：${originalReason}`
  if (!await confirmation.ask(label)) return
  if (data.value !== request || (kind === 'duplicate-close' && selectedEquipment.value !== device) || (kind === 'approve-and-assign' && selectedTechnician.value !== person) || (kind !== 'approve-and-assign' && reason.value !== originalReason) || action.busy.value || action.pending.value) { localMessage.value = '资料或选择已变化，请重新核对并确认。'; return }
  const pending = prepareReviewOperation(kind, selection(kind), crypto.randomUUID())
  if (!pending) { localMessage.value = reviewBlock(kind, selection(kind)); return }
  localMessage.value = ''; await action.submit(pending)
}
</script>
<template>
  <InlineConfirmation :message="confirmation.message.value" @answer="confirmation.answer" />
  <div class="page-heading"><div><h1>报修申请审核与首次派单</h1><p class="muted">用户原文与管理员台账分别保存，审核不会改写用户的描述、联系信息或历史图片。</p></div><div class="actions"><RouterLink :to="returnTo">返回审核列表</RouterLink><button class="secondary" :disabled="loading || locked" @click="load">刷新只读资料</button></div></div>
  <p v-if="loading" role="status">正在读取申请…</p><p v-if="message" class="notice" role="alert">{{ message }} {{ stale ? `上次读取 ${lastSuccessAt}，资料可能过期。` : '' }}</p><p v-if="localMessage || action.message.value" class="notice" role="status">{{ localMessage || action.message.value }}</p>
  <div v-if="action.pending.value" class="notice"><strong>本次操作结果待确认</strong><p>原输入与标识保留。先核对原操作结果，或明确使用原标识重试。</p><p>原操作：{{ action.pending.value.operation }}；台账：{{ action.pending.value.payload.equipmentId || '不涉及' }}；人员：{{ action.pending.value.payload.technicianId || '不涉及' }}；原因：{{ action.pending.value.payload.reason || '不涉及' }}。</p><div class="actions"><button class="secondary" :disabled="action.busy.value" @click="action.check">核对原操作结果</button><button class="secondary" :disabled="action.busy.value" @click="action.retry">使用原标识重试</button></div></div>
  <div v-if="data" class="review-columns">
    <section class="card" aria-label="用户报修原文与历史"><RequestMaterialContent :detail="data" /></section>
    <section class="card" aria-label="管理员审核操作">
      <template v-if="data.status === 'PENDING_REVIEW'">
        <AssignmentPanel :request-id="id" :selected-id="technicianId" :disabled="locked" @selected="chooseTechnician" @ready="qualifiedReady = $event" /><p>已选维修人员：<strong>{{ selectedTechnician?.name || '尚未主动选择' }}</strong></p>
        <button class="primary full" :disabled="locked || !!approveBlock || !data.allowedActions.includes('APPROVE_AND_ASSIGN')" @click="submit('approve-and-assign')">审核通过并首次派单</button>
        <details class="other-decisions"><summary>重复关闭时匹配设备台账</summary>
        <p>申请位置：<strong>{{ data.locationNameSnapshot || '旧文字位置（无3D关联）' }}</strong></p>
        <form @submit.prevent="searchEquipment()"><label>搜索此位置的设备编号／名称<input v-model="equipmentKeyword" maxlength="100" :disabled="locked"></label><div class="actions"><button class="secondary" :disabled="locked || equipmentRead.loading.value">查询台账</button><button type="button" class="secondary" :disabled="locked || equipmentRead.loading.value" @click="equipmentRead.load">刷新台账</button></div></form>
        <p v-if="equipmentRead.loading.value" role="status">正在读取台账…</p><p v-if="equipmentRead.message.value" class="notice" role="alert">{{ equipmentRead.message.value }} {{ equipmentRead.stale.value ? '台账可能过期，请重新核对。' : '' }}</p>
        <p v-if="returnedEquipment.message.value" class="notice" role="alert">{{ returnedEquipment.message.value }} <button class="text-button" @click="returnedEquipment.load">重新读取返回台账</button></p><p v-if="equipmentRead.data.value">当前条件共{{ equipmentRead.data.value.total }}条台账。</p><label>匹配具体设备<select :value="equipmentId" :disabled="locked || equipmentRead.loading.value || equipmentRead.stale.value" @change="chooseEquipment(($event.target as HTMLSelectElement).value)"><option value="">请选择具体台账</option><option v-for="e in equipmentOptions" :key="e.id" :value="e.id">{{ e.equipmentNo }} · {{ e.name }} · {{ e.enabled ? '启用' : '停用' }}{{ e.currentWorkOrder ? ' · 有未完结工单' : '' }}</option></select></label>
        <div v-if="equipmentRead.data.value" class="actions"><button class="secondary" :disabled="locked || equipmentRead.loading.value || equipmentPage <= 1" @click="searchEquipment(equipmentPage - 1)">上一页设备</button><span>第{{ equipmentPage }}页</span><button class="secondary" :disabled="locked || equipmentRead.loading.value || equipmentPage * equipmentRead.data.value.pageSize >= equipmentRead.data.value.total" @click="searchEquipment(equipmentPage + 1)">下一页设备</button></div>
        <div v-if="selectedEquipment" class="matched-equipment"><h3>已选管理员台账</h3><dl><dt>编号及名称</dt><dd>{{ selectedEquipment.equipmentNo }} · {{ selectedEquipment.name }}</dd><dt>台账地址原文</dt><dd>{{ selectedEquipment.addressText }}</dd><dt>设施类型</dt><dd>{{ facilityNames[selectedEquipment.facilityType] }}</dd><dt>台账状态</dt><dd>{{ selectedEquipment.enabled ? '启用' : '停用' }}{{ selectedEquipment.currentWorkOrder ? '，已有未完结工单' : '' }}</dd></dl></div>
        <RouterLink v-if="!locked" class="secondary link-button" :to="{ path: '/equipment/new', query: { requestId: id, locationId: data.location?.id, returnTo } }">补录范围内设备后返回审核</RouterLink><p class="muted">范围外设施应退回纠正或由原用户撤回。补录仍遵守该空间允许的设施类型。</p>
        </details>
        <section class="other-decisions"><h3>退回补充或重复关闭</h3><label>原因（必填，最多2000字）<textarea v-model="reason" :disabled="locked" maxlength="2000" rows="4"></textarea></label><div class="actions"><button class="secondary" :disabled="locked || !!returnBlock || !data.allowedActions.includes('RETURN_REQUEST')" @click="submit('return')">退回待补充</button><button class="secondary" :disabled="locked || !!duplicateBlock || !data.allowedActions.includes('CLOSE_DUPLICATE')" @click="submit('duplicate-close')">按所选台账重复关闭</button></div><p class="muted">重复关闭必须匹配具体合法台账并填写实际原因，不能用于范围外申请。</p></section>
      </template>
      <template v-else><h2>当前申请已处理</h2><p>此状态的操作以真实申请及工单规则为准，原文和历史保留。</p></template>
      <RouterLink v-if="workOrderId" class="primary link-button" :to="{ path: `/work-orders/${workOrderId}`, query: { returnTo } }">查看关联工单</RouterLink>
    </section>
  </div>
</template>
<style scoped>.matched-equipment,.other-decisions { border-top: 1px solid #dce6ea; padding-top: 18px; margin-top: 22px; }.matched-equipment dd { overflow-wrap: anywhere; }</style>
