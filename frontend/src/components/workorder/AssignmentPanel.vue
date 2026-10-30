<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import type { EligibleTechnician } from '../../api/workorder'
import { useAssignmentCandidates } from './useAssignmentCandidates'
import AssignmentCandidateList from './AssignmentCandidateList.vue'
const props=defineProps<{requestId?:string;workOrderId?:string;context?:'CHANGE_BEFORE_ACCEPT'|'REASSIGN';selectedId:string;disabled?:boolean}>()
const emit=defineEmits<{selected:[candidate:EligibleTechnician|null];ready:[ready:boolean]}>()
const selection=shallowRef(props.selectedId),expanded=shallowRef(false),searchText=shallowRef('')
watch(()=>props.selectedId,id=>{selection.value=id})
const state=useAssignmentCandidates(()=>props.workOrderId && props.context ? {workOrderId:props.workOrderId,context:props.context} : {requestId:props.requestId??''},selection)
watch(state.selected,value=>emit('selected',value),{flush:'sync'})
watch(state.ready,value=>emit('ready',value),{immediate:true,flush:'sync'})
const qualification=state.qualification,recommendation=state.recommendation
const total=computed(()=>qualification.value?.eligibleTotal??0)
const fallbackRows=computed(()=>{const term=state.keyword.value.trim().toLocaleLowerCase();return (qualification.value?.candidates.records??[]).filter(c=>!term || c.name.toLocaleLowerCase().includes(term))})
const allRows=computed(()=>{
  const eligible=new Set(qualification.value?.candidates.records.map(c=>c.id)??[])
  if(recommendation.value)return recommendation.value.candidates.records.filter(c=>eligible.has(c.id))
  return fallbackRows.value.slice((state.page.value-1)*20,state.page.value*20)
})
const preferred=computed(()=>recommendation.value?.top5.filter(c=>qualification.value?.candidates.records.some(q=>q.id===c.id))??[])
const filteredTotal=computed(()=>recommendation.value?.candidates.total??fallbackRows.value.length)
function search() {if(props.disabled)return;expanded.value=true;state.page.value=1;state.keyword.value=searchText.value}
const time=(value:string)=>new Date(value).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',hour12:false})
</script>
<template>
  <section aria-label="人工选择合格维修人员">
    <h3>{{ workOrderId ? '人工重新分派' : '人工首次派单' }}</h3>
    <p class="muted">按负荷、今日完成量、最近分派和人员ID提供建议。请主动选人；任意合格人员均可选择。</p>
    <div class="actions"><button type="button" class="secondary" :disabled="disabled || state.loading.value" @click="state.load">刷新资格与推荐</button><button type="button" class="text-button" :disabled="disabled || !state.ready.value" @click="state.select(null)">清除人员选择</button></div>
    <p v-if="state.loading.value" role="status">正在核对推荐与实时资格…</p>
    <p v-if="state.notice.value" class="notice" role="alert">{{ state.notice.value }}</p>
    <p v-if="state.recommendationError.value" class="notice" role="alert">推荐不可用：{{ state.recommendationError.value }}。实时资格读取成功后仍可展开全部名单人工选择。</p>
    <p v-if="state.qualificationError.value" class="notice" role="alert">资格无法确认：{{ state.qualificationError.value }}。已暂停提交，请重新读取；其余输入保留。</p>
    <template v-if="qualification">
      <p>当前合格人员共{{ total }}名；资格取数 {{ time(qualification.queriedAt) }}（上海时间）。</p>
      <p v-if="recommendation" class="muted">推荐取数 {{ time(recommendation.queriedAt) }} · 业务日期 {{ recommendation.businessDate }} · 前台每10秒核对。</p>
      <p v-if="!total" class="notice">无人具备资格，当前不能派单。原申请或工单保留原状态。</p>
      <template v-else>
        <h4>优先推荐 Top5</h4><p v-if="recommendation && !preferred.length" class="notice">暂无优先推荐；全部合格人员仍可人工选择。</p>
        <AssignmentCandidateList :candidates="preferred" :selected-id="selection" :disabled="!!disabled || !state.ready.value" @selected="state.select" />
        <button type="button" class="secondary" :aria-expanded="expanded" @click="expanded=!expanded">{{ expanded ? '收起' : '展开' }}全部合格人员（{{ total }}名）</button>
        <div v-if="expanded">
          <form @submit.prevent="search"><label>搜索维修人员姓名<input v-model="searchText" maxlength="100" :disabled="disabled"></label><button class="secondary" :disabled="disabled || state.loading.value">查询名单</button></form>
          <p>符合搜索条件{{ filteredTotal }}名。</p><p v-if="!allRows.length" class="muted">没有符合条件的人员，可修改搜索或翻页。</p>
          <AssignmentCandidateList :candidates="allRows" :selected-id="selection" :disabled="!!disabled || !state.ready.value" @selected="state.select" />
          <div class="actions"><button type="button" class="secondary" :disabled="disabled || state.loading.value || state.page.value<=1" @click="state.page.value--">上一页名单</button><span>第{{ state.page.value }}页</span><button type="button" class="secondary" :disabled="disabled || state.loading.value || state.page.value*20>=filteredTotal" @click="state.page.value++">下一页名单</button></div>
        </div>
        <p v-if="state.selected.value">已选维修人员：<strong>{{ state.selected.value.name }}</strong>；排序变化会保留仍合法的选择。</p>
      </template>
    </template>
  </section>
</template>
