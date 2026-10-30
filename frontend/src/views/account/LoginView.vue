<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
const auth = useAuthStore(), router = useRouter()
const loginName = ref(typeof router.currentRoute.value.query.account === 'string' ? router.currentRoute.value.query.account : '')
const password = ref(''), visible = ref(false), busy = ref(false), message = ref(''), help = ref(false)
const unknown = computed(() => auth.authenticationPending)
onMounted(() => auth.ensureCsrf())
async function login() {
  if (busy.value || unknown.value) return
  if (!loginName.value || !password.value) { message.value = '请输入登录账号和密码'; return }
  busy.value = true; message.value = ''
  try {
    const result = await auth.login({ loginName: loginName.value, password: password.value })
    if (result.kind === 'SUCCESS') { password.value = ''; await router.replace('/campus') }
    else message.value = result.kind === 'HTTP_ERROR' ? `${result.response.message}（${result.response.requestId}）` : result.message
  } finally { busy.value = false }
}
async function verifySession() {
  if (busy.value) return; busy.value = true
  try { if (await auth.restore()) { password.value = ''; await router.replace('/campus') } else message.value = auth.recoveryError || '当前会话尚未登录，请重新核对账号密码' }
  finally { busy.value = false }
}
</script>
<template>
  <main class="auth-page"><div class="auth-brand"><span class="brand-mark" aria-hidden="true">维</span><strong>校园设备维护</strong><span>本机示意校园</span></div>
    <section class="auth-card" aria-labelledby="login-title"><p class="eyebrow">欢迎回来</p><h1 id="login-title">登录你的账号</h1><p class="muted">统一账号登录，根据真实身份提供入口。</p>
      <p v-if="auth.passwordChangedNotice" class="notice success" role="status">密码已修改，全部原登录已失效。请使用新密码重新登录。</p><p v-if="auth.recoveryError" class="notice" role="alert">{{ auth.recoveryError }} <button type="button" class="text-button" @click="verifySession">重试会话核对</button></p>
      <form @submit.prevent="login"><label>登录账号<input v-model="loginName" name="username" autocomplete="username" maxlength="60" required placeholder="中文、英文、数字或下划线"></label>
        <label>密码<div class="password-field"><input v-model="password" name="password" :type="visible ? 'text' : 'password'" autocomplete="current-password" required maxlength="64"><button type="button" :aria-pressed="visible" @click="visible = !visible">{{ visible ? '隐藏' : '显示' }}</button></div></label>
        <p v-if="message" class="notice" role="alert">{{ message }}</p><button class="primary full" :disabled="busy || unknown" type="submit">{{ busy ? '正在登录…' : '登录' }}</button><button v-if="unknown" class="secondary full" :disabled="busy" type="button" @click="verifySession">核对当前会话</button>
      </form><div class="auth-links"><RouterLink to="/register">注册账号</RouterLink><button type="button" class="text-button" @click="help = !help">忘记密码？</button></div>
      <p v-if="help" class="notice">普通账号请联系管理员；管理员请联系项目维护者。普通账号由管理员在人员详情随机重置；管理员遗忘密码由维护者在本地重置。本系统没有短信找回。</p><p class="fine">管理员使用预置账号。手机号仅作联系，不验证号码归属。</p>
    </section></main>
</template>

