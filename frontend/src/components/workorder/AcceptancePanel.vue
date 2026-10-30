<script setup lang="ts">
import { computed, shallowRef } from 'vue'
import type { OrderDetail } from '../../api/workorder'
import { useAuthStore } from '../../stores/auth'
import { useDraftStore } from '../../stores/drafts'
import { useBusinessAction } from '../../composables/useBusinessAction'
import { useBusinessConfirmation } from '../../composables/useBusinessConfirmation'
import InlineConfirmation from '../common/InlineConfirmation.vue'
const props = defineProps<{ detail: OrderDetail; refresh: () => Promise<unknown> }>()
const auth = useAuthStore(), drafts = useDraftStore(), confirmation = useBusinessConfirmation(), localMessage = shallowRef('')
const current = computed(() => props.detail.rounds.find(r => r.round === props.detail.round))
const draft = computed(() => drafts.action(auth.currentUser?.id ?? '', 'ACCEPTANCE_DRAFT', `${props.detail.id}/${props.detail.round}`))
const action = useBusinessAction('ACCEPTANCE', () => props.detail.id, async (_receipt, original) => { drafts.clearAction(`ACCEPTANCE_DRAFT/${auth.currentUser?.id}/${original.resourceId}/${original.payload.round}`); await props.refresh() })
const busy = computed(() => action.busy.value || !!confirmation.message.value)
const canAccept = computed(() => props.detail.allowedActions.includes('ACCEPTANCE') && !!current.value?.submittedAt && !!current.value.result && current.value.attachments.length > 0)
async function decide(passed: boolean) {
  if (!canAccept.value || busy.value || action.pending.value) return
  const observed = props.detail, reason = passed ? null : draft.value.reason.trim()
  if (!passed && !reason) { localMessage.value = '验收不通过须填写原因。'; return }
  if (!await confirmation.ask(`确认本轮验收 ${observed.workOrderNo} · 第${observed.round}轮？${passed ? '通过后工单完成。' : `退回原维修人员返工。原因：${reason}`}`)) return
  if (observed !== props.detail || !canAccept.value || action.pending.value || (!passed && reason !== draft.value.reason.trim())) { localMessage.value = '本轮结果或原因已变化，请重新核对。'; return }
  const operationId = crypto.randomUUID(), payload = { version: observed.version, round: observed.round, passed, ...(reason ? { reason } : {}), operationId }
  await action.submit({ operationId, operation: 'ACCEPTANCE', path: `/api/work-orders/${observed.id}/acceptance`, method: 'POST', payload, resourceType: 'WORK_ORDER', resourceId: observed.id, phase: 'SENDING' })
}
</script>
<template>
  <section class="card" aria-label="原报修人验收">
    <h2>本轮验收</h2><p class="muted">请先核对上方第{{ detail.round }}轮处理结果和本轮新维修凭证，再明确确认。</p>
    <InlineConfirmation :message="confirmation.message.value" @answer="confirmation.answer" />
    <p v-if="localMessage || action.message.value" class="notice" role="alert">{{ localMessage || action.message.value }}</p>
    <div v-if="action.pending.value" class="notice"><strong>原验收结果待确认</strong><p>保留原原因和标识；NOT_CONFIRMED 不代表事务失败。</p><button class="secondary" :disabled="busy" @click="action.check">核对原验收结果</button><button class="secondary" :disabled="busy" @click="action.retry">原标识重试</button></div>
    <template v-if="canAccept"><div class="actions"><button type="button" class="primary" :disabled="busy || !!action.pending.value" @click="decide(true)">验收通过</button></div><label>验收不通过原因（必填）<textarea v-model="draft.reason" maxlength="2000" :disabled="busy || !!action.pending.value"></textarea></label><button type="button" class="secondary" :disabled="busy || !!action.pending.value || !draft.reason.trim()" @click="decide(false)">核对并退回返工</button></template>
    <p v-else-if="!action.pending.value" class="muted">当前没有可验收的本轮维修结果。</p>
  </section>
</template>
