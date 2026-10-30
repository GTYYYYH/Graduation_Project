<script setup lang="ts">
import type { AssignmentCandidate, EligibleTechnician } from '../../api/workorder'
defineProps<{ candidates: (EligibleTechnician | AssignmentCandidate)[]; selectedId: string; disabled: boolean }>()
defineEmits<{ selected: [candidate: EligibleTechnician] }>()
const time=(value:string|null)=>value ? new Date(value).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',hour12:false}) : '从未分派'
</script>
<template>
  <ul class="business-list candidate-list">
    <li v-for="candidate in candidates" :key="candidate.id" class="candidate-row" :data-candidate-id="candidate.id">
      <div class="candidate-info"><strong>{{ candidate.name }}</strong><span class="muted"> · 审核通过 · {{ candidate.enabled ? '启用' : '停用' }}</span>
        <template v-if="'load' in candidate"><p>待接 W={{ candidate.W }} · 处理中 I={{ candidate.I }} · 返工 R={{ candidate.R }} · 待验收 A={{ candidate.A }}</p><p><strong>负荷 L={{ candidate.load }}</strong> · 今日完成 D={{ candidate.todayCompleted }} · 最近分派：{{ time(candidate.lastAssignedAt) }}</p><p class="muted">{{ candidate.reason }}</p></template>
        <p v-else class="muted">推荐指标暂不可用；实时资格已核对，可人工选择。</p>
        <p v-for="refusal in candidate.refusal" :key="refusal.assignmentId" class="notice">曾拒单：{{ refusal.reason }}（{{ time(refusal.refusedAt) }}）；提交前须明确确认。</p>
      </div>
      <button type="button" class="secondary" :disabled="disabled || !candidate.enabled || !candidate.eligible" :aria-pressed="selectedId === candidate.id" @click="$emit('selected',candidate)">{{ selectedId === candidate.id ? '已选择' : '选择此维修人员' }}</button>
    </li>
  </ul>
</template>
<style scoped>
.candidate-row { display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:12px; }
.candidate-info { min-width:0;flex:1 1 270px;overflow-wrap:anywhere; }.candidate-info p { margin:6px 0; }
button[aria-pressed="true"] { background:#eaf3f6;border-color:#155e75; }
</style>
