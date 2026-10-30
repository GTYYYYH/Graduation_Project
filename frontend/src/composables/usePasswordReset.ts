import { shallowRef } from 'vue'
import { resetAccountPassword } from '../api/account'
import { feedback,fetchOperation } from '../api/business'
import { useAuthStore } from '../stores/auth'
export function usePasswordReset(target:()=>{id:string;version:number}|null){
  const auth=useAuthStore(),visible=shallowRef(false),secret=shallowRef(''),message=shallowRef(''),busy=shallowRef(false),committed=shallowRef(false),pending=shallowRef<{operationId:string;id:string;version:number}|null>(null)
  let alive=true
  async function submit(){const account=target();if(!account||busy.value||pending.value||committed.value||!auth.businessRouteGranted)return;const epoch=auth.identityEpoch;busy.value=true
    try{if(!await auth.ensureCsrf()||epoch!==auth.identityEpoch||!alive)return
      const original={id:account.id,version:account.version,operationId:crypto.randomUUID()};pending.value=original
      const r=await resetAccountPassword(original.id,{version:original.version,operationId:original.operationId});if(epoch!==auth.identityEpoch||!alive)return
      if(r.kind==='SUCCESS'){committed.value=true;pending.value=null;secret.value=r.data.newPassword??'';visible.value=false;message.value=r.data.kind==='NEW_SECRET'?'密码已重置，全部旧登录失效。请妥善接收本次密码。':'原重置已提交；本次不会再次返回明文。未保留密码时须明确发起新重置。'}else{message.value=feedback(r);if(r.kind!=='UNKNOWN')pending.value=null;if(r.kind==='HTTP_ERROR'&&r.response.code==='CSRF_INVALID')await auth.ensureCsrf(true)}
    }finally{busy.value=false}
  }
  async function check(){const original=pending.value;if(!original||busy.value)return;const epoch=auth.identityEpoch;busy.value=true
    try{const r=await fetchOperation(original.operationId,'PASSWORD_RESET');if(epoch!==auth.identityEpoch||!alive)return
      if(r.kind==='SUCCESS'&&r.data.kind==='SUCCEEDED'&&r.data.receipt.resourceId===original.id){committed.value=true;pending.value=null;message.value='原重置已提交，无法再次获取此次明文。请返回详情核对，确需新密码时明确再次重置。'}else message.value=r.kind==='SUCCESS'?'原操作尚未确认，不自动重发。':feedback(r)
    }finally{busy.value=false}
  }
  async function copy(write:(value:string)=>Promise<void>){if(!secret.value)return;try{await write(secret.value);message.value='已复制，请线下交给账号本人。'}catch{message.value='自动复制不可用；请显示并手动选择复制本次密码。重置已成功，不必再次重置。';visible.value=true}}
  function clear(){secret.value='';visible.value=false;pending.value=null}
  function dispose(){alive=false;clear()}
  return{visible,secret,message,pending,busy,committed,submit,check,copy,clear,dispose}
}
