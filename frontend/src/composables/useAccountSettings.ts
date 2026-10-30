import { computed, reactive, shallowRef } from 'vue'
import { fetchProfile, type AccountProfileVo } from '../api/account'
import { businessMutation, feedback, fetchOperation } from '../api/business'
import { useAuthStore } from '../stores/auth'
export function useAccountSettings() {
  const auth=useAuthStore(),profile=shallowRef<AccountProfileVo|null>(null),form=reactive({name:'',phone:''}),passwords=reactive({currentPassword:'',newPassword:'',confirmNewPassword:''})
  const loading=shallowRef(false),busy=shallowRef(false),message=shallowRef(''),confirming=shallowRef(false),passwordChanged=shallowRef(false),pending=shallowRef<{operationId:string;operation:string;path:string;body:Record<string,unknown>}|null>(null)
  let generation=0,alive=true
  const canEdit=computed(()=>profile.value?.allowedActions.includes('EDIT_PROFILE')??false),changed=computed(()=>profile.value!==null&&(form.name.trim()!==profile.value.name||form.phone!==profile.value.phone))
  const valid=(epoch:number)=>alive&&auth.identityEpoch===epoch
  function cancelProfile(){if(pending.value||busy.value)return;if(profile.value)Object.assign(form,{name:profile.value.name,phone:profile.value.phone})}
  async function load(){if(busy.value||pending.value)return;const own=++generation,epoch=auth.identityEpoch;loading.value=true
    const r=await fetchProfile();if(!valid(epoch)||own!==generation)return;loading.value=false
    if(r.kind==='SUCCESS'){profile.value=r.data;auth.setConfirmedUser(r.data);cancelProfile()}else{message.value=feedback(r);profile.value=null}
  }
  function clearPasswords(){Object.assign(passwords,{currentPassword:'',newPassword:'',confirmNewPassword:''});confirming.value=false}
  async function send(path:string,body:Record<string,unknown>,operation:string){
    if(busy.value||pending.value||!profile.value||!auth.businessRouteGranted)return;const epoch=auth.identityEpoch;busy.value=true;message.value=''
    try{if(!await auth.ensureCsrf()||!valid(epoch))return
      pending.value={operationId:String(body.operationId),operation,path,body};const r=await businessMutation(path,path.endsWith('/profile')?'PATCH':'POST',body,'ACCOUNT',profile.value.id)
      if(!valid(epoch))return
      if(r.kind==='SUCCESS'){pending.value=null;if(operation==='PASSWORD_CHANGE'){clearPasswords();passwordChanged.value=true;message.value='密码已修改，原登录全部失效。请使用新密码重新登录。';auth.clearIdentity();auth.passwordChangedNotice=true}else{busy.value=false;await load();message.value='资料已保存。'}}
      else{message.value=feedback(r);if(r.kind!=='UNKNOWN')pending.value=null;if(r.kind==='HTTP_ERROR'&&r.response.code==='CSRF_INVALID')await auth.ensureCsrf(true)}
    }finally{busy.value=false}
  }
  async function saveProfile(){if(!canEdit.value||!changed.value||pending.value)return
    if(!form.name.trim()||[...form.name.trim()].length>100||!/^1[3-9][0-9]{9}$/.test(form.phone)){message.value='请输入1—100字姓名和大陆11位手机号';return}
    await send('/api/account/profile',{operationId:crypto.randomUUID(),version:profile.value!.version,name:form.name.trim(),phone:form.phone},'PROFILE_UPDATE')
  }
  function preparePassword(){if(busy.value||pending.value||!profile.value)return
    if(!passwords.currentPassword||[...passwords.newPassword].length<8||[...passwords.newPassword].length>32||!/[A-Za-z]/.test(passwords.newPassword)||!/[0-9]/.test(passwords.newPassword)){message.value='密码需8—32位且含英文字母和数字';return}
    if(passwords.newPassword!==passwords.confirmNewPassword){message.value='两次新密码不一致';return}confirming.value=true;message.value=''
  }
  function backPassword(){if(!busy.value&&!pending.value)confirming.value=false}
  async function changePassword(){if(!confirming.value||!profile.value)return;await send('/api/account/password',{operationId:crypto.randomUUID(),version:profile.value.version,...passwords},'PASSWORD_CHANGE')}
  async function check(){const original=pending.value;if(!original||busy.value)return;busy.value=true;const epoch=auth.identityEpoch
    try{const r=await fetchOperation(original.operationId,original.operation);if(!valid(epoch))return
      if(r.kind==='SUCCESS'&&r.data.kind==='SUCCEEDED'&&r.data.receipt.resourceId===profile.value?.id){pending.value=null;if(original.operation==='PASSWORD_CHANGE'){clearPasswords();passwordChanged.value=true;auth.clearIdentity();auth.passwordChangedNotice=true}else{busy.value=false;await load()}message.value='原操作已确认成功。'}else message.value=r.kind==='SUCCESS'?'原操作尚未确认；保留原输入与操作标识，不自动重发。':feedback(r)
    }finally{busy.value=false}
  }
  function dispose(){alive=false;generation++;clearPasswords();pending.value=null}
  return{profile,form,passwords,canEdit,changed,loading,busy,message,confirming,passwordChanged,pending,load,saveProfile,cancelProfile,preparePassword,backPassword,clearPasswords,changePassword,check,dispose}
}


