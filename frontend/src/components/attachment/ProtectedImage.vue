<script setup lang="ts">
import { shallowRef, watch, onUnmounted } from 'vue'
import { apiImage } from '../../api/client'
import { useAuthStore } from '../../stores/auth'
import { feedback } from '../../api/business'
import type { AttachmentVo } from '../../api/attachment'
const props = defineProps<{ attachment: AttachmentVo }>(), auth = useAuthStore()
const url = shallowRef(''), message = shallowRef(''), loading = shallowRef(false)
let generation = 0, alive = true, controller: AbortController | null = null
function clear() { if (url.value) URL.revokeObjectURL(url.value); url.value = '' }
async function load() {
  const own = ++generation, epoch = auth.identityEpoch; controller?.abort(); controller = new AbortController(); clear(); loading.value = true; message.value = ''
  const result = await apiImage(props.attachment.contentUrl, { signal: controller.signal })
  if (!alive || own !== generation || epoch !== auth.identityEpoch) return
  loading.value = false
  if (result.kind === 'SUCCESS') url.value = URL.createObjectURL(result.data)
  else message.value = feedback(result)
}
function invalidImage() { clear(); message.value = '图片无法显示，请重试并核对记录权限' }
watch(() => props.attachment.id, load, { immediate: true })
watch(() => auth.identityEpoch, () => { generation++; controller?.abort(); clear(); message.value = ''; loading.value = false }, { flush: 'sync' })
onUnmounted(() => { alive = false; generation++; controller?.abort(); clear() })
</script>
<template><figure class="protected-image"><img v-if="url" :src="url" :alt="attachment.originalName" @error="invalidImage"><p v-if="loading" role="status">正在读取图片…</p><p v-if="message" class="notice" role="alert">{{ message }} <button type="button" class="text-button" @click="load">重试图片</button></p><figcaption>{{ attachment.originalName }} · 历史图片只读</figcaption></figure></template>
<style scoped>.protected-image { margin: 12px 0; min-width: 0; }.protected-image img { display: block; max-width: 100%; max-height: 320px; border-radius: 7px; }.protected-image figcaption { overflow-wrap: anywhere; color: #617b85; font-size: 12px; margin-top: 8px; }</style>
