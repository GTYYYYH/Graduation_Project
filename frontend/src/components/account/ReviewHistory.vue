<script setup lang="ts">
import type { ReviewSubmissionVo } from '../../api/account'
defineProps<{ history: ReviewSubmissionVo[] }>()
const status = { PENDING: '待审核', REJECTED: '审核未通过', APPROVED: '审核通过' }
const time = (value: string) => new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })
</script>
<template><section class="card"><h2>申请与审核历史</h2><p class="fine">最新在前；每轮保留当次资料快照。</p><details v-for="item in history" :key="item.id"><summary>第 {{ item.attempt }} 次 · {{ status[item.status] }}<br><small>{{ time(item.submittedAt) }} 提交</small></summary><dl><dt>姓名快照</dt><dd>{{ item.nameSnapshot }}</dd><dt>手机号快照</dt><dd>{{ item.phoneSnapshot }}</dd><dt>审核人</dt><dd>{{ item.reviewedBy?.name ?? '尚未审核' }}</dd><dt>审核时间</dt><dd>{{ item.reviewedAt ? time(item.reviewedAt) : '—' }}</dd></dl><p v-if="item.reason" class="notice">原因：{{ item.reason }}</p></details><p v-if="!history.length" class="muted">暂无审核记录。</p></section></template>
