import { computed } from 'vue'
import type { Ref } from 'vue'
import type { RendererState } from './types'
export interface SceneRetry { configVersion: string; kind: 'OVERVIEW' | 'BUILDING' | 'FLOOR' | 'LOCATION'; code: string; enter: boolean }
export interface SavedSceneRetry extends SceneRetry { sessionScope: string; expiresAt: number }
type RetryStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>
const retryKey = 'campus:scene-retry'
export async function makeSceneSessionScope(userId: string | undefined, expiresAt: string | null): Promise<string | null> {
  if (!userId || !expiresAt || !Number.isFinite(Date.parse(expiresAt))) return null
  // 公开ID/固定到期时间的摘要只用来隔离UI恢复，不包含登录凭据。
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(`${userId}|${Date.parse(expiresAt)}`))
  return [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, '0')).join('')
}
export function clearSceneRetry(storage: RetryStorage): void { storage.removeItem(retryKey) }
export function saveSceneRetry(storage: RetryStorage, intent: SceneRetry, sessionScope: string, now = Date.now()): void {
  // 仅几何code/观察意图和失效边界；不保存账号ID、Cookie/Token或业务对象。
  storage.setItem(retryKey, JSON.stringify({ configVersion: intent.configVersion, kind: intent.kind, code: intent.code, enter: intent.enter, sessionScope, expiresAt: now + 60000 }))
}
export function isCurrentSceneRetry(intent: SavedSceneRetry | null, sessionScope: string, now = Date.now()): boolean {
  return !!intent && intent.sessionScope === sessionScope && intent.expiresAt > now && intent.expiresAt <= now + 60000
}
export function takeSceneRetry(storage: RetryStorage, now = Date.now()): SavedSceneRetry | null {
  const raw = storage.getItem(retryKey); storage.removeItem(retryKey)
  if (!raw) return null
  try {
    const value = JSON.parse(raw)
    if (!value || typeof value.configVersion !== 'string' || typeof value.code !== 'string' || typeof value.enter !== 'boolean'
      || !['OVERVIEW', 'BUILDING', 'FLOOR', 'LOCATION'].includes(value.kind) || typeof value.expiresAt !== 'number'
      || !Number.isFinite(value.expiresAt) || value.expiresAt <= now || value.expiresAt > now + 60000 || (value.enter && value.kind !== 'LOCATION')
      || typeof value.sessionScope !== 'string' || !/^[a-f0-9]{64}$/.test(value.sessionScope)) return null
    return { configVersion: value.configVersion, kind: value.kind, code: value.code, enter: value.enter, sessionScope: value.sessionScope, expiresAt: value.expiresAt }
  } catch { return null }
}
interface InteriorIntent { revision: number; locationId: string }
export class ObservationIntent {
  private revision = 0
  private interiorId: string | null = null
  select(): void { this.revision++; this.interiorId = null }
  enter(id: string): void { this.revision++; this.interiorId = id }
  capture(): InteriorIntent | null { return this.interiorId ? { revision: this.revision, locationId: this.interiorId } : null }
  canResume(intent: InteriorIntent | null, id: string): boolean {
    return !!intent && intent.revision === this.revision && intent.locationId === this.interiorId && intent.locationId === id
  }
}
export function useEntryPermission(ready: Ref<boolean>, error: Ref<string>, state: Ref<RendererState>, locationId: Ref<string>) {
  return computed(() => ready.value && !error.value && !state.value.error && !state.value.loading && state.value.level === 'DOOR' && state.value.locationId === locationId.value)
}
