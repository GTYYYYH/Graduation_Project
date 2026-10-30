<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { shanghaiMonth, statisticTime } from '../../api/statistics'
import type { StatisticsDrill, StatisticsTab } from '../../api/statistics'
import { useStatistics } from '../../composables/useStatistics'
import { useAuthStore } from '../../stores/auth'
import StatisticsCharts from '../../components/statistics/StatisticsCharts.vue'
const route = useRoute(), router = useRouter(), auth = useAuthStore()
const month = computed(() => typeof route.query.month === 'string' ? route.query.month : shanghaiMonth())
const tab = computed<StatisticsTab>(() => route.query.tab === 'analysis' ? 'analysis' : 'overview')
const { data, loading, message, stale, load } = useStatistics(month)
function selectMonth(event: Event) { void router.push({ path: '/admin/statistics', query: { month: (event.target as HTMLInputElement).value, tab: tab.value } }) }
function selectTab(value: StatisticsTab) { void router.push({ path: '/admin/statistics', query: { month: month.value, tab: value } }) }
function drill(query: StatisticsDrill) { void router.push({ path: '/admin/statistics/details', query: { month: data.value!.month, tab: tab.value, ...query, unlinkedEquipment: query.unlinkedEquipment ? 'true' : undefined, page: '1' } }) }
</script>

<template>
  <div class="page-heading"><div><h1>统计看板</h1><p class="muted">真实工单统计 · 上海自然月</p></div><button class="secondary" :disabled="loading" @click="load">刷新数据</button></div>
  <p v-if="auth.currentUser?.role !== 'ADMIN'" class="notice" role="alert">当前身份没有统计权限。</p>
  <template v-else>
    <div class="statistics-controls"><label>统计月份 <input type="month" :value="month" min="1000-01" max="9998-12" @change="selectMonth"></label><div class="statistics-tabs" role="tablist" aria-label="看板视图"><button role="tab" :aria-selected="tab === 'overview'" :class="{ active: tab === 'overview' }" @click="selectTab('overview')">概览</button><button role="tab" :aria-selected="tab === 'analysis'" :class="{ active: tab === 'analysis' }" @click="selectTab('analysis')">分析</button></div></div>
    <p v-if="loading" role="status">{{ data ? `正在刷新 ${data.month}，保留上次成功数据。` : `正在读取 ${month}，统计尚未确定…` }}</p>
    <p v-if="message" class="notice" role="alert">{{ message }}{{ data ? `；显示 ${data.month} 上次成功数据，已过期，请重试。` : '；当前没有可展示的统计结果。' }}</p>
    <template v-if="data"><p class="muted" :class="{ 'stale-note': stale }">当前展示月份：<strong>{{ data.month }}</strong> · 查询时间：{{ statisticTime(data.queriedAt) }}</p><StatisticsCharts :data="data" :tab="tab" @drill="drill" /></template>
  </template>
</template>

<style scoped>
.statistics-controls{display:flex;align-items:center;gap:24px;flex-wrap:wrap;margin:20px 0}.statistics-controls label{display:flex;align-items:center;gap:12px}.statistics-tabs{display:flex;gap:8px}.statistics-tabs button{padding:10px 24px;border:1px solid #d8e0eb;background:white;border-radius:8px;color:#334155;min-height:44px}.statistics-tabs .active{background:#eaf2ff;border-color:#487cc8;color:#174a92}.stale-note{color:#985500}@media(max-width:480px){.statistics-controls{align-items:stretch;flex-direction:column}.statistics-tabs button{flex:1}.page-heading{align-items:flex-start;gap:12px}}
</style>
