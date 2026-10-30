<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { fetchOwnReview, resubmitReview } from '../../api/account'
import type { ResubmitReviewDto, TechnicianReviewVo } from '../../api/account'
import ReviewHistory from '../../components/account/ReviewHistory.vue'
import { createReviewFlow } from './review-flow'
const auth = useAuthStore(), detail = ref<TechnicianReviewVo | null>(null), loading = ref(false), message = ref(''), stale = ref(false)
const editing = ref(false), name = ref(''), phone = ref(''), busy = ref(false)
let generation = 0
const status = { PENDING: '待审核', REJECTED: '审核未通过', APPROVED: '审核通过' }
const canResubmit = computed(() => detail.value?.allowedActions.includes('RESUBMIT_REVIEW') ?? false)
const flow = createReviewFlow<ResubmitReviewDto>(resubmitReview, () => detail.value && auth.businessRouteGranted ? {
  targetId: detail.value.account.id, identityEpoch: auth.identityEpoch, version: detail.value.account.version,
  attempt: detail.value.current.attempt, reviewVersion: detail.value.current.version, allowed: canResubmit.value,
} : null)
async function load() {
  const own = ++generation, epoch = auth.identityEpoch; loading.value = true; message.value = ''
  const r = await fetchOwnReview()
  if (own !== generation || epoch !== auth.identityEpoch) return
  loading.value = false
  if (r.kind === 'SUCCESS') {
    detail.value = r.data; stale.value = false
    const { allowedActions: _actions, ...user } = r.data.account; auth.setConfirmedUser(user)
  } else { message.value = r.kind === 'HTTP_ERROR' ? r.response.message : r.message; stale.value = detail.value !== null; if (r.kind === 'HTTP_ERROR' && r.status === 403) detail.value = null }
}
function edit() { if (!detail.value) return; name.value = detail.value.account.name; phone.value = detail.value.account.phone; editing.value = true; flow.cancel() }
function prepare() {
  if (!detail.value || !canResubmit.value || busy.value || flow.state.phase === 'UNKNOWN') return
  if (!name.value.trim() || [...name.value.trim()].length > 100 || !/^1[3-9][0-9]{9}$/.test(phone.value)) { message.value = '请输入1—100字姓名和大陆11位手机号'; return }
  flow.prepare({ operationId: crypto.randomUUID(), version: detail.value.account.version, attempt: detail.value.current.attempt,
    reviewVersion: detail.value.current.version, name: name.value.trim(), phone: phone.value })
}
async function confirm() {
  if (busy.value) return; busy.value = true
  try {
    if (!await auth.ensureCsrf()) { message.value = '校验信息获取失败，请重试'; return }
    const r = await flow.confirm()
    if (flow.state.phase === 'SUCCEEDED') { editing.value = false; await load() }
    else if (r?.kind === 'HTTP_ERROR') { if (r.response.code === 'CSRF_INVALID') await auth.ensureCsrf(true); if (r.status === 409) await load() }
  } finally { busy.value = false }
}
function cancel() { flow.cancel(); if (flow.state.phase === 'IDLE') { editing.value = false; name.value = ''; phone.value = '' } }
onMounted(load); onUnmounted(() => { generation++; flow.dispose() })
onBeforeRouteLeave(() => busy.value ? false : !editing.value && flow.state.phase !== 'UNKNOWN' ? true : window.confirm('离开会放弃本次内存草稿。结果待确认时，请先核对原提交。是否离开？'))
</script>
<template><div class="page-heading"><div><p class="eyebrow">维修人员资格</p><h1>我的审核状态</h1><p class="muted">账号启用、审核资格和工单状态分别表达。</p></div><button class="secondary" :disabled="loading || busy" @click="load">{{ loading ? '加载中…' : '刷新审核状态' }}</button></div>
  <p v-if="message" class="notice" role="alert">{{ message }} {{ stale ? '保留上次资料，请重试核对最新状态。' : '' }}</p><p v-if="flow.state.message" class="notice" :class="{ success: flow.state.phase === 'SUCCEEDED' }" role="status">{{ flow.state.message }}</p>
  <section v-if="!detail && !loading" class="card"><p>申请加载失败。</p><button class="primary" @click="load">重新加载</button></section><p v-if="loading && !detail" role="status">正在读取申请…</p>
  <div v-if="detail" class="review-columns"><section class="card"><span class="tag">{{ status[detail.current.status] }}</span><h2 style="margin-top:18px">本次申请 · 第 {{ detail.current.attempt }} 次</h2><dl><dt>登录账号</dt><dd>{{ detail.account.loginName }}</dd><dt>固定角色</dt><dd>维修人员</dd><dt>账号状态</dt><dd>{{ detail.account.enabled ? '启用' : '停用' }}</dd><dt>申请姓名</dt><dd>{{ detail.current.nameSnapshot }}</dd><dt>手机号</dt><dd>{{ detail.current.phoneSnapshot }}</dd></dl>
      <p v-if="detail.current.reason" class="notice">审核未通过原因：{{ detail.current.reason }}<br>审核人：{{ detail.current.reviewedBy?.name }}<br>审核时间：{{ detail.current.reviewedAt ? new Date(detail.current.reviewedAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) : '—' }}</p>
      <p v-if="detail.current.status === 'PENDING'" class="muted">申请待审核，资料只读。可浏览校园，审核通过前不能参与维修。</p><p v-if="detail.current.status === 'APPROVED'" class="muted">资格已通过，不会自动分派工单。</p><RouterLink v-if="detail.current.status === 'APPROVED'" to="/tasks">进入我的任务</RouterLink>
      <button v-if="canResubmit && !editing" class="primary" @click="edit">修改资料并重新申请</button>
      <form v-if="editing && !['CONFIRMING','SUBMITTING','UNKNOWN'].includes(flow.state.phase)" @submit.prevent="prepare"><label>姓名<input v-model="name" required maxlength="200"></label><label>手机号码<input v-model="phone" type="tel" inputmode="numeric" required maxlength="11"></label><p v-if="!canResubmit" class="notice">审核状态已变化，当前旧草稿不能提交。</p><div class="actions"><button class="primary" type="submit" :disabled="busy || loading || !canResubmit">核对并重新提交</button><button class="secondary" type="button" @click="cancel">取消修改</button></div></form>
      <section v-if="['CONFIRMING','SUBMITTING','UNKNOWN'].includes(flow.state.phase)"><h3>请核对后确认提交</h3><dl><dt>账号 / 角色</dt><dd>{{ detail.account.loginName }} / 维修人员</dd><dt>新姓名</dt><dd>{{ flow.state.pending?.name }}</dd><dt>新手机号</dt><dd>{{ flow.state.pending?.phone }}</dd></dl><div class="actions"><button class="primary" :disabled="busy || loading" @click="confirm">{{ busy ? '正在提交…' : flow.state.phase === 'UNKNOWN' ? '重试原提交' : '确认提交审核' }}</button><button v-if="flow.state.phase !== 'UNKNOWN'" class="secondary" :disabled="busy" @click="flow.backToEdit">返回修改</button></div></section>
    </section><ReviewHistory :history="detail.history" /></div>
</template>
