<script setup lang="ts">
import {computed,onUnmounted,shallowRef,watch} from 'vue'
import {useRoute,useRouter,onBeforeRouteLeave} from 'vue-router'
import {fetchPersonnelHistory,type PersonnelVo} from '../../api/account'
import {feedback} from '../../api/business'
import {usePasswordReset} from '../../composables/usePasswordReset'
import {businessReturnTo} from '../../router/businessNavigation'
import {useAuthStore} from '../../stores/auth'
const route=useRoute(),router=useRouter(),auth=useAuthStore(),person=shallowRef<PersonnelVo|null>(null),message=shallowRef(''),confirming=shallowRef(false)
const back=computed(()=>businessReturnTo(route.query.detailReturn,`/admin/personnel/${route.params.id}`)),state=usePasswordReset(()=>person.value)
let generation=0
watch(()=>route.params.id,async()=>{const own=++generation,epoch=auth.identityEpoch;person.value=null;state.clear();const r=await fetchPersonnelHistory(String(route.params.id));if(own!==generation||epoch!==auth.identityEpoch)return;if(r.kind==='SUCCESS'&&r.data.person.allowedActions.includes('RESET_PASSWORD'))person.value=r.data.person;else message.value=r.kind==='SUCCESS'?'此账号不允许通过网页重置密码。':feedback(r)},{immediate:true})
onBeforeRouteLeave(()=>state.busy.value?false:!state.secret.value&&!state.pending.value?true:window.confirm('确认离开？本页随机密码将立即清除，此次已提交的重置不会撤销；待确认操作不会自动重发。'))
onUnmounted(()=>{generation++;state.dispose()})
async function close(){if(state.busy.value)return;if(!state.secret.value&&!state.pending.value||window.confirm('确认已妥善接收？关闭后不能再取回此次密码。')){state.clear();await router.push(back.value)}}
function copyPassword(){return state.copy(value=>navigator.clipboard.writeText(value))}
</script>
<template><div class="page-heading"><h1>重置普通账号密码</h1><button class="secondary" @click="close">返回人员详情</button></div><p v-if="message" class="notice">{{message}}</p><section v-if="person" class="card"><dl><dt>登录账号 / 姓名</dt><dd>{{person.loginName}} / {{person.name}}</dd><dt>角色 / 状态</dt><dd>{{person.role}} / {{person.enabled?'启用':'停用'}}</dd></dl><p>随机生成符合规则的新密码，全部旧登录失效。重置不改变资格与启停用状态。请线下告知本人。</p><p v-if="state.message.value" class="notice" role="status">{{state.message.value}}</p><template v-if="!state.committed.value&&!state.pending.value"><button v-if="!confirming" class="primary" @click="confirming=true">核对重置对象与影响</button><div v-else class="notice"><p>确认重置 {{person.loginName}}（{{person.name}}）？</p><button class="primary" :disabled="state.busy.value" @click="state.submit">确认随机重置</button><button class="secondary" :disabled="state.busy.value" @click="confirming=false">取消</button></div></template><div v-if="state.secret.value"><label>本次一次性新密码<input :value="state.secret.value" :type="state.visible.value?'text':'password'" autocomplete="off" readonly @focus="($event.target as HTMLInputElement).select()"></label><div class="actions"><button class="secondary" :aria-pressed="state.visible.value" @click="state.visible.value=!state.visible.value">{{state.visible.value?'隐藏':'显示'}}</button><button class="secondary" @click="copyPassword">复制本次密码</button><button class="primary" @click="close">关闭并清除</button></div></div><div v-if="state.pending.value" class="notice"><p>结果待确认，不自动再次重置。请先核对原操作。</p><button class="secondary" :disabled="state.busy.value" @click="state.check">核对原重置</button></div><p v-if="state.committed.value&&!state.secret.value">此次明文不可再取回。需要新密码时请返回详情，明确发起另一次重置；新重置将使上次密码失效。</p></section></template>
