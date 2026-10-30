<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { registerAccount } from '../../api/account'
import type { RegistrationVo } from '../../api/account'
const auth = useAuthStore()
const form = reactive({ role: '' as '' | 'REPORTER' | 'TECHNICIAN', loginName: '', name: '', phone: '', password: '', confirmPassword: '' })
const visible = ref(false), busy = ref(false), message = ref(''), result = ref<RegistrationVo | null>(null), unknown = ref(false)
onMounted(() => auth.ensureCsrf())
async function submit() {
  if (busy.value || result.value || unknown.value) return
  const count = (v: string) => [...v].length
  if (!form.role) { message.value = '请主动选择角色，注册后不可更改'; return }
  if (!/^[\p{Script=Han}A-Za-z0-9_]+$/u.test(form.loginName) || count(form.loginName) < 2 || count(form.loginName) > 30) { message.value = '账号需为2—30位中文、英文、数字或下划线'; return }
  if (!form.name.trim() || count(form.name.trim()) > 100) { message.value = '请输入1—100字姓名'; return }
  if (!/^1[3-9][0-9]{9}$/.test(form.phone)) { message.value = '请输入大陆11位手机号码'; return }
  if (count(form.password) < 8 || count(form.password) > 32 || !/[A-Za-z]/.test(form.password) || !/[0-9]/.test(form.password)) { message.value = '密码需为8—32位，至少包含英文字母和数字'; return }
  if (form.password !== form.confirmPassword) { message.value = '两次密码输入不一致'; return }
  busy.value = true; message.value = ''
  try {
    if (!await auth.ensureCsrf()) { message.value = '校验信息获取失败，请重试'; return }
    const r = await registerAccount({ ...form, role: form.role })
    if (r.kind === 'SUCCESS') { result.value = r.data; form.password = ''; form.confirmPassword = '' }
    else if (r.kind === 'HTTP_ERROR') { message.value = `${r.response.message}（${r.response.requestId}）`; if (r.response.code === 'CSRF_INVALID') await auth.ensureCsrf(true) }
    else { unknown.value = true; message.value = '注册结果待确认。请使用当前账号前往登录核对；重复账号或手机号提示不能当作注册成功。' }
  } finally { busy.value = false }
}
</script>
<template>
  <main class="auth-page"><div class="auth-brand"><span class="brand-mark" aria-hidden="true">维</span><strong>校园设备维护</strong><span>本机示意校园</span></div>
    <section class="auth-card register-card" aria-labelledby="register-title"><p class="eyebrow">加入校园维护</p><h1 id="register-title">注册账号</h1>
      <template v-if="result"><p class="notice success" role="status">注册成功。{{ result.role === 'REPORTER' ? '无需审核，登录后即可获得报修身份。' : '申请已进入待审核，可登录查看状态；审核通过后参与维修。' }}</p><p>登录账号：<strong>{{ result.loginName }}</strong></p><RouterLink class="primary full link-button" :to="{ path: '/login', query: { account: result.loginName } }">前往登录</RouterLink><p class="fine">注册没有自动登录，登录页密码留空。</p></template>
      <form v-else @submit.prevent="submit"><fieldset class="roles"><legend>选择角色（注册后不可更改）</legend><label><input v-model="form.role" type="radio" name="role" value="REPORTER" required>报修用户<span>无需审核，登录后可报修</span></label><label><input v-model="form.role" type="radio" name="role" value="TECHNICIAN">维修人员<span>管理员审核通过后参与维修</span></label></fieldset>
        <label>登录账号<input v-model="form.loginName" name="username" autocomplete="username" required maxlength="60"><small>2—30位；中文、英文、数字、下划线，全局唯一</small></label>
        <div class="form-grid"><label>姓名<input v-model="form.name" name="name" autocomplete="name" required maxlength="200"></label><label>手机号码<input v-model="form.phone" name="tel" type="tel" autocomplete="tel-national" inputmode="numeric" required maxlength="11" placeholder="11位大陆手机号"></label></div>
        <label>密码<div class="password-field"><input v-model="form.password" name="new-password" :type="visible ? 'text' : 'password'" autocomplete="new-password" required maxlength="64"><button type="button" :aria-pressed="visible" @click="visible = !visible">{{ visible ? '隐藏' : '显示' }}</button></div><small>8—32位，至少包含英文字母和数字，可用特殊字符</small></label><label>确认密码<input v-model="form.confirmPassword" name="confirm-password" :type="visible ? 'text' : 'password'" autocomplete="new-password" required maxlength="64"></label>
        <p v-if="message" class="notice" role="alert">{{ message }}</p><button class="primary full" type="submit" :disabled="busy || unknown">{{ busy ? '正在提交…' : '注册账号' }}</button><RouterLink v-if="unknown" class="secondary full link-button" :to="{ path: '/login', query: { account: form.loginName } }">前往登录核对</RouterLink>
      </form><div class="auth-links"><RouterLink to="/login">返回登录</RouterLink></div><p class="fine">手机号仅作联系方式，无短信验证。管理员使用本地预置账号。</p>
    </section></main>
</template>
