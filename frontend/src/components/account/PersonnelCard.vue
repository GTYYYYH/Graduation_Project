<script setup lang="ts">
import type { PersonnelVo } from '../../api/account'
import AccountStatusControls from './AccountStatusControls.vue'
defineProps<{person:PersonnelVo;back:string;blocked?:boolean}>();defineEmits<{changed:[]}>()
const roles={ADMIN:'管理员',TECHNICIAN:'维修人员',REPORTER:'报修用户'}
const review={PENDING:'待审核',APPROVED:'已通过',REJECTED:'未通过'}
const countNames:Record<string,string>={pendingRequests:'待处理申请',unfinishedOrders:'未完结工单',completedOrders:'本人已完成工单',unfinishedTasks:'当前未完结任务'}
</script>
<template><article class="card person-card"><h2>{{person.name}} <span class="tag">{{roles[person.role]}}</span></h2><dl><dt>登录账号</dt><dd>{{person.loginName}}</dd><dt>手机号</dt><dd>{{person.phone}}</dd><dt>启停用</dt><dd>{{person.enabled?'启用':'停用'}}</dd><dt>资格</dt><dd>{{person.role==='TECHNICIAN'?`${review[person.reviewStatus!]} · 第${person.reviewAttempt}次申请`:person.role==='ADMIN'?'预置账号':'无需审核'}}</dd></dl><template v-if="person.counts"><p v-for="key in Object.keys(countNames).filter(k=>person.counts?.[k]!==undefined)" :key="key">{{countNames[key]}}：{{person.counts[key]}}</p></template><p v-else class="muted">管理员通过全局业务管理入口查看记录。</p><div class="actions"><RouterLink :to="{path:`/admin/personnel/${person.id}`,query:{listReturn:back}}">人员详情 / 完整历史</RouterLink><RouterLink v-if="person.role==='TECHNICIAN'&&person.reviewStatus==='PENDING'" :to="{path:`/admin/accounts/${person.id}/registration-review`,query:{listReturn:back}}">{{person.enabled?'审核申请':'查看审核申请'}}</RouterLink><AccountStatusControls :person="person" :blocked="blocked" @changed="$emit('changed')"/></div></article></template>
<style scoped>.person-card{min-width:0}.person-card dd,.person-card p{overflow-wrap:anywhere}</style>
