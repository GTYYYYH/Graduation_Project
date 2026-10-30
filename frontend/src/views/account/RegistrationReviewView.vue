<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { fetchAdminReview, fetchPendingReviews, submitReviewDecision } from '../../api/account'
import type { ReviewDecisionDto, TechnicianReviewVo, PendingReviewVo } from '../../api/account'
import type { PageResult } from '../../api/common'
import { personnelListReturn } from './personnel-navigation'
import ReviewHistory from '../../components/account/ReviewHistory.vue'
import { createReviewFlow } from './review-flow'
import { requestReviewCountRefresh } from '../../composables/useReviewNotifications'
const route = useRoute(), router = useRouter(), auth = useAuthStore()
const target = computed(() => typeof route.params.id === 'string' ? route.params.id : '')
const detail = ref<TechnicianReviewVo | null>(null), list = ref<PageResult<PendingReviewVo> | null>(null), loading = ref(false), message = ref(''), stale = ref(false)
const keyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : ''), rejecting = ref(false), reason = ref(''), busy = ref(false)
let generation = 0
const page = computed(() => Math.max(1, Number(route.query.page) || 1))
const canDecide = computed(() => detail.value?.allowedActions.includes('REVIEW_APPROVE') ?? false)
const flow = createReviewFlow<ReviewDecisionDto>(dto => submitReviewDecision(target.value, dto), () => detail.value && detail.value.account.id === target.value && auth.businessRouteGranted ? {
  targetId: target.value, identityEpoch: auth.identityEpoch, version: detail.value.account.version, attempt: detail.value.current.attempt,
  reviewVersion: detail.value.current.version, allowed: canDecide.value,
} : null)
async function load() {
  const own = ++generation, epoch = auth.identityEpoch, id = target.value; loading.value = true; message.value = ''
  if (id) {
    const r = await fetchAdminReview(id)
    if (own !== generation || epoch !== auth.identityEpoch || target.value !== id) return
    if (r.kind === 'SUCCESS') { detail.value = r.data; stale.value = false }
    else { message.value = r.kind === 'HTTP_ERROR' ? r.response.message : r.message; stale.value = detail.value !== null; if (r.kind === 'HTTP_ERROR' && [403,404].includes(r.status)) detail.value = null }
  } else {
    const r = await fetchPendingReviews(page.value, typeof route.query.keyword === 'string' ? route.query.keyword : '')
    if (own !== generation || epoch !== auth.identityEpoch || target.value) return
    if (r.kind === 'SUCCESS') { list.value = r.data; stale.value = false }
    else { message.value = r.kind === 'HTTP_ERROR' ? r.response.message : r.message; stale.value = list.value !== null; if (r.kind === 'HTTP_ERROR' && r.status === 403) list.value = null }
  }
  loading.value = false
}
watch(() => route.fullPath, () => { generation++; detail.value = null; list.value = null; rejecting.value = false; reason.value = ''; flow.cancel(); void load() }, { immediate: true })
function search(next = 1) { void router.replace({ path: '/admin/personnel', query: { keyword: keyword.value, page: String(next) } }) }
function prepare(passed: boolean) {
  if (!detail.value || !canDecide.value || busy.value || loading.value) return
  if (!passed && (!reason.value.trim() || [...reason.value.trim()].length > 2000)) { message.value = '未通过原因必填，且不超过2000字'; return }
  flow.prepare({ operationId: crypto.randomUUID(), version: detail.value.account.version, attempt: detail.value.current.attempt,
    reviewVersion: detail.value.current.version, passed, reason: passed ? null : reason.value.trim() })
}
async function confirm() {
  if (busy.value) return; busy.value = true
  try {
    if (!await auth.ensureCsrf()) { message.value = '校验信息获取失败，请重试'; return }
    const r = await flow.confirm()
    if (flow.state.phase === 'SUCCEEDED') { rejecting.value = false; reason.value = ''; requestReviewCountRefresh(); await load() }
    else if (r?.kind === 'HTTP_ERROR') { if (r.response.code === 'CSRF_INVALID') await auth.ensureCsrf(true); if (r.status === 409) await load() }
  } finally { busy.value = false }
}
function cancel() { flow.cancel(); if (flow.state.phase === 'IDLE') { rejecting.value = false; reason.value = '' } }
onUnmounted(() => { generation++; flow.dispose() })
onBeforeRouteLeave(() => busy.value ? false : !rejecting.value && !['CONFIRMING','UNKNOWN'].includes(flow.state.phase) ? true : window.confirm('离开会放弃本次内存草稿。结果待确认时，请先核对原提交。是否离开？'))
</script>
<template>
  <div class="page-heading"><div><p class="eyebrow">人员管理 / {{ target ? '注册审核' : '待审核维修人员' }}</p><h1>{{ target ? '维修人员注册审核' : '待审核维修人员' }}</h1><p class="muted">{{ target ? '核对当次资料及历次快照，再确认审核决定。' : '首次申请与原账号重新申请都进入这里；完整人员管理在后续批次合并。' }}</p></div><button class="secondary" :disabled="loading || busy" @click="load">{{ loading ? '加载中…' : '刷新' }}</button></div>
  <p v-if="message" class="notice" role="alert">{{ message }} {{ stale ? '保留上次结果，数据可能已过期。' : '' }}</p><p v-if="flow.state.message" class="notice" :class="{ success: flow.state.phase === 'SUCCEEDED' }" role="status">{{ flow.state.message }}</p>
  <template v-if="!target"><section class="card"><form @submit.prevent="search()"><label>姓名、账号或手机号<input v-model="keyword" maxlength="100" placeholder="搜索待审核维修人员"></label><div class="actions"><button class="primary" type="submit">搜索</button><button class="secondary" type="button" @click="keyword = ''; search()">重置</button></div></form><p v-if="list" class="fine">当前条件共 {{ list.total }} 人；停用账号仍可查看，须先启用才能审核。</p><p v-if="list && !list.records.length" class="muted">{{ keyword ? '当前条件暂无待审核人员，可重置筛选。' : '暂无待审核人员。' }}</p><div v-if="list" class="review-list"><article v-for="person in list.records" :key="person.id" class="person-row"><div><strong>{{ person.name }} <span class="tag">第 {{ person.reviewAttempt }} 次申请</span></strong><p>账号：{{ person.loginName }} · 手机：{{ person.phone }}<br>账号状态：{{ person.enabled ? '启用' : '停用，只读' }}</p></div><RouterLink :to="{ path: `/admin/accounts/${person.id}/registration-review`, query: route.query }">{{ person.enabled ? '审核申请' : '查看申请' }}</RouterLink></article></div><div v-if="list" class="actions"><button class="secondary" :disabled="page <= 1 || loading" @click="search(page - 1)">上一页</button><span>第 {{ page }} 页</span><button class="secondary" :disabled="page * list.pageSize >= list.total || loading" @click="search(page + 1)">下一页</button></div></section></template>
  <template v-else><RouterLink :to="personnelListReturn(route.query.listReturn)">← 返回人员列表</RouterLink><p v-if="loading && !detail" role="status">正在读取申请…</p><section v-if="!detail && !loading" class="card"><p>申请读取失败，请重试。</p><button class="primary" @click="load">重新加载</button></section>
    <div v-if="detail" class="review-columns" style="margin-top:20px"><section class="card"><h2>本次申请 · 第 {{ detail.current.attempt }} 次</h2><dl><dt>账号</dt><dd>{{ detail.account.loginName }}</dd><dt>角色</dt><dd>维修人员（固定）</dd><dt>姓名快照</dt><dd>{{ detail.current.nameSnapshot }}</dd><dt>手机号快照</dt><dd>{{ detail.current.phoneSnapshot }}</dd><dt>提交时间</dt><dd>{{ new Date(detail.current.submittedAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}</dd><dt>账号状态</dt><dd>{{ detail.account.enabled ? '启用' : '停用' }}</dd><dt>审核状态</dt><dd>{{ ({ PENDING:'待审核', REJECTED:'审核未通过', APPROVED:'审核通过' })[detail.current.status] }}</dd></dl>
      <p v-if="!detail.account.enabled" class="notice">账号已停用，可查看资料和历史。请先到人员管理启用原账号，再审核；不会自动启用。</p><p v-if="detail.current.reason" class="notice">未通过原因：{{ detail.current.reason }}</p><p v-if="detail.current.status === 'APPROVED'" class="muted">已通过资格审核，不会自动派单。</p>
      <div v-if="canDecide && !['CONFIRMING','SUBMITTING','UNKNOWN'].includes(flow.state.phase)"><div class="actions"><button class="primary" @click="prepare(true)">审核通过</button><button class="secondary" @click="rejecting = true">审核未通过</button></div><form v-if="rejecting" @submit.prevent="prepare(false)"><label>未通过原因<textarea v-model="reason" required maxlength="4000" placeholder="说明需修改或补充的资料"></textarea></label><div class="actions"><button class="primary" type="submit">核对未通过原因</button><button class="secondary" type="button" @click="cancel">取消未通过操作</button></div></form></div>
      <section v-if="['CONFIRMING','SUBMITTING','UNKNOWN'].includes(flow.state.phase)"><h3>请核对审核决定</h3><p>账号：{{ detail.account.loginName }}<br>姓名：{{ detail.current.nameSnapshot }}<br>提交次数：{{ flow.state.pending?.attempt }}<br>决定：{{ flow.state.pending?.passed ? '审核通过' : '审核未通过' }}</p><p v-if="flow.state.pending?.reason" class="notice">原因：{{ flow.state.pending.reason }}</p><div class="actions"><button class="primary" :disabled="busy || loading" @click="confirm">{{ busy ? '正在提交…' : flow.state.phase === 'UNKNOWN' ? '重试原提交' : '确认审核决定' }}</button><button v-if="flow.state.phase !== 'UNKNOWN'" class="secondary" :disabled="busy" @click="flow.backToEdit">返回修改</button><button v-if="flow.state.phase !== 'UNKNOWN'" class="secondary" :disabled="busy" @click="cancel">取消操作</button></div></section>
    </section><ReviewHistory :history="detail.history" /></div>
  </template>
</template>

