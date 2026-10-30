<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchRequest } from '../../api/repair-request'
import { useBusinessRead } from '../../composables/useBusinessRead'
import { useDraftStore } from '../../stores/drafts'
import { useAuthStore } from '../../stores/auth'
import RequestForm from './RequestForm.vue'
import { businessReturnTo } from '../../router/businessNavigation'
const route = useRoute(), router = useRouter(), auth = useAuthStore(), store = useDraftStore()
const { data, loading, message, stale, clear, load } = useBusinessRead(signal => fetchRequest(String(route.params.id), {signal}), () => store.clear(`SUPPLEMENT_REQUEST/${auth.currentUser?.id}/${route.params.id}`), { target: () => `supplement/${route.params.id}`, poll: true })
const returnTo = computed(() => businessReturnTo(route.query.returnTo, '/requests'))
watch(() => route.params.id, () => { clear(); void load() }, { immediate: true })
</script>
<template><RouterLink :to="{path:`/requests/${route.params.id}`,query:{returnTo}}">返回原申请</RouterLink><p v-if="loading" role="status">正在核对原申请…</p><p v-if="message" class="notice" role="alert">{{ message }} {{ stale ? '保留上次资料，当前可能过期。' : '' }} <button class="text-button" @click="load">重新核对</button></p><section v-if="data" class="card"><h1>{{ data.requestNo }} · 补充材料</h1><p v-if="!data.allowedActions.includes('SUPPLEMENT_REQUEST')" class="notice">原申请状态已变化，目前不能重提；已有编辑保留，先核对最新记录。</p><p v-for="event in data.events.filter(e => e.type === 'RETURNED')" :key="event.id" class="notice">退回意见：{{ event.reason }}</p><button class="secondary" :disabled="loading" @click="load">刷新只读状态与意见</button><RequestForm :detail="data" @saved="receipt => router.push({path:`/requests/${receipt.resourceId}`,query:{returnTo}})" /></section></template>
