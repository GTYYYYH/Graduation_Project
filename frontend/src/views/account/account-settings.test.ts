import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../../stores/auth'
import { useAccountSettings } from '../../composables/useAccountSettings'
const profile = { id: '12', loginName: 'synthetic', name: '原姓名', phone: '13800000001', role: 'REPORTER' as const, enabled: true, reviewStatus: null, version: 0, capabilities: { canBrowseCampus: true, canCreateRequest: true, canWorkOnTasks: false, canManage: false }, allowedActions: ['EDIT_PROFILE','CHANGE_PASSWORD'] }
const response = (data: unknown) => new Response(JSON.stringify({ code: 'OK',message: '成功',data,requestId: 'local-test' }), { status: 200,headers: { 'Content-Type':'application/json' } })
beforeEach(() => { setActivePinia(createPinia());const auth=useAuthStore();auth.setConfirmedUser(profile);auth.csrf={token:'x'.repeat(64),headerName:'X-CSRF-TOKEN'} })
afterEach(() => vi.unstubAllGlobals())
it('unchanged_profile_does_not_submit', async () => {
  let saveCallsForUnchangedProfile=0
  vi.stubGlobal('fetch',vi.fn(async (_url,init) => { if(init?.method==='PATCH')saveCallsForUnchangedProfile++;return response(profile) }))
  const settings=useAccountSettings();await settings.load();await settings.saveProfile()
  expect(saveCallsForUnchangedProfile).toBe(0)
  settings.form.name='草稿';settings.cancelProfile();expect(settings.form.name).toBe('原姓名')
})
it('profile_save_updates_header_and_preserves_unknown_original_input',async () => {
  let saved=false
  vi.stubGlobal('fetch',vi.fn(async (_url,init) => { if(init?.method==='PATCH'){saved=true; const body=JSON.parse(init.body as string);return response({operationId:body.operationId,resourceType:'ACCOUNT',resourceId:'12',resultVersion:1,resultRound:null,committedAt:'2026-10-25T00:00:00.000Z',relatedResourceType:null,relatedResourceId:null})}return response(saved?{...profile,name:'新姓名',version:1}:profile) }))
  const settings=useAccountSettings();await settings.load();settings.form.name='新姓名';await settings.saveProfile();expect(useAuthStore().currentUser?.name).toBe('新姓名')
  vi.stubGlobal('fetch',vi.fn().mockRejectedValue(new TypeError('network')))
  settings.form.name='未确认';await settings.saveProfile();const operation=settings.pending.value?.operationId
  await settings.saveProfile();expect(settings.pending.value?.operationId).toBe(operation);expect(settings.form.name).toBe('未确认');expect(settings.message.value).toContain('确认')
})
it('password_back_keeps_inputs_success_clears_secrets_and_identity',async () => {
  vi.stubGlobal('fetch',vi.fn(async (_url,init) => {if(init?.method==='POST'){const body=JSON.parse(init.body as string);return response({operationId:body.operationId,resourceType:'ACCOUNT',resourceId:'12',resultVersion:1,resultRound:null,committedAt:'2026-10-25T00:00:00.000Z',relatedResourceType:null,relatedResourceId:null})}return response(profile)}))
  const settings=useAccountSettings();await settings.load();Object.assign(settings.passwords,{currentPassword:'Synthetic1!',newPassword:'Synthetic2!',confirmNewPassword:'Synthetic2!'})
  settings.preparePassword();settings.backPassword();expect(settings.passwords.newPassword).toBe('Synthetic2!')
  settings.preparePassword();await settings.changePassword();expect(settings.passwords.currentPassword).toBe('');expect(settings.passwords.newPassword).toBe('');expect(useAuthStore().currentUser).toBeNull();expect(settings.passwordChanged.value).toBe(true);expect(useAuthStore().passwordChangedNotice).toBe(true)
})
it('csrf_rotation_refreshes_token_without_automatically_resending_profile',async()=>{
  let writes=0,csrfReads=0
  vi.stubGlobal('fetch',vi.fn(async(url,init)=>{if(String(url).endsWith('/csrf')){csrfReads++;return response({token:'y'.repeat(64),headerName:'X-CSRF-TOKEN'})}if(init?.method==='PATCH'){writes++;return new Response(JSON.stringify({code:'CSRF_INVALID',message:'校验失效',data:null,requestId:'test'}),{status:403,headers:{'Content-Type':'application/json'}})}return response(profile)}))
  const settings=useAccountSettings();await settings.load();settings.form.name='输入保留';await settings.saveProfile();expect(csrfReads).toBe(1);expect(writes).toBe(1);expect(settings.form.name).toBe('输入保留');expect(useAuthStore().csrf?.token).toBe('y'.repeat(64))
})
