<script setup lang="ts">
import { computed, shallowRef, watch, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useBusinessRead } from '../../composables/useBusinessRead'
import { fetchRequest } from '../../api/repair-request'
import { useAuthStore } from '../../stores/auth'
import { useDraftStore } from '../../stores/drafts'
import { freezeSubmission, settleSubmission, applyConfirmedOperation } from '../../composables/useMutationConfirmation'
import { businessMutation, fetchOperation, feedback } from '../../api/business'
import RequestMaterialContent from '../../components/request/RequestMaterialContent.vue'
import { useBusinessConfirmation } from '../../composables/useBusinessConfirmation'
import InlineConfirmation from '../../components/common/InlineConfirmation.vue'
import { businessReturnTo, relatedBusinessTo } from '../../router/businessNavigation'
const route = useRoute(), auth = useAuthStore(), store = useDraftStore(), busy = shallowRef(false), actionMessage = shallowRef('')
const confirmation = useBusinessConfirmation()
let alive = true
const currentAction = computed(() => store.action(auth.currentUser?.id ?? '', 'WITHDRAW_REQUEST', String(route.params.id)))
const { data, loading, message, stale, lastSuccessAt, clear, load } = useBusinessRead(signal => fetchRequest(String(route.params.id), {signal}), () => { store.clear(`SUPPLEMENT_REQUEST/${auth.currentUser?.id}/${route.params.id}`); currentAction.value.pending = null }, { target: () => `request/${route.params.id}`, poll: true })
const returnEntry = computed(() => auth.currentUser?.role === 'TECHNICIAN' ? { path: '/tasks', label: '返回任务列表' }
  : auth.currentUser?.role === 'ADMIN' ? { path: '/admin/requests', label: '返回审核列表' } : { path: '/requests', label: '返回申请列表' })
const returnTo = computed(() => businessReturnTo(route.query.returnTo, returnEntry.value.path))
watch(() => route.params.id, () => { clear(); actionMessage.value = ''; void load() }, { immediate: true })
async function send() {
  const action = currentAction.value, pending = action.pending, epoch = auth.identityEpoch
  if (!pending) return
  const result = await businessMutation(pending.path, 'POST', pending.payload, 'REPAIR_REQUEST', pending.resourceId)
  if (!alive || epoch !== auth.identityEpoch || action.pending?.operationId !== pending.operationId) return
  settleSubmission(action, result, pending.operationId); actionMessage.value = result.kind === 'SUCCESS' ? '原申请已撤回，历史和图片保留。' : feedback(result)
  if (result.kind === 'SUCCESS') { store.clear(`SUPPLEMENT_REQUEST/${auth.currentUser?.id}/${route.params.id}`); await load() }
  if (result.kind === 'HTTP_ERROR' && result.response.code === 'CSRF_INVALID') await auth.ensureCsrf(true)
}
async function withdraw() {
  const record = data.value
  if (!record?.allowedActions.includes('WITHDRAW_REQUEST') || busy.value || currentAction.value.pending || !await confirmation.ask('撤回这份申请？将停止审核，申请、历史和图片继续保留。') || data.value !== record || currentAction.value.pending) return
  const epoch = auth.identityEpoch; busy.value = true
  try {
    if (!await auth.ensureCsrf() || epoch !== auth.identityEpoch) return
    const operationId = crypto.randomUUID(), id = data.value.id
    freezeSubmission(currentAction.value, { operationId, operation: 'WITHDRAW_REQUEST', path: `/api/repair-requests/${id}/withdraw`, method: 'POST', payload: { operationId, version: data.value.version }, resourceType: 'REPAIR_REQUEST', resourceId: id, phase: 'SENDING' }); await send()
  } finally { busy.value = false }
}
async function check() {
  const action = currentAction.value, pending = action.pending, epoch = auth.identityEpoch; if (!pending || busy.value) return; busy.value = true
  try { const result = await fetchOperation(pending.operationId, pending.operation); if (!alive || epoch !== auth.identityEpoch) return; if (result.kind === 'SUCCESS' && applyConfirmedOperation(action, result.data, pending.operationId)) { actionMessage.value = '已确认原撤回操作成功。'; await load() } else actionMessage.value = result.kind === 'SUCCESS' ? '原结果尚未确认，不等于失败；保留原标识继续核对。' : feedback(result) }
  finally { busy.value = false }
}
async function retry() { if (busy.value || !currentAction.value.pending) return; const epoch = auth.identityEpoch; busy.value = true; try { if (!await auth.ensureCsrf() || epoch !== auth.identityEpoch) return; await send() } finally { busy.value = false } }
onUnmounted(() => { alive = false })
</script>
<template><InlineConfirmation :message="confirmation.message.value" @answer="confirmation.answer" /><div class="page-heading"><h1>申请详情</h1><div class="actions"><RouterLink :to="returnTo">返回上一页</RouterLink><button class="secondary" :disabled="loading" @click="load">刷新只读资料</button></div></div><p v-if="loading" role="status">正在读取申请…</p><p v-if="message" class="notice" role="alert">{{ message }} {{ stale ? `上次成功 ${lastSuccessAt}，资料可能过期。` : '' }} <button class="text-button" @click="load">重试</button></p><p v-if="actionMessage" class="notice" role="status">{{ actionMessage }}</p><section v-if="data" class="card"><div class="actions"><RouterLink v-if="data.allowedActions.includes('SUPPLEMENT_REQUEST')" class="primary link-button" :to="{path:`/requests/${data.id}/supplement`,query:{returnTo}}">对照意见补充原申请</RouterLink><button v-if="data.allowedActions.includes('WITHDRAW_REQUEST')" type="button" class="secondary" :disabled="busy || !!currentAction.pending" @click="withdraw">撤回申请</button><RouterLink v-if="data.workOrder" :to="relatedBusinessTo(`/work-orders/${data.workOrder.id}`, route.fullPath, route.query.returnTo)">查看关联工单 {{ data.workOrder.workOrderNo }}</RouterLink></div><div v-if="currentAction.pending" class="notice"><strong>撤回结果待确认</strong><p>原标识与版本保留，尚未确认不等于失败。</p><div class="actions"><button class="secondary" :disabled="busy" @click="check">核对原操作结果</button><button class="secondary" :disabled="busy" @click="retry">使用原标识重试</button></div></div><RequestMaterialContent :detail="data" /></section></template>
