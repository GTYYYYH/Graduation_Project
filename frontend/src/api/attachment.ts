import { apiMutation } from './client'
import { isId } from './account'
import { object, isInstant } from './business'
import type { MutationResult } from './common'
import type { ImageSelection, UploadContext, UploadAttachment } from '../composables/useImageSelection'
export interface AttachmentVo extends UploadAttachment { purpose: 'REQUEST_FAULT' | 'REQUEST_SUPPLEMENT' | 'REPAIR_VOUCHER'; state: 'TEMPORARY' | 'BOUND' | 'DELETING' | 'DELETED' }
export function isAttachment(value: unknown): value is AttachmentVo {
  return object(value) && isId(value.id) && typeof value.originalName === 'string' && ['image/jpeg', 'image/png'].includes(String(value.mediaType)) && typeof value.byteSize === 'number' && Number.isInteger(value.byteSize) && value.byteSize > 0 && value.byteSize <= 5_000_000
    && ['REQUEST_FAULT', 'REQUEST_SUPPLEMENT', 'REPAIR_VOUCHER'].includes(String(value.purpose)) && ['TEMPORARY', 'BOUND', 'DELETING', 'DELETED'].includes(String(value.state)) && isInstant(value.uploadedAt)
    && (value.expiresAt === null || isInstant(value.expiresAt)) && value.contentUrl === `/api/attachments/${value.id}/content`
}
export async function uploadTemporary(selection: ImageSelection, context: UploadContext): Promise<MutationResult<AttachmentVo>> {
  const form = new FormData(); form.append('operationId', selection.uploadOperationId); form.append('purpose', context.purpose); form.append('file', selection.file)
  if (context.batchId) form.append('batchId', context.batchId)
  if (context.repairRequestId) form.append('repairRequestId', context.repairRequestId)
  if (context.workOrderId) form.append('workOrderId', context.workOrderId)
  if (context.round) form.append('round', String(context.round))
  const result = await apiMutation<unknown>('/api/attachments/temp', 'POST', form)
  if (result.kind !== 'SUCCESS') return result
  return isAttachment(result.data) && result.data.state === 'TEMPORARY' ? { ...result, data: result.data } : { kind: 'UNKNOWN', operationId: selection.uploadOperationId, message: '上传回执异常，请保留原图片与上传标识核对重试' }
}
export const cancelTemporary = (id: string, operationId: string) => apiMutation<null>(`/api/attachments/${encodeURIComponent(id)}/temp`, 'DELETE', undefined, { operationId })
