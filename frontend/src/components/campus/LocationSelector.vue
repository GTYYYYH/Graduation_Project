<script setup lang="ts">
import { computed } from 'vue'
import type { CatalogVo } from '../../api/account'
const props = defineProps<{ catalog: CatalogVo; buildingId: string; floorId: string; locationId: string }>()
const emit = defineEmits<{ building: [id: string]; floor: [id: string]; location: [id: string] }>()
const floors = computed(() => props.catalog.floors.filter(f => f.buildingId === props.buildingId))
const locations = computed(() => props.catalog.locations.filter(l => l.floorId === props.floorId))
const value = (event: Event) => (event.target as HTMLSelectElement).value
</script>
<template>
  <div class="position-selectors">
    <label>楼栋<select :value="buildingId" @change="emit('building', value($event))"><option v-for="b in catalog.buildings" :key="b.id" :value="b.id">{{ b.name }}</option></select></label>
    <label>楼层<select :value="floorId" @change="emit('floor', value($event))"><option v-for="f in floors" :key="f.id" :value="f.id">{{ f.name }}</option></select></label>
    <p v-if="locations.length === 1 && locations[0]?.type === 'AREA'">当前区域：{{ locations[0].name }}<button v-if="locationId !== locations[0].id" type="button" class="secondary" @click="emit('location',locations[0].id)">定位区域入口</button></p>
    <label v-else>房间／区域<select :value="locationId" @change="emit('location', value($event))"><option value="">选择位置，先定位入口</option><option v-for="l in locations" :key="l.id" :value="l.id">{{ l.name }}{{ l.reportable ? '' : ' · 仅供观察' }}</option></select></label>
  </div>
</template>
<style scoped>
.position-selectors { display: grid; grid-template-columns: 1fr 1fr 1.4fr; gap: 16px; }
@media(max-width:767px) { .position-selectors { grid-template-columns: 1fr; gap: 12px; } }
</style>
