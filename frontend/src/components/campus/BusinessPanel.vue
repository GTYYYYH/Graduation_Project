<script setup lang="ts">
import { shallowRef, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import type { CatalogVo } from '../../api/account'
import type { BusinessReceipt } from '../../api/common'
import RequestForm from '../../views/request/RequestForm.vue'
const props = defineProps<{ locationId: string; catalog: CatalogVo; returnTo?: string }>(), auth = useAuthStore(), open = shallowRef(false), saved = shallowRef<BusinessReceipt | null>(null)
watch(() => props.locationId, () => { saved.value = null })
watch(() => auth.identityEpoch, () => { open.value = false; saved.value = null })
</script>
<template><section v-if="catalog.locations.some(l => l.id === locationId && l.canCreateRequest)" class="business-panel" aria-label="当前位置报修入口"><button class="primary full" type="button" @click="open = !open">{{ open ? '收起报修表单（保留同次草稿）' : '在此位置新建报修' }}</button><RouterLink class="secondary link-button" :to="{ path: '/requests/new', query: { locationId, returnTo } }">用普通页面报修此位置</RouterLink><p v-if="saved" class="notice success" role="status">申请已提交，进入待审核。 <RouterLink :to="{path:`/requests/${saved.resourceId}`,query:{returnTo}}">查看真实申请</RouterLink></p><RequestForm v-if="open && !saved" :initial-location-id="locationId" :catalog="catalog" @saved="receipt => saved = receipt" /></section></template>
<style scoped>.business-panel { margin-top: 20px; min-width: 0; }.business-panel :deep(.position-fields) { grid-template-columns: 1fr; }.business-panel :deep(.form-grid) { grid-template-columns: 1fr; }.business-panel :deep(.request-form) { margin-top: 18px; }</style>
