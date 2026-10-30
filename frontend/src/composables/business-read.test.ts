import { afterEach, expect, it } from 'vitest'
import { effectScope } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../stores/auth'
import { useBusinessRead } from './useBusinessRead'

const scopes: ReturnType<typeof effectScope>[] = []
afterEach(() => scopes.splice(0).forEach(scope => scope.stop()))
it('equivalent_poll_preserves_confirmation_and_upload_identity_but_changed_actions_replace_it', async () => {
  setActivePinia(createPinia())
  useAuthStore().setConfirmedUser({ id:'10',loginName:'owner',name:'合成用户',phone:'13800000000',role:'REPORTER',enabled:true,reviewStatus:null,version:0,capabilities:{canBrowseCampus:true,canCreateRequest:true,canManage:false,canWorkOnTasks:false} })
  let value = { id:'20', version:1, queriedAt:'2026-10-18T13:00:00Z', allowedActions:['ACCEPT'] }
  const scope=effectScope(); scopes.push(scope)
  const state=scope.run(() => useBusinessRead(async () => ({kind:'SUCCESS', data:{...value, allowedActions:[...value.allowedActions]},requestId:'controlled'})))!
  await state.load(); const confirmed=state.data.value
  value={...value, queriedAt:'2026-10-18T13:00:10Z'}
  await state.load(); expect(state.data.value).toBe(confirmed)
  expect(state.data.value?.queriedAt).toBe(value.queriedAt)
  value={...value, allowedActions:[], queriedAt:'2026-10-18T13:00:20Z'}
  await state.load(); expect(state.data.value).not.toBe(confirmed)
  const previous=state.data.value; state.clear(); await state.load(); expect(state.data.value).not.toBe(previous)
})
