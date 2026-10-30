import { beforeEach,afterEach,expect,it,vi } from 'vitest'
import { createPinia,setActivePinia } from 'pinia'
import { useAuthStore } from '../../stores/auth'
import { usePasswordReset } from '../../composables/usePasswordReset'
beforeEach(()=>{setActivePinia(createPinia());const auth=useAuthStore();auth.setConfirmedUser({id:'1',loginName:'syntheticadmin',name:'管理员',phone:'13800000000',role:'ADMIN',reviewStatus:null,enabled:true,version:0,capabilities:{canBrowseCampus:true,canCreateRequest:false,canWorkOnTasks:false,canManage:true}});auth.csrf={token:'x'.repeat(64),headerName:'X-CSRF-TOKEN'}})
afterEach(()=>vi.unstubAllGlobals())
it('full_personnel_record_sends_only_reset_dto_fields',async()=>{
  let sent:Record<string,unknown>|undefined
  vi.stubGlobal('fetch',vi.fn(async(_url,init)=>{
    sent=JSON.parse(init?.body as string)
    return new Response(JSON.stringify({code:'VALIDATION_FAILED',message:'请求格式不正确',data:null,requestId:'contract-test'}),{status:400,headers:{'Content-Type':'application/json'}})
  }))
  // The real page passes its entire PersonnelVo, not the minimal object used by earlier tests.
  const person={...useAuthStore().currentUser!,id:'12',version:3,allowedActions:['RESET_PASSWORD'],reviewAttempt:0,counts:{role:'REPORTER',pendingRequests:0,unfinishedOrders:0,completedOrders:0}}
  const reset=usePasswordReset(()=>person)
  await reset.submit()
  expect(sent).toEqual({operationId:expect.any(String),version:3})
  expect(reset.pending.value).toBeNull()
  expect(reset.secret.value).toBe('')
})
it('copy_failure_does_not_reset_again',async()=>{
  let resetRequestsAfterCopyFailure=0
  vi.stubGlobal('fetch',vi.fn(async(_url,init)=>{resetRequestsAfterCopyFailure++;const body=JSON.parse(init?.body as string);return new Response(JSON.stringify({code:'OK',message:'成功',requestId:'test',data:{kind:'NEW_SECRET',receipt:{operationId:body.operationId,resourceType:'ACCOUNT',resourceId:'12',resultVersion:1,resultRound:null,committedAt:'2026-10-25T00:00:00.000Z',relatedResourceType:null,relatedResourceId:null},account:{id:'12',name:'合成'},loginName:'synthetic',newPassword:'Synthetic2!'}}),{status:200,headers:{'Content-Type':'application/json'}})}))
  const reset=usePasswordReset(()=>({id:'12',version:0}));await reset.submit();expect(reset.visible.value).toBe(false)
  await reset.copy(async()=>{throw new Error('clipboard denied')});expect(resetRequestsAfterCopyFailure).toBe(1);expect(reset.message.value).toContain('手动');expect(reset.secret.value).toBe('Synthetic2!');reset.clear();expect(reset.secret.value).toBe('')
})
it('unknown_never_resubmits_and_preserves_original_operation',async()=>{
  const fetcher=vi.fn().mockRejectedValue(new TypeError('network'));vi.stubGlobal('fetch',fetcher)
  const reset=usePasswordReset(()=>({id:'12',version:0}));await reset.submit();const operation=reset.pending.value?.operationId;await reset.submit();expect(fetcher).toHaveBeenCalledTimes(1);expect(reset.pending.value?.operationId).toBe(operation);expect(reset.secret.value).toBe('')
})
it('csrf_rotation_refreshes_without_a_second_reset',async()=>{
  let writes=0,reads=0
  vi.stubGlobal('fetch',vi.fn(async(url)=>{if(String(url).endsWith('/csrf')){reads++;return new Response(JSON.stringify({code:'OK',message:'成功',requestId:'test',data:{token:'y'.repeat(64),headerName:'X-CSRF-TOKEN'}}),{status:200,headers:{'Content-Type':'application/json'}})}writes++;return new Response(JSON.stringify({code:'CSRF_INVALID',message:'校验失效',data:null,requestId:'test'}),{status:403,headers:{'Content-Type':'application/json'}})}))
  const reset=usePasswordReset(()=>({id:'12',version:0}));await reset.submit();expect(reads).toBe(1);expect(writes).toBe(1);expect(reset.pending.value).toBeNull();expect(reset.secret.value).toBe('')
})
