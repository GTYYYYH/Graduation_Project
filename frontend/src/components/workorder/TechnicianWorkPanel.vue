<script setup lang="ts">
import { computed, onUnmounted, shallowRef } from 'vue'
import type { OrderDetail } from '../../api/workorder'
import { useAuthStore } from '../../stores/auth'
import { useDraftStore } from '../../stores/drafts'
import { useBusinessAction } from '../../composables/useBusinessAction'
import { useBusinessConfirmation } from '../../composables/useBusinessConfirmation'
import { ensureTemporaryUploaded } from '../../composables/useImageSelection'
import { uploadTemporary } from '../../api/attachment'
import ImagePicker from '../attachment/ImagePicker.vue'
import InlineConfirmation from '../common/InlineConfirmation.vue'
const props = defineProps<{ detail: OrderDetail; refresh: () => Promise<unknown> }>()
const auth = useAuthStore(), drafts = useDraftStore(), confirmation = useBusinessConfirmation(), localMessage = shallowRef('')
const uploading = shallowRef(false)
let alive = true
onUnmounted(() => { alive = false })
const round = computed(() => props.detail.round ?? 0), userId = computed(() => auth.currentUser?.id ?? '')
const refusal = computed(() => drafts.action(userId.value, 'REFUSAL_DRAFT', props.detail.id))
const progressDraft = computed(() => drafts.action(userId.value, 'PROGRESS_DRAFT', `${props.detail.id}/${round.value}`))
const resultDraft = computed(() => drafts.action(userId.value, 'REPAIR_RESULT', `${props.detail.id}/${round.value}`))
const context = computed(() => ({ purpose: 'REPAIR_VOUCHER' as const, workOrderId: props.detail.id, round: round.value }))
const accept = useBusinessAction('ACCEPTED', () => props.detail.id, async () => { await props.refresh() })
const refuse = useBusinessAction('REFUSED', () => props.detail.id, async (_receipt, original) => { drafts.clearAction(`REFUSAL_DRAFT/${userId.value}/${original.resourceId}`); await props.refresh() })
const progress = useBusinessAction('PROGRESS_ADDED', () => props.detail.id, async (_receipt, original) => { drafts.clearAction(`PROGRESS_DRAFT/${userId.value}/${original.resourceId}/${original.payload.round}`); await props.refresh() })
const result = useBusinessAction('RESULT_SUBMITTED', () => props.detail.id, async (_receipt, original) => { drafts.clearAction(`REPAIR_RESULT/${userId.value}/${original.resourceId}/${original.payload.round}`); await props.refresh() })
const pending = computed(() => accept.pending.value || refuse.pending.value || progress.pending.value || result.pending.value)
const busy = computed(() => uploading.value || accept.busy.value || refuse.busy.value || progress.busy.value || result.busy.value || !!confirmation.message.value)
const canAccept = computed(() => props.detail.allowedActions.includes('ACCEPT'))
const canRepair = computed(() => props.detail.allowedActions.includes('PROGRESS') && round.value > 0)
const retainedRepair = computed(() => round.value > 0 && !!(progressDraft.value.progress || resultDraft.value.result || resultDraft.value.images.length))
function clearFeedback() { localMessage.value = ''; for (const action of [accept, refuse, progress, result]) action.message.value = '' }
function submission(operation: string, action: string, payload: Record<string, unknown>) {
  return { operationId: String(payload.operationId), operation, path: `/api/work-orders/${props.detail.id}/${action}`, method: 'POST' as const, payload, resourceType: 'WORK_ORDER', resourceId: props.detail.id, phase: 'SENDING' as const }
}
async function decide(kind: 'accept' | 'refuse') {
  if (!canAccept.value || busy.value || pending.value) return
  clearFeedback()
  const observed = props.detail, reason = refusal.value.reason.trim()
  if (kind === 'refuse' && !reason) { localMessage.value = '请填写拒单原因。'; return }
  if (!await confirmation.ask(kind === 'accept' ? `核对原报修材料后，确认接单 ${observed.workOrderNo}？` : `确认拒单 ${observed.workOrderNo}？原因：${reason}`)) return
  if (observed !== props.detail || !canAccept.value || pending.value) { localMessage.value = '工单资料已变化，请重新核对。'; return }
  const operationId = crypto.randomUUID(), payload = { version: observed.version, operationId, ...(kind === 'refuse' ? { reason } : {}) }
  await (kind === 'accept' ? accept : refuse).submit(submission(kind === 'accept' ? 'ACCEPTED' : 'REFUSED', kind, payload))
}
async function addProgress() {
  if (!canRepair.value || busy.value || pending.value || !progressDraft.value.progress.trim()) return
  clearFeedback()
  const observed = props.detail, content = progressDraft.value.progress.trim()
  if (!await confirmation.ask(`确认独立追加第${round.value}轮维修进度？内容：${content}`)) return
  if (observed !== props.detail || pending.value) { localMessage.value = '工单资料已变化，请重新核对。'; return }
  const operationId = crypto.randomUUID()
  await progress.submit(submission('PROGRESS_ADDED', 'progress', { version: observed.version, round: round.value, content, operationId }))
}
async function submitResult() {
  if (!canRepair.value || busy.value || pending.value) return
  clearFeedback()
  const observed = props.detail, draft = resultDraft.value, text = draft.result.trim(), images = [...draft.images]
  if (!text || images.length < 1 || images.length > 5) { localMessage.value = '本轮处理结果和1—5张本轮新凭证都必填。'; return }
  if (!await confirmation.ask(`核对第${round.value}轮处理结果与${images.length}张新凭证，确认提交给原报修人验收？`)) return
  if (observed !== props.detail || draft !== resultDraft.value || pending.value) { localMessage.value = '工单或轮次已变化，请重新核对。'; return }
  const epoch = auth.identityEpoch
  const isCurrent = () => alive && epoch === auth.identityEpoch && observed === props.detail && canRepair.value && draft === resultDraft.value && text === draft.result.trim() && images.length === draft.images.length && images.every((image, index) => image === draft.images[index])
  uploading.value = true
  try {
    if (!await auth.ensureCsrf() || !isCurrent()) return
    const uploaded = await ensureTemporaryUploaded(images, context.value, uploadTemporary, isCurrent)
    if (uploaded.kind !== 'SUCCESS') { if (alive) localMessage.value = uploaded.message; return }
    if (!isCurrent()) { localMessage.value = '工单、权限或编辑内容已变化，请重新核对。'; return }
    const operationId = crypto.randomUUID()
    await result.submit(submission('RESULT_SUBMITTED', 'submit-result', { version: observed.version, round: round.value, result: text, attachmentIds: uploaded.ids, operationId }))
  } finally { uploading.value = false }
}
</script>
<template>
  <section class="card" aria-label="维修人员处理工单">
    <h2>维修处理</h2><p class="muted">请先核对上方原报修描述和图片。进度与本轮结果分别提交。</p>
    <InlineConfirmation :message="confirmation.message.value" @answer="confirmation.answer" />
    <p v-if="localMessage || accept.message.value || refuse.message.value || progress.message.value || result.message.value" class="notice" role="alert">{{ localMessage || accept.message.value || refuse.message.value || progress.message.value || result.message.value }}</p>
    <div v-for="item in [accept, refuse, progress, result]" :key="item === accept ? 'accept' : item === refuse ? 'refuse' : item === progress ? 'progress' : 'result'">
      <div v-if="item.pending.value" class="notice"><strong>原操作结果待确认</strong><p>{{ item.pending.value.operation }} · {{ item.pending.value.operationId }}</p><button class="secondary" :disabled="item.busy.value" @click="item.check">核对原结果</button><button class="secondary" :disabled="item.busy.value" @click="item.retry">原标识重试</button></div>
    </div>
    <div v-if="canAccept" class="actions"><button class="primary" :disabled="busy || !!pending" @click="decide('accept')">核对并接单</button><label>拒单原因（必填）<textarea v-model="refusal.reason" maxlength="2000" :disabled="busy || !!pending"></textarea></label><button class="secondary" :disabled="busy || !!pending || !refusal.reason.trim()" @click="decide('refuse')">核对并拒单</button></div>
    <p v-if="!canRepair && retainedRepair" class="notice">工单状态或可用操作已变化，编辑与本轮图片选择保留，旧提交已禁用。</p><template v-if="canRepair || retainedRepair"><h3>第{{ round }}轮 · 独立追加进度</h3><label>进度内容<textarea v-model="progressDraft.progress" maxlength="4000" :disabled="!canRepair || busy || !!pending"></textarea></label><button class="secondary" :disabled="!canRepair || busy || !!pending || !progressDraft.progress.trim()" @click="addProgress">确认追加进度</button><h3>第{{ round }}轮 · 提交处理结果</h3><label>本轮结果<textarea v-model="resultDraft.result" maxlength="4000" :disabled="!canRepair || busy || !!pending"></textarea></label><ImagePicker v-model="resultDraft.images" :context="context" :disabled="!canRepair || busy || !!pending" /><button class="primary" :disabled="!canRepair || busy || !!pending || !resultDraft.result.trim() || !resultDraft.images.length" @click="submitResult">上传本轮新凭证并提交验收</button></template>
    <p v-else-if="!canAccept && !pending" class="muted">当前仅可查看有权历史，暂无维修写入操作。</p>
  </section>
</template>
