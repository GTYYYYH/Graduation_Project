<script setup lang="ts">
import { shallowRef } from 'vue'
import { addImageFiles, disposeImageSelection, ensureTemporaryUploaded } from '../../composables/useImageSelection'
import type { ImageSelection, UploadContext } from '../../composables/useImageSelection'
import { uploadTemporary, cancelTemporary } from '../../api/attachment'
import { useAuthStore } from '../../stores/auth'
import { feedback } from '../../api/business'
const model = defineModel<ImageSelection[]>({ required: true })
const props = defineProps<{ context: UploadContext; disabled?: boolean }>(), auth = useAuthStore()
const message = shallowRef(''), busy = shallowRef(false), cancelOperations = new Map<string, string>()
function choose(event: Event) { const input = event.target as HTMLInputElement; if (props.disabled || busy.value) return; const result = addImageFiles(model.value, Array.from(input.files ?? [])); model.value = result.selection; message.value = result.errors.join('；'); input.value = '' }
async function upload() {
  if (props.disabled || busy.value || !model.value.length) return
  const epoch = auth.identityEpoch, selection = model.value, scope = JSON.stringify(props.context); busy.value = true
  try { if (!await auth.ensureCsrf() || epoch !== auth.identityEpoch) return; const result = await ensureTemporaryUploaded(selection, props.context, uploadTemporary, () => epoch === auth.identityEpoch && scope === JSON.stringify(props.context)); if (epoch === auth.identityEpoch && scope === JSON.stringify(props.context)) message.value = result.kind === 'SUCCESS' ? '所选图片已临时上传，提交申请后才正式关联。' : result.message }
  finally { busy.value = false }
}
async function remove(index: number) {
  if (props.disabled || busy.value) return; const item = model.value[index]; if (!item) return
  const epoch = auth.identityEpoch; busy.value = true
  try {
    if (item.temporaryId) {
      if (!await auth.ensureCsrf() || epoch !== auth.identityEpoch) return
      const operation = cancelOperations.get(item.uploadOperationId) ?? crypto.randomUUID(); cancelOperations.set(item.uploadOperationId, operation)
      const result = await cancelTemporary(item.temporaryId, operation)
      if (epoch !== auth.identityEpoch) return
      if (result.kind !== 'SUCCESS') { message.value = feedback(result); return }
    }
    if (epoch !== auth.identityEpoch || !model.value.includes(item)) return
    disposeImageSelection([item]); model.value = model.value.filter(value => value !== item); message.value = '已移除本次新增图片。'
  } finally { busy.value = false }
}
</script>
<template><section class="image-picker" :aria-label="context.purpose === 'REPAIR_VOUCHER' ? '本轮新增维修凭证' : '本次新增故障图片'"><label>选择{{ context.purpose === 'REPAIR_VOUCHER' ? '本轮维修凭证' : '故障图片' }}（每次1—5张，JPG/JPEG/PNG，单张最多5,000,000字节）<input type="file" accept="image/jpeg,image/png,.jpg,.jpeg,.png" multiple :disabled="disabled || busy" @change="choose"></label><p class="muted">选择和切换页面只在本地预览。可主动上传，或在业务提交时上传；历史图片保留。</p><p v-if="message" class="notice" role="status">{{ message }}</p><ul class="image-list"><li v-for="(item, index) in model" :key="item.uploadOperationId"><img :src="item.previewUrl" :alt="`本地预览：${item.file.name}`"><p>{{ item.file.name }}</p><small>{{ item.temporaryId ? '已临时上传，尚未正式绑定' : '本地选择，尚未确认上传' }}</small><button type="button" class="secondary" :disabled="disabled || busy" @click="remove(index)">移除本次图片</button></li></ul><button v-if="model.length" type="button" class="secondary" :disabled="disabled || busy" @click="upload">{{ busy ? '图片处理中…' : '上传所选图片' }}</button></section></template>
<style scoped>.image-picker { border-top: 1px solid #dce6ea; padding-top: 20px; }.image-picker p { font-size: 13px; }.image-list { list-style: none; display: grid; grid-template-columns: repeat(auto-fit,minmax(140px,1fr)); padding: 0; gap: 14px; }.image-list li { border: 1px solid #dce6ea; padding: 12px; border-radius: 7px; min-width: 0; }.image-list img { max-width: 100%; height: 130px; object-fit: contain; }.image-list p { overflow-wrap: anywhere; }.image-list small { display: block; margin-bottom: 10px; }.image-list button { width: 100%; }@media(max-width:480px) { .image-list { grid-template-columns: 1fr; } }</style>
