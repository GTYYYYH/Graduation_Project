<script setup lang="ts">
import { computed, shallowRef } from 'vue'
import type { EligibleTechnician, OrderDetail } from '../../api/workorder'
import { useAuthStore } from '../../stores/auth'
import { useDraftStore } from '../../stores/drafts'
import { useBusinessAction } from '../../composables/useBusinessAction'
import { useBusinessConfirmation } from '../../composables/useBusinessConfirmation'
import AssignmentPanel from './AssignmentPanel.vue'
import InlineConfirmation from '../common/InlineConfirmation.vue'
const props = defineProps<{ detail: OrderDetail; refresh: () => Promise<unknown> }>()
const auth = useAuthStore(), drafts = useDraftStore(), confirmation = useBusinessConfirmation(), localMessage = shallowRef('')
const selected = shallowRef<EligibleTechnician | null>(null), ready = shallowRef(false)
const draft = computed(() => drafts.action(auth.currentUser?.id ?? '', 'ASSIGN_DRAFT', props.detail.id))
const action = useBusinessAction('ASSIGNED', () => props.detail.id, async () => { draft.value.reason = ''; draft.value.selectedId = ''; draft.value.confirmedRefusal = false; selected.value = null; await props.refresh() })
const busy = computed(() => action.busy.value || !!confirmation.message.value)
const context = computed(() => props.detail.status === 'PENDING_ACCEPT' ? 'CHANGE_BEFORE_ACCEPT' as const : 'REASSIGN' as const)
const allowed = computed(() => props.detail.allowedActions.includes('ASSIGN'))
function choose(person: EligibleTechnician | null) { if (person && (action.pending.value || busy.value)) return; selected.value = person; draft.value.selectedId = person?.id ?? ''; draft.value.confirmedRefusal = false }
async function submit() {
  const observed = props.detail, candidate = selected.value, reason = draft.value.reason.trim(), confirmedRefusal = draft.value.confirmedRefusal
  if (!allowed.value || busy.value || action.pending.value || !ready.value || !candidate || !reason) { localMessage.value = '请核对实时资格、主动选人并填写原因。'; return }
  if (candidate.id === observed.assignedTechnician?.id) { localMessage.value = '不能改派给当前负责人。'; return }
  if (candidate.refusal.length && !draft.value.confirmedRefusal) { localMessage.value = '此人曾拒单，请核对原拒单记录后明确确认。'; return }
  if (!await confirmation.ask(`确认将 ${observed.workOrderNo} 人工分派给 ${candidate.name}？原因：${reason}${candidate.refusal.length ? '；已核对其曾拒单记录。' : ''}`)) return
  if (observed !== props.detail || selected.value !== candidate || !allowed.value || !ready.value || action.pending.value || reason !== draft.value.reason.trim() || confirmedRefusal !== draft.value.confirmedRefusal) { localMessage.value = '工单、人选或原因已变化，请重新核对。'; return }
  const operationId = crypto.randomUUID(), payload = { version: observed.version, technicianId: candidate.id, reason, confirmPreviouslyRefused: confirmedRefusal, operationId }
  await action.submit({ operationId, operation: 'ASSIGNED', path: `/api/work-orders/${observed.id}/assign`, method: 'POST', payload, resourceType: 'WORK_ORDER', resourceId: observed.id, phase: 'SENDING' })
}
</script>
<template>
  <section class="card" aria-label="管理员人工重新分派">
    <h2>人工改派／重派</h2><InlineConfirmation :message="confirmation.message.value" @answer="confirmation.answer" />
    <p v-if="localMessage || action.message.value" class="notice" role="alert">{{ localMessage || action.message.value }}</p>
    <div v-if="action.pending.value" class="notice"><strong>原分派操作待确认</strong><p>人员：{{ action.pending.value.payload.technicianId }}；原因：{{ action.pending.value.payload.reason }}。</p><button class="secondary" :disabled="busy" @click="action.check">核对原分派结果</button><button class="secondary" :disabled="busy" @click="action.retry">原标识重试</button></div>
    <template v-if="allowed"><p>当前负责人：{{ detail.assignedTechnician?.name ?? '待重派，无当前负责人' }}</p><AssignmentPanel :work-order-id="detail.id" :context="context" :selected-id="draft.selectedId" :disabled="busy || !!action.pending.value" @selected="choose" @ready="ready = $event" /><label>改派／重派原因（必填）<textarea v-model="draft.reason" maxlength="2000" :disabled="busy || !!action.pending.value"></textarea></label><label v-if="selected?.refusal.length"><input v-model="draft.confirmedRefusal" type="checkbox" :disabled="busy || !!action.pending.value">已核对 {{ selected.name }} 曾拒单：{{ selected.refusal[0]?.reason }}</label><button class="primary" :disabled="busy || !!action.pending.value || !ready || !selected || !draft.reason.trim() || selected?.id === detail.assignedTechnician?.id || (!!selected?.refusal.length && !draft.confirmedRefusal)" @click="submit">核对并重新分派</button></template>
    <p v-else-if="!action.pending.value" class="muted">接单后负责人固定；维修与验收资料由管理员只读查看。</p>
  </section>
</template>
