<script setup lang="ts">
import { onMounted,onUnmounted,shallowRef } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useAccountSettings } from '../../composables/useAccountSettings'
import ProfileSettingsPanel from '../../components/account/ProfileSettingsPanel.vue'
import PasswordSettingsPanel from '../../components/account/PasswordSettingsPanel.vue'
const state=useAccountSettings(),tab=shallowRef('profile')
onMounted(state.load);onUnmounted(state.dispose)
onBeforeRouteLeave(()=>state.busy.value?false:!state.pending.value&&!state.changed.value&&!state.passwords.currentPassword&&!state.passwords.newPassword?true:window.confirm('离开会清除本次草稿与密码输入。结果待确认时请先核对原操作。是否离开？'))
</script>
<template><div class="page-heading"><h1>账号设置</h1><button class="secondary" :disabled="state.busy.value||!!state.pending.value" @click="state.load">重新读取</button></div><p v-if="state.message.value" role="status" class="notice">{{ state.message.value }}</p><p v-if="state.loading.value">正在读取账号…</p><section v-if="state.profile.value" class="card"><dl><dt>登录账号</dt><dd>{{state.profile.value.loginName}}</dd><dt>固定角色 / 资格</dt><dd>{{state.profile.value.role}} / {{state.profile.value.reviewStatus??'无需审核'}}</dd><dt>账号状态</dt><dd>{{state.profile.value.enabled?'启用':'停用'}}</dd></dl></section><div class="actions"><button class="secondary" @click="tab='profile'">个人资料</button><button class="secondary" @click="tab='password'">修改密码</button></div><ProfileSettingsPanel v-if="state.profile.value&&tab==='profile'" :state="state"/><PasswordSettingsPanel v-if="state.profile.value&&tab==='password'" :state="state"/><section v-if="state.pending.value" class="card"><p>结果待确认，请保留本页原输入和操作标识。密码操作不会自动重发；原会话失效也不代表已确认改密。</p><button class="secondary" :disabled="state.busy.value" @click="state.check">核对原操作</button></section><RouterLink v-if="state.passwordChanged.value" to="/login">返回登录（密码留空）</RouterLink></template>
