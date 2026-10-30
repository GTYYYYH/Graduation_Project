<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchEquipmentDetail, facilityNames } from '../../api/equipment'
import { orderStatusNames } from '../../api/workorder'
import { useBusinessRead } from '../../composables/useBusinessRead'
import { useBusinessAction } from '../../composables/useBusinessAction'
import { useBusinessConfirmation } from '../../composables/useBusinessConfirmation'
import InlineConfirmation from '../../components/common/InlineConfirmation.vue'
import { businessReturnTo, relatedBusinessTo } from '../../router/businessNavigation'
const route = useRoute(), router = useRouter(), { data, loading, message, stale, lastSuccessAt, clear, load } = useBusinessRead(() => fetchEquipmentDetail(String(route.params.id), Math.max(1, Number(route.query.page ?? 1) || 1)))
const action = useBusinessAction('EQUIPMENT_STATUS', () => String(route.params.id), load)
const confirmation = useBusinessConfirmation()
const returnTo = computed(() => businessReturnTo(route.query.returnTo, '/equipment'))
watch(() => route.fullPath, () => { clear(); void load() }, { immediate: true })
async function changeStatus() {
  const e = data.value?.equipment; if (!e || stale.value || loading.value || action.pending.value || action.busy.value) return
  if (!await confirmation.ask(e.enabled ? '停用后不能受理新工单，已有工单继续处理，历史保留。确认停用？' : '确认启用该设备，恢复新工单受理？') || data.value?.equipment !== e || stale.value || action.pending.value || action.busy.value) return
  const operationId = crypto.randomUUID(); await action.submit({ operationId, operation: e.enabled ? 'EQUIPMENT_DISABLE' : 'EQUIPMENT_ENABLE', path: `/api/equipment/${e.id}/${e.enabled ? 'disable' : 'enable'}`, method: 'POST', payload: { operationId, version: e.version }, resourceType: 'EQUIPMENT', resourceId: e.id, phase: 'SENDING' })
}
function page(value: number) { void router.replace({ path: route.path, query: { ...route.query, page: String(value) } }) }
</script>
<template><InlineConfirmation :message="confirmation.message.value" @answer="confirmation.answer" /><div class="page-heading"><h1>设备详情与工单历史</h1><div class="actions"><RouterLink :to="returnTo">返回上一页</RouterLink><button class="secondary" :disabled="loading" @click="load">刷新只读资料</button></div></div><p v-if="loading" role="status">正在读取设备…</p><p v-if="message" class="notice" role="alert">{{ message }} {{ stale ? `上次成功 ${lastSuccessAt}，资料可能过期。` : '' }}</p><p v-if="action.message.value" class="notice" role="status">{{ action.message.value }}</p><section v-if="data" class="card"><h2>{{ data.equipment.equipmentNo }} · {{ data.equipment.name }}</h2><p>{{ data.equipment.enabled ? '启用' : '停用' }} · {{ facilityNames[data.equipment.facilityType] }} · {{ data.equipment.addressText }}</p><div class="actions"><RouterLink v-if="!action.pending.value" class="secondary link-button" :to="`/equipment/${data.equipment.id}/edit`">维护资料（编号不可改）</RouterLink><button class="secondary" :disabled="stale || loading || action.busy.value || !!action.pending.value" @click="changeStatus">{{ data.equipment.enabled ? '停用设备' : '启用设备' }}</button></div><div v-if="action.pending.value" class="notice"><strong>启停用结果待确认</strong><div class="actions"><button class="secondary" :disabled="action.busy.value" @click="action.check">核对原操作结果</button><button class="secondary" :disabled="action.busy.value" @click="action.retry">使用原标识重试</button></div></div><h2>当前未完结工单</h2><p v-if="data.equipment.currentWorkOrder"><RouterLink :to="relatedBusinessTo(`/work-orders/${data.equipment.currentWorkOrder.id}`, route.fullPath, route.query.returnTo)">{{ data.equipment.currentWorkOrder.workOrderNo }} · {{ orderStatusNames[data.equipment.currentWorkOrder.status] }}</RouterLink> · 当前有工单，禁止设备移动。</p><p v-else class="muted">当前无未完结工单。</p><h2>历次已完成工单</h2><p v-if="!data.history.records.length" class="muted">暂无已完成工单。</p><ul class="business-list"><li v-for="order in data.history.records" :key="order.id"><RouterLink :to="relatedBusinessTo(`/work-orders/${order.id}`, route.fullPath, route.query.returnTo)">{{ order.workOrderNo }} · {{ orderStatusNames[order.status] }} · {{ order.locationNameSnapshot ?? '旧文字位置' }}</RouterLink></li></ul><div class="actions"><span>共{{ data.history.total }}条</span><button class="secondary" :disabled="loading || data.history.page <= 1" @click="page(data.history.page - 1)">上一页</button><span>第{{ data.history.page }}页</span><button class="secondary" :disabled="loading || data.history.page * data.history.pageSize >= data.history.total" @click="page(data.history.page + 1)">下一页</button></div></section></template>
