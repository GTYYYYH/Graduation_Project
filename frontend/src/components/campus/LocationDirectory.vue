<script setup lang="ts">
import { computed } from 'vue'
import type { CatalogVo } from '../../api/account'
import type { LocationSummary } from '../../api/campus'
import { categoryNames, stageNames } from '../../api/campus'
import '../../campus/location-status.css'

const props = defineProps<{ title: string; sampleFloor: boolean; locations: CatalogVo['locations']; summaries: LocationSummary[]; selectedId: string }>()
const emit = defineEmits<{ select: [id: string]; open: [id: string] }>()
const entries = computed(() => {
  const summaries = new Map(props.summaries.map(summary => [summary.locationId, summary]))
  return props.locations.map(location => ({ location, summary: summaries.get(location.id) }))
})
</script>
<template>
  <section class="card directory-card" aria-label="当前楼层空间目录">
    <div class="section-heading"><h2>{{ title }}空间目录</h2><small>{{ sampleFloor ? `${locations.length}处可进入观察 · 按显式位置选择` : '此处内景将在后续批次制作，目录保持可用' }}</small></div>
    <ul class="location-grid">
      <li v-for="entry in entries" :key="entry.location.id" :class="[{ selected: entry.location.id === selectedId, 'campus-location-status': !!entry.summary }, entry.summary?.category]">
        <div class="location-heading"><strong>{{ entry.location.name }}</strong><span class="tag" :class="{ observe: !entry.location.reportable }">{{ entry.location.reportable ? '范围内报修位置' : '仅供观察' }}</span></div>
        <template v-if="entry.summary">
          <p class="business-category">{{ categoryNames[entry.summary.category] }} · {{ entry.summary.visibleTotal }}项</p>
          <p class="stage-counts"><template v-for="(amount,stage) in entry.summary.counts" :key="stage"><span v-if="amount">{{ stageNames[stage] }} {{ amount }}</span></template></p>
          <button type="button" class="secondary full" @click="emit('open',entry.location.id)">{{ entry.summary.visibleTotal === 1 ? '查看事项详情' : '选择查看事项' }}</button>
        </template>
        <button type="button" class="secondary full" :aria-pressed="entry.location.id === selectedId" @click="emit('select',entry.location.id)">定位{{ entry.location.name }}</button>
        <p>{{ !entry.location.reportable ? '本区域不提供报修' : entry.location.canCreateRequest ? '定位后可打开共用报修表单' : '当前角色可浏览此位置' }}</p>
      </li>
    </ul>
  </section>
</template>
<style scoped>
.location-heading{display:flex;flex-wrap:wrap;justify-content:space-between;align-items:center;gap:6px;margin-bottom:14px}.location-heading strong{margin:0}.location-grid{margin-top:12px;grid-template-columns:repeat(auto-fill,minmax(160px,1fr))}.location-grid li.selected{box-shadow:0 0 0 2px #49869b}.location-grid li.campus-location-status{border-color:var(--campus-status-border);background:var(--campus-status-background);color:var(--campus-status-text)}.business-category{font-weight:600}.business-category,.stage-counts{color:inherit}.stage-counts{display:flex;gap:8px;flex-wrap:wrap}.location-grid button+button{margin-top:8px}button:focus-visible{outline:3px solid #478aa6;outline-offset:3px}@media(max-width:767px){.location-grid{grid-template-columns:1fr}}
</style>
