import type { MutationResult } from '../api/common'
import { markRaw } from 'vue'
export interface ImageSelection { file: File; previewUrl: string; temporaryId: string | null; uploadOperationId: string; uploadContext?: UploadContext; expiresAt?: string }
export interface UploadContext { purpose: 'REQUEST_FAULT' | 'REQUEST_SUPPLEMENT' | 'REPAIR_VOUCHER'; batchId?: string; repairRequestId?: string; workOrderId?: string; round?: number }
export interface UploadAttachment { id: string; originalName: string; mediaType: 'image/jpeg' | 'image/png'; byteSize: number; purpose: string; state: string; uploadedAt: string; expiresAt: string | null; contentUrl: string }
type Urls = Pick<typeof URL, 'createObjectURL' | 'revokeObjectURL'>
export type Upload = (selection: ImageSelection, context: UploadContext) => Promise<MutationResult<UploadAttachment>>
export function addImageFiles(existing: ImageSelection[], files: File[], urls: Urls = URL) {
  const selection = [...existing], errors: string[] = []
  for (const file of files) {
    const name = file.name.toLowerCase(), mime = name.endsWith('.png') ? 'image/png' : /\.jpe?g$/.test(name) ? 'image/jpeg' : null
    if (!mime || file.type !== mime) { errors.push(`${file.name}：仅支持格式与类型一致的 JPG/JPEG/PNG`); continue }
    if (file.size < 1 || file.size > 5_000_000) { errors.push(`${file.name}：大小应为 1—5,000,000 字节`); continue }
    if (selection.length >= 5) { errors.push('每次新增最多5张图片'); continue }
    selection.push({ file: markRaw(file), previewUrl: urls.createObjectURL(file), temporaryId: null, uploadOperationId: crypto.randomUUID() })
  }
  return { selection, errors }
}
export function disposeImageSelection(selection: ImageSelection[], urls: Pick<typeof URL, 'revokeObjectURL'> = URL) { for (const item of selection) urls.revokeObjectURL(item.previewUrl) }
function contextKey(context: UploadContext) { return `${context.purpose}/${context.batchId ?? ''}/${context.repairRequestId ?? ''}/${context.workOrderId ?? ''}/${context.round ?? ''}` }
export async function ensureTemporaryUploaded(selection: ImageSelection[], context: UploadContext, upload: Upload, isCurrent: () => boolean = () => true): Promise<{ kind: 'SUCCESS'; ids: string[] } | { kind: 'FAILED'; message: string }> {
  for (const item of selection) {
    if (!isCurrent()) return { kind: 'FAILED', message: '身份或表单已变化，请重新核对' }
    if (item.uploadContext && contextKey(item.uploadContext) !== contextKey(context)) return { kind: 'FAILED', message: '图片已属于另一上传目标，请返回原草稿核对' }
    if (item.temporaryId) continue
    item.uploadContext = { ...context }
    const result = await upload(item, context)
    if (!isCurrent()) return { kind: 'FAILED', message: '身份或表单已变化，请重新核对' }
    if (result.kind !== 'SUCCESS') return { kind: 'FAILED', message: result.kind === 'HTTP_ERROR' ? result.response.message : result.message }
    item.temporaryId = result.data.id
    if (result.data.expiresAt) item.expiresAt = result.data.expiresAt
  }
  return { kind: 'SUCCESS', ids: selection.flatMap(item => item.temporaryId ? [item.temporaryId] : []) }
}
export function resetExpiredSelection(selection: ImageSelection[]) {
  for (const item of selection) { item.temporaryId = null; item.uploadOperationId = crypto.randomUUID(); delete item.uploadContext; delete item.expiresAt }
}
