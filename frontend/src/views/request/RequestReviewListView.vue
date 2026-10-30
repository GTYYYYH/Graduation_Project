<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchAdminRequests, requestStatusNames } from '../../api/repair-request'
import { useBusinessRead } from '../../composables/useBusinessRead'
import { requestReviewCountRefresh } from '../../composables/useReviewNotifications'
const route = useRoute(), router = useRouter()
const keyword = shallowRef(String(route.query.keyword ?? '')), status = shallowRef(String(route.query.status ?? 'PENDING_REVIEW'))
const page = computed(() => Math.max(1, Number(route.query.page ?? 1) || 1))
const { data, loading, message, stale, lastSuccessAt, clear, load } = useBusinessRead(() => fetchAdminRequests({ page: page.value, pageSize: 20, keyword: String(route.query.keyword ?? ''), status: String(route.query.status ?? 'PENDING_REVIEW') }))
function apply(value = 1) { void router.replace({ path: '/admin/requests', query: { keyword: keyword.value, status: status.value, page: String(value) } }) }
async function refresh() { await load(); requestReviewCountRefresh() }
watch(() => route.fullPath, () => { keyword.value = String(route.query.keyword ?? ''); status.value = String(route.query.status ?? 'PENDING_REVIEW'); clear(); void load() }, { immediate: true })
</script>
<template>
  <div class="page-heading"><div><h1>申请审核与首次派单</h1><p class="muted">查看报修原文，选择合格维修人员即可审核通过并生成待接单工单。</p></div><button class="secondary" :disabled="loading" @click="refresh">刷新申请与数量</button></div>
  <section class="card"><form @submit.prevent="apply()"><div class="form-grid"><label>申请状态<select v-model="status"><option value="">全部状态</option><option v-for="(name, code) in requestStatusNames" :key="code" :value="code">{{ name }}</option></select></label><label>搜索申请编号／设备名称<input v-model="keyword" maxlength="100"></label></div><div class="actions"><button class="primary" :disabled="loading">查询申请</button><button type="button" class="secondary" :disabled="loading" @click="status = 'PENDING_REVIEW'; keyword = ''; apply()">查看全部待审核</button></div></form></section>
  <p v-if="loading" role="status">正在读取申请…</p><p v-if="message" class="notice" role="alert">{{ message }} {{ stale ? `资料可能过期；上次读取 ${lastSuccessAt}` : '' }} <button class="text-button" @click="load">重试读取</button></p>
  <section v-if="data" class="card"><p>符合当前条件 {{ data.total }} 条</p><p v-if="!data.records.length" class="muted">当前没有符合条件的申请。</p><div class="review-list"><article v-for="request in data.records" :key="request.id" class="person-row"><div><strong>{{ request.requestNo }} · {{ request.equipmentName }}</strong><p>{{ requestStatusNames[request.status] }} · {{ request.locationNameSnapshot || '旧文字位置，未关联3D' }}</p><p>首次提交 {{ new Date(request.firstSubmittedAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}</p><p v-if="request.workOrder">关联工单 {{ request.workOrder.workOrderNo }} · {{ request.workOrder.status === 'PENDING_ACCEPT' ? '待接单' : request.workOrder.status }}</p></div><RouterLink :to="{ path: `/admin/requests/${request.id}`, query: { returnTo: route.fullPath } }">{{ request.status === 'PENDING_REVIEW' ? '查看原文并审核' : '查看申请与历史' }}</RouterLink></article></div><div class="actions"><button class="secondary" :disabled="loading || data.page <= 1" @click="apply(data.page - 1)">上一页申请</button><span>第{{ data.page }}页</span><button class="secondary" :disabled="loading || data.page * data.pageSize >= data.total" @click="apply(data.page + 1)">下一页申请</button></div></section>
</template>

