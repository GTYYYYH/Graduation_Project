<script setup lang="ts">
import { computed } from 'vue'
import type { PersonnelVo } from '../../api/account'
import { useBusinessAction } from '../../composables/useBusinessAction'
import { useBusinessConfirmation } from '../../composables/useBusinessConfirmation'
import InlineConfirmation from '../common/InlineConfirmation.vue'
const props=defineProps<{person:PersonnelVo;blocked?:boolean}>(),emit=defineEmits<{changed:[]}>()
const confirmation=useBusinessConfirmation(),action=useBusinessAction('ACCOUNT_STATUS',()=>props.person.id,()=>{emit('changed')})
const canStatus=computed(()=>props.person.allowedActions.includes(props.person.enabled?'DISABLE_ACCOUNT':'ENABLE_ACCOUNT'))
async function status(){const p=props.person;if(!canStatus.value||props.blocked||action.pending.value||action.busy.value)return
  if(!await confirmation.ask(`${p.enabled?'停用':'启用'} ${p.name}（${p.loginName}）？${p.enabled?'将核对全部未完结业务；成功后全部旧登录失效。':'只恢复登录，不自动通过审核，也不恢复旧登录。'}`))return
  if(props.blocked||props.person!==p)return
  const operationId=crypto.randomUUID();await action.submit({operationId,operation:p.enabled?'ACCOUNT_DISABLE':'ACCOUNT_ENABLE',path:`/api/admin/accounts/${p.id}/${p.enabled?'disable':'enable'}`,method:'POST',payload:{operationId,version:p.version},resourceType:'ACCOUNT',resourceId:p.id,phase:'SENDING'})
}
</script>
<template><InlineConfirmation :message="confirmation.message.value" @answer="confirmation.answer"/><button v-if="canStatus" class="secondary" :disabled="blocked||action.busy.value||!!action.pending.value" @click="status">{{ person.enabled?'停用账号':'启用账号' }}</button><p v-if="action.message.value" class="notice" role="status">{{action.message.value}}</p><p v-if="action.pending.value" class="notice">启停用结果待确认。<button :disabled="action.busy.value" @click="action.check">核对原操作</button></p></template>

