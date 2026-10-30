<script setup lang="ts">
import type { useAccountSettings } from '../../composables/useAccountSettings'
defineProps<{state:ReturnType<typeof useAccountSettings>}>()
</script>
<template><section class="card"><h2>个人资料</h2><form @submit.prevent="state.saveProfile"><fieldset :disabled="!state.canEdit.value||state.busy.value||!!state.pending.value"><label>姓名<input v-model="state.form.name" maxlength="200" required></label><label>手机号码<input v-model="state.form.phone" type="tel" maxlength="11" required></label><div class="actions"><button type="submit" class="primary" :disabled="!state.changed.value">保存资料</button><button class="secondary" type="button" @click="state.cancelProfile">取消修改</button></div></fieldset></form><p v-if="!state.canEdit.value" class="notice">当前资格的资料只读。审核未通过请到审核状态页修改并重新申请。</p><RouterLink v-if="state.profile.value?.role==='TECHNICIAN'" to="/account/review">查看资格与审核历史</RouterLink></section></template>

