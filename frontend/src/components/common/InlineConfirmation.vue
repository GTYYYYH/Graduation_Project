<script setup lang="ts">
import { nextTick, shallowRef, watch } from 'vue'
const props = defineProps<{ message: string }>(), emit = defineEmits<{ answer: [confirmed: boolean] }>(), cancel = shallowRef<HTMLButtonElement | null>(null)
watch(() => props.message, async text => { if (text) { await nextTick(); cancel.value?.focus() } })
</script>
<template><section v-if="message" class="notice confirmation" role="alert" @keydown.esc="emit('answer', false)"><strong>请确认本次操作</strong><p>{{ message }}</p><div class="actions"><button type="button" class="primary" @click="emit('answer', true)">确认继续</button><button ref="cancel" type="button" class="secondary" @click="emit('answer', false)">取消本次操作</button></div></section></template>
<style scoped>.confirmation { border: 2px solid #155e75; margin: 18px 0; }.confirmation p { white-space: pre-wrap; overflow-wrap: anywhere; }</style>
