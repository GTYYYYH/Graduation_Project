<script setup lang="ts">
import type { LocationItems } from '../../api/campus'
import { categoryNames,stageNames } from '../../api/campus'
import { campusItemPath } from '../../router/businessNavigation'
defineProps<{data:LocationItems|null;loading:boolean;message:string;stale:boolean;lastSuccessAt:string;returnTo:string;page:number;manageRequests?:boolean}>()
const emit=defineEmits<{retry:[];page:[page:number];close:[]}>()
</script>
<template>
  <section class="items-panel" aria-label="当前位置未完结事项">
    <div class="items-heading"><h2>{{ data?.location.name || '当前位置' }} · 未完结事项</h2><button type="button" class="secondary" @click="emit('close')">关闭事项列表</button></div>
    <p v-if="loading" role="status">正在核对当前位置事项…</p>
    <p v-if="message" class="notice" role="alert">{{ message }} {{ stale ? `资料可能过期，上次成功 ${lastSuccessAt}。` : '' }} <button type="button" class="text-button" @click="emit('retry')">重试事项读取</button></p>
    <template v-if="data">
      <p v-if="data.visibleTotal===0" class="muted">当前没有可查看的未完结记录</p>
      <template v-else><p>{{ categoryNames[data.category] }} · 当前有权范围共{{ data.visibleTotal }}项</p><p class="counts"><template v-for="(amount,stage) in data.counts" :key="stage"><span v-if="amount">{{ stageNames[stage] }} {{ amount }}</span></template></p>
      <p v-if="data.items.total===0" class="muted">当前没有可查看的未完结记录</p>
      <ul class="items-list"><li v-for="item in data.items.records" :key="`${item.resourceType}/${item.id}`"><strong>{{ item.businessNo }}</strong><span>{{ item.equipmentName }} · {{ stageNames[item.stage] }}</span><time>{{ item.createdAt }}</time><RouterLink class="secondary link-button" :to="{path:campusItemPath(item,manageRequests),query:{returnTo}}">选择查看此事项</RouterLink></li></ul>
      <div class="actions"><button type="button" class="secondary" :disabled="loading || page<=1" @click="emit('page',page-1)">上一页事项</button><span>第{{ page }}页 · {{ data.items.total }}条</span><button type="button" class="secondary" :disabled="loading || page*data.items.pageSize>=data.items.total" @click="emit('page',page+1)">下一页事项</button></div></template>
      <small>上次成功 {{ lastSuccessAt }} · 历史完结记录从普通列表查看。</small>
    </template>
  </section>
</template>
<style scoped>
.items-panel{min-width:0;margin-top:20px;border-top:1px solid #dce6ea;padding-top:18px}.items-heading{display:flex;justify-content:space-between;gap:12px;align-items:center;flex-wrap:wrap}.items-heading h2{font-size:17px}.items-list{list-style:none;padding:0;display:grid;gap:12px}.items-list li{border:1px solid #dce6ea;border-radius:8px;padding:14px;overflow-wrap:anywhere;display:grid;gap:8px}.items-list time{font-size:12px;color:#637c87}.counts{display:flex;gap:12px;flex-wrap:wrap}
</style>
