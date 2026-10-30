<script setup lang="ts">
import type { LocationSummary } from '../../api/campus'
import { categoryNames,stageNames } from '../../api/campus'
import '../../campus/location-status.css'
defineProps<{markers:(Omit<LocationSummary,'queriedAt'> & {name:string;x:number;y:number})[]}>()
const emit=defineEmits<{open:[id:string]}>()
</script>
<template>
  <div class="summary-markers" aria-label="当前位置可见未完结事项提示">
    <button v-for="marker in markers" :key="marker.locationId" type="button" class="summary-marker campus-location-status" :class="marker.category" :style="{left:`${marker.x}px`,top:`${marker.y}px`}" :aria-label="`${marker.name}：${categoryNames[marker.category]}，共${marker.visibleTotal}项，${marker.visibleTotal === 1 ? '查看事项详情' : '打开事项列表'}`" @click="emit('open',marker.locationId)">
      <strong>{{ marker.name }}</strong><span>{{ categoryNames[marker.category] }} · {{ marker.visibleTotal }}项</span>
      <small><template v-for="(amount,stage) in marker.counts" :key="stage"><span v-if="amount">{{ stageNames[stage] }} {{ amount }} </span></template></small>
    </button>
  </div>
</template>
<style scoped>
.summary-markers{position:absolute;inset:0;pointer-events:none;z-index:2}.summary-marker{position:absolute;transform:translate(-50%,-115%);pointer-events:auto;min-height:44px;min-width:100px;max-width:210px;padding:7px 10px;border:2px solid var(--campus-status-border);border-radius:8px;background:var(--campus-status-background);color:var(--campus-status-text);box-shadow:0 2px 8px #203c4b20;text-align:left;font-size:12px;cursor:pointer}.summary-marker strong,.summary-marker>span,.summary-marker small{display:block}.summary-marker:focus-visible{outline:3px solid #155e75;outline-offset:3px}
</style>
