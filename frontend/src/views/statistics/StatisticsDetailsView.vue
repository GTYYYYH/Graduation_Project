<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchStatisticsDetails, formatSeconds, shanghaiMonth, statisticTime, statisticsMetricNames } from '../../api/statistics'
import type { StatisticsDetailQuery, StatisticsMetric } from '../../api/statistics'
import { orderStatusNames } from '../../api/workorder'
import type { OrderStatus } from '../../api/workorder'
import { useCampusRefresh } from '../../composables/useCampusRefresh'
import { useAuthStore } from '../../stores/auth'
const route = useRoute(), router = useRouter(), auth = useAuthStore()
const text = (v: unknown) => typeof v === 'string' ? v : undefined
const month = computed(() => text(route.query.month) ?? shanghaiMonth())
const tab = computed(() => route.query.tab === 'analysis' ? 'analysis' : 'overview')
const metric = computed(() => (text(route.query.metric) ?? 'COMPLETED') as StatisticsMetric)
const query = computed<StatisticsDetailQuery>(() => ({ metric: metric.value, month: month.value, date: text(route.query.date), equipmentId: text(route.query.equipmentId), unlinkedEquipment: route.query.unlinkedEquipment === 'true' ? true : undefined, status: text(route.query.status) as OrderStatus | undefined, keyword: text(route.query.keyword), page: Number(route.query.page ?? 1), pageSize: Number(route.query.pageSize ?? 20) }))
const { data, loading, message, stale, load } = useCampusRefresh(() => auth.currentUser?.role === 'ADMIN' ? `statistics-details/${route.fullPath}` : null, signal => fetchStatisticsDetails(query.value, { signal }), undefined, { poll: false })
const keyword = shallowRef(''), status = shallowRef('')
watch(() => route.fullPath, () => { keyword.value = text(route.query.keyword) ?? ''; status.value = text(route.query.status) ?? '' }, { immediate: true })
const dashboard = computed(() => ({ path: '/admin/statistics', query: { month: month.value, tab: tab.value } }))
const scopeLabel = computed(() => !data.value ? '' : data.value.scope === 'ALL_CURRENT_STATE' ? '全部已生成工单·当前状态（不按月份过滤）' : `${data.value.month} · ${data.value.scope === 'MONTH_CREATED' ? '按工单生成时间归月' : '按最终验收通过时间归月'}`)
function search() { void router.push({ path: route.path, query: { ...route.query, keyword: keyword.value || undefined, status: metric.value === 'CURRENT_STATE' ? status.value || undefined : undefined, page: '1' } }) }
function page(value: number) { void router.push({ path: route.path, query: { ...route.query, page: String(value) } }) }
</script>

<template>
  <div class="page-heading"><div><h1>{{ statisticsMetricNames[metric] ?? '统计' }}明细</h1><p class="muted">每行对应一张真实工单。</p></div><div class="actions"><RouterLink :to="dashboard">返回 {{ month }} {{ tab === 'analysis' ? '分析' : '概览' }}</RouterLink><button class="secondary" :disabled="loading" @click="load">刷新明细</button></div></div>
  <form class="statistics-filters card" @submit.prevent="search"><label>搜索工单 / 设备名称或编号 <input v-model="keyword" maxlength="200" placeholder="输入关键词后查询"></label><label v-if="metric === 'CURRENT_STATE'">当前状态 <select v-model="status"><option value="">全部状态</option><option v-for="(name, key) in orderStatusNames" :key="key" :value="key">{{ name }}</option></select></label><button class="primary" type="submit">查询</button></form>
  <p v-if="loading" role="status">{{ data ? '正在刷新，保留上次查询结果。' : '正在读取明细，数量尚未确定…' }}</p>
  <p v-if="message" class="notice" role="alert">{{ message }}{{ stale && data ? `；保留 ${scopeLabel} 的旧结果，已过期。` : '；当前没有可展示的明细。' }}</p>
  <template v-if="data"><section class="card scope-note"><h2>{{ scopeLabel }}</h2><p v-if="query.date">验收日期：{{ query.date }}</p><p v-if="query.unlinkedEquipment">分组：未关联台账。下列原报修名称不代表同一台设备。</p><p>共 {{ data.records.total }} 张 · 查询时间：{{ statisticTime(data.queriedAt) }}。本次明细是新的读取快照，业务变化后可刷新看板核对。</p></section><p v-if="!data.records.records.length" class="card">当前范围暂无工单。</p>
    <div class="statistic-records"><article v-for="item in data.records.records" :key="item.id" class="card statistic-record"><div class="section-heading"><h2><RouterLink :to="{ path: `/work-orders/${item.id}`, query: { returnTo: route.fullPath } }">{{ item.workOrderNo }}</RouterLink></h2><span class="tag">{{ orderStatusNames[item.status] }}</span></div><p>{{ item.equipment ? `${item.equipment.name} · ${item.equipment.equipmentNo}` : `未关联台账 · 原报修名称：${item.reportedEquipmentName}` }}</p><p v-if="item.hasInvalidTime" class="notice" role="alert">时间记录异常：缺失或不符合时序的时长显示“—”，工单仍保留在样本中。</p><dl class="record-times"><dt>申请首次提交</dt><dd>{{ statisticTime(item.firstSubmittedAt) }}</dd><dt>工单生成</dt><dd>{{ statisticTime(item.createdAt) }}</dd><dt>实际接单</dt><dd>{{ statisticTime(item.acceptedAt) }}</dd><dt>最终验收通过</dt><dd>{{ statisticTime(item.completedAt) }}</dd><dt>响应时长</dt><dd>{{ formatSeconds(item.responseSeconds) }}</dd><dt>累计维修时长</dt><dd>{{ formatSeconds(item.repairSeconds) }}</dd></dl><p class="muted">未接单或尚未提交的轮次不伪造时长；累计维修扣除待验收。打开工单可查看各轮过程及凭证。</p></article></div>
    <nav class="pagination" aria-label="统计明细分页"><button class="secondary" :disabled="data.records.page <= 1 || loading" @click="page(data.records.page - 1)">上一页</button><span>第 {{ data.records.page }} 页 · 共 {{ data.records.total }} 张</span><button class="secondary" :disabled="data.records.page * data.records.pageSize >= data.records.total || loading" @click="page(data.records.page + 1)">下一页</button></nav>
  </template>
</template>

<style scoped>
.statistics-filters{display:flex;align-items:flex-end;gap:16px;flex-wrap:wrap;margin:20px 0}.statistics-filters label{display:flex;flex-direction:column;gap:8px;max-width:100%}.statistics-filters input{width:280px;max-width:100%}.scope-note{margin-bottom:16px}.statistic-records{display:grid;gap:16px}.section-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}.section-heading h2{overflow-wrap:anywhere}.record-times{display:grid;grid-template-columns:140px minmax(0,1fr);gap:10px 16px}.record-times dt{color:#607086}.record-times dd{margin:0;overflow-wrap:anywhere}.pagination{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:24px 0;flex-wrap:wrap}@media(max-width:480px){.statistics-filters{align-items:stretch;flex-direction:column}.statistics-filters input{width:100%;box-sizing:border-box}.record-times{grid-template-columns:1fr;gap:5px}.record-times dd{margin-bottom:10px}.actions{flex-wrap:wrap}}
</style>
