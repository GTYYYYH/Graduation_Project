import { onUnmounted, shallowRef, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
/** Page-local confirmation that is cancelled when its identity or component leaves. */
export function useBusinessConfirmation() {
  const auth = useAuthStore(), message = shallowRef('')
  let resolve: ((confirmed: boolean) => void) | null = null, alive = true
  function answer(confirmed: boolean) { const current = resolve; resolve = null; message.value = ''; current?.(confirmed) }
  function ask(text: string): Promise<boolean> { if (!alive || resolve) return Promise.resolve(false); message.value = text; return new Promise<boolean>(done => { resolve = done }) }
  watch(() => auth.identityEpoch, () => answer(false), { flush: 'sync' })
  onUnmounted(() => { alive = false; answer(false) })
  return { message, ask, answer }
}
