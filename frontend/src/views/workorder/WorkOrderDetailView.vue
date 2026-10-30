<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { fetchOrder, orderStatusNames } from '../../api/workorder'
import { useBusinessRead } from '../../composables/useBusinessRead'
import { useAuthStore } from '../../stores/auth'
import { useDraftStore } from '../../stores/drafts'
import { businessReturnTo, relatedBusinessTo } from '../../router/businessNavigation'
import WorkOrderHistoryContent from '../../components/workorder/WorkOrderHistoryContent.vue'
import TechnicianWorkPanel from '../../components/workorder/TechnicianWorkPanel.vue'
import AcceptancePanel from '../../components/workorder/AcceptancePanel.vue'
import ReassignmentPanel from '../../components/workorder/ReassignmentPanel.vue'
const firstTime = (value: string | null | undefined) => value ? new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) : '—'
const route = useRoute(), auth = useAuthStore(), drafts = useDraftStore()
const { data, loading, message, stale, lastSuccessAt, clear, load } = useBusinessRead(signal => fetchOrder(String(route.params.id), {signal}), () => {
  if (auth.currentUser) drafts.clearOrderEditors(auth.currentUser.id, String(route.params.id))
}, { target: () => `order/${route.params.id}`, poll: true })
watch(data, record => { if (record?.historyScope === 'ASSIGNMENT_ONLY' && auth.currentUser) drafts.clearOrderEditors(auth.currentUser.id, record.id) })
const returnTo = computed(() => businessReturnTo(route.query.returnTo, '/work-orders'))
watch(() => route.params.id, () => { clear(); void load() }, { immediate: true })
</script>
<template><div class="page-heading"><h1>工单详情</h1><div class="actions"><RouterLink :to="returnTo">返回上一页</RouterLink><button class="secondary" :disabled="loading" @click="load">刷新只读资料</button></div></div><p v-if="loading" role="status">正在读取工单…</p><p v-if="message" class="notice" role="alert">{{ message }} {{ stale ? `上次成功 ${lastSuccessAt}，资料可能过期。` : '' }} <button class="text-button" @click="load">重试</button></p><section v-if="data" class="card"><h2>{{ data.workOrderNo }}</h2><p class="tag">{{ orderStatusNames[data.status] }}</p><dl class="detail-grid"><dt>申请首次提交</dt><dd>{{ firstTime(data.firstSubmittedAt) }}</dd><template v-if="data.historyScope === 'FULL'"><dt>工单生成</dt><dd>{{ firstTime(data.createdAt) }}</dd><dt>实际接单</dt><dd>{{ firstTime(data.acceptedAt) }}</dd><dt>最终验收通过</dt><dd>{{ firstTime(data.completedAt) }}</dd></template><dt>固化位置</dt><dd>{{ data.locationNameSnapshot ?? '旧文字工单 · 未关联3D位置' }}</dd><dt>匹配台账</dt><dd><template v-if="data.equipment">{{ data.equipment.equipmentNo }} · {{ data.equipment.name }} <RouterLink v-if="auth.currentUser?.capabilities.canManage" :to="relatedBusinessTo(`/equipment/${data.equipment.id}`, route.fullPath, route.query.returnTo)">查看台账</RouterLink></template><span v-else>未关联台账</span></dd><dt>当前指派</dt><dd>{{ data.historyScope === 'ASSIGNMENT_ONLY' ? '本人历史范围' : data.assignedTechnician?.name ?? '暂无' }}</dd></dl><RouterLink v-if="auth.currentUser?.role !== 'TECHNICIAN'" :to="relatedBusinessTo(`/requests/${data.requestId}`, route.fullPath, route.query.returnTo)">查看原申请 {{ data.requestNo }}</RouterLink><WorkOrderHistoryContent :detail="data" /></section><TechnicianWorkPanel v-if="data && auth.currentUser?.role === 'TECHNICIAN'" :detail="data" :refresh="load" /><AcceptancePanel v-if="data && auth.currentUser?.role === 'REPORTER'" :detail="data" :refresh="load" /><ReassignmentPanel v-if="data && auth.currentUser?.role === 'ADMIN'" :detail="data" :refresh="load" /></template>

