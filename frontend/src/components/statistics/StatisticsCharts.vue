<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, useTemplateRef, watch } from 'vue'
import { init, use } from 'echarts/core'
import type { EChartsType } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { AriaComponent, GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { formatSeconds } from '../../api/statistics'
import type { StatisticsDrill, StatisticsTab, StatisticsVo } from '../../api/statistics'
import { orderStatusNames } from '../../api/workorder'
import type { OrderStatus } from '../../api/workorder'
use([BarChart, AriaComponent, GridComponent, TooltipComponent, CanvasRenderer])
const props = defineProps<{ data: StatisticsVo; tab: StatisticsTab }>()
const emit = defineEmits<{ drill: [query: StatisticsDrill] }>()
const chartHost = useTemplateRef<HTMLDivElement>('chartHost'), frequencyHost = useTemplateRef<HTMLDivElement>('frequencyHost')
const states = computed(() => (Object.entries(orderStatusNames) as [OrderStatus, string][]).map(([status, name]) => ({ status, name, count: props.data.stateCounts[status] })))
const metrics = computed(() => [{ metric: 'COMPLETED' as const, label: '月度维修数量', value: `${props.data.completedCount} 张` }, { metric: 'RESPONSE_DURATION' as const, label: '平均响应时长', value: formatSeconds(props.data.averageResponseSeconds) }, { metric: 'REPAIR_DURATION' as const, label: '平均维修时长', value: formatSeconds(props.data.averageRepairSeconds) }])
let charts: EChartsType[] = [], observer: ResizeObserver | null = null
function dispose() { observer?.disconnect(); observer = null; charts.forEach(c => c.dispose()); charts = [] }
function resize() { charts.forEach(c => c.resize()) }
function equipmentDrill(index: number) { const item = props.data.equipmentFrequencies[index]; if (item) emit('drill', item.equipment ? { metric: 'EQUIPMENT_FREQUENCY', equipmentId: item.equipment.id } : { metric: 'EQUIPMENT_FREQUENCY', unlinkedEquipment: true }) }
function draw() {
  dispose()
  if (typeof HTMLElement === 'undefined' || !(chartHost.value instanceof HTMLElement)) return
  const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
  const chart = init(chartHost.value); charts.push(chart)
  const overview = props.tab === 'overview'
  chart.setOption({ animation: !reduced, aria: { enabled: true }, tooltip: { trigger: 'axis', renderMode: 'richText' }, grid: { left: 16, right: 24, top: 16, bottom: 24, containLabel: true },
    xAxis: { type: overview ? 'value' : 'category', minInterval: overview ? 1 : undefined, data: overview ? undefined : props.data.dailyCompleted.map(d => d.date.slice(5)) },
    yAxis: { type: overview ? 'category' : 'value', minInterval: 1, data: overview ? states.value.map(s => s.name) : undefined },
    series: [{ type: 'bar', data: overview ? states.value.map(s => s.count) : props.data.dailyCompleted.map(d => d.count), itemStyle: { color: '#4479bb' }, label: { show: true, position: overview ? 'right' : 'top' } }] })
  chart.on('click', (event: unknown) => { const index = (event as { dataIndex?: number }).dataIndex; if (typeof index !== 'number') return; if (props.tab === 'overview') { const row = states.value[index]; if (row) emit('drill', { metric: 'CURRENT_STATE', status: row.status }) } else { const row = props.data.dailyCompleted[index]; if (row) emit('drill', { metric: 'DAILY_COMPLETED', date: row.date }) } })
  if (!overview && frequencyHost.value instanceof HTMLElement && props.data.equipmentFrequencies.length) {
    const frequency = init(frequencyHost.value); charts.push(frequency)
    frequency.setOption({ animation: !reduced, aria: { enabled: true }, tooltip: { trigger: 'axis', renderMode: 'richText' }, grid: { left: 16, right: 24, top: 16, bottom: 24, containLabel: true }, xAxis: { type: 'value', minInterval: 1 }, yAxis: { type: 'category', inverse: true, data: props.data.equipmentFrequencies.map(e => e.equipment ? `${e.equipment.name} · ${e.equipment.equipmentNo}` : '未关联台账'), axisLabel: { width: 140, overflow: 'truncate' } }, series: [{ type: 'bar', data: props.data.equipmentFrequencies.map(e => e.count), itemStyle: { color: '#548e82' }, label: { show: true, position: 'right' } }] })
    frequency.on('click', (event: unknown) => { const index = (event as { dataIndex?: number }).dataIndex; if (typeof index === 'number') equipmentDrill(index) })
  }
  if (typeof ResizeObserver !== 'undefined') { observer = new ResizeObserver(resize); observer.observe(chartHost.value); if (frequencyHost.value) observer.observe(frequencyHost.value) }
}
watch(() => [props.data, props.tab], async () => { await nextTick(); draw() }, { flush: 'post' })
onMounted(() => { draw(); if (typeof window !== 'undefined') window.addEventListener('resize', resize) })
onBeforeUnmount(() => { dispose(); if (typeof window !== 'undefined') window.removeEventListener('resize', resize) })
</script>

<template>
  <section class="sample-note"><p>{{ data.month }} 最终验收通过：{{ data.sampleCount }} 张完成样本。响应从申请首次提交起算，维修累计各轮处理时段，扣除待验收时间。</p><p v-if="!data.sampleCount">暂无完成样本；月度维修数量为 0，平均时长为“—”。</p><p v-if="data.invalidTimeSampleCount" class="notice" role="alert">{{ data.invalidTimeSampleCount }} 张完成样本存在时间异常，受影响均时显示“—”；样本未被剔除，请打开明细核对。</p></section>
  <template v-if="tab === 'overview'">
    <div class="metric-grid"><button v-for="item in metrics" :key="item.metric" class="metric-card" @click="emit('drill', { metric: item.metric })"><span>{{ item.label }}</span><strong>{{ item.value }}</strong><span class="muted">查看对应完成样本 →</span></button></div>
    <section class="card chart-section"><div class="section-heading"><h2>全部工单·当前状态</h2><button class="text-button" @click="emit('drill', { metric: 'CURRENT_STATE' })">全部状态明细</button></div><p class="muted">所有已生成工单的当前状态，范围不随月份改变，包含跨月积压。</p><div ref="chartHost" class="chart" role="img" aria-label="全部工单当前状态图，文字数值和明细入口见下方"/><ul class="state-readings"><li v-for="item in states" :key="item.status"><button class="reading-button" @click="emit('drill', { metric: 'CURRENT_STATE', status: item.status })">{{ item.name }} <strong>{{ item.count }} 张</strong><span>明细 →</span></button></li></ul></section>
  </template>
  <template v-else>
    <section class="card chart-section"><div class="section-heading"><h2>{{ data.month }} 每日维修完成分布</h2><button class="text-button" @click="emit('drill', { metric: 'COMPLETED' })">完成样本明细（{{ data.sampleCount }}）</button></div><p class="muted">按上海最终验收通过日统计，与月度维修数量使用同一批工单。</p><div ref="chartHost" class="chart" role="img" aria-label="所选月份每日完成图，展开下方每日文字数值可查看明细"/><details><summary>每日数值与明细入口</summary><ul class="daily-readings"><li v-for="item in data.dailyCompleted" :key="item.date"><button class="reading-button" @click="emit('drill', { metric: 'DAILY_COMPLETED', date: item.date })">{{ item.date }} <strong>{{ item.count }} 张</strong><span>明细 →</span></button></li></ul></details></section>
    <section class="card chart-section"><h2>{{ data.month }} 设备故障频次</h2><p class="muted">按工单生成时间归月，每张工单计一次，包含未完成工单。同名台账按 ID 区分；未关联台账单独分组。</p><p v-if="!data.equipmentFrequencies.length">暂无记录：本月没有生成工单。</p><template v-else><div ref="frequencyHost" class="chart" role="img" aria-label="设备频次图，完整名称、编号、数值和明细入口见下方"/><ul class="frequency-readings"><li v-for="(item, index) in data.equipmentFrequencies" :key="item.equipment?.id ?? 'unlinked'"><button class="reading-button" @click="equipmentDrill(index)"><span>{{ item.equipment ? `${item.equipment.name} · ${item.equipment.equipmentNo}` : '未关联台账' }}</span><strong>{{ item.count }} 张</strong><span>明细 →</span></button></li></ul></template></section>
  </template>
</template>

<style scoped>
.metric-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin:20px 0}.metric-card{display:flex;flex-direction:column;align-items:flex-start;gap:14px;padding:24px;border:1px solid #dce3ec;background:white;border-radius:12px;color:#27374e;text-align:left;cursor:pointer}.metric-card strong{font-size:1.75rem;overflow-wrap:anywhere}.chart-section{margin-top:24px}.chart{height:280px;width:100%}.section-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}.sample-note{color:#526176}.state-readings,.daily-readings,.frequency-readings{list-style:none;padding:0;margin:16px 0;display:grid;gap:8px}.state-readings{grid-template-columns:repeat(auto-fit,minmax(170px,1fr))}.daily-readings{grid-template-columns:repeat(auto-fit,minmax(240px,1fr))}.reading-button{display:flex;align-items:center;gap:12px;justify-content:space-between;min-height:44px;width:100%;padding:12px;border:1px solid #dde5ef;background:#f8fafc;border-radius:8px;text-align:left;color:#30435f}.reading-button span:first-child{overflow-wrap:anywhere;min-width:0}.reading-button strong{white-space:nowrap}.reading-button span:last-child{white-space:nowrap;color:#215b9e}.metric-card:hover,.reading-button:hover{border-color:#7198c7}summary{cursor:pointer;min-height:44px;padding:12px 0}@media(max-width:720px){.metric-grid{grid-template-columns:1fr}.metric-card{padding:18px}.chart{height:240px}.reading-button{gap:8px}.frequency-readings .reading-button{flex-wrap:wrap}}
</style>
