import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useDraftStore } from './stores/drafts'

const pinia = createPinia()
const app = createApp(App).use(pinia).use(router)
const drafts = useDraftStore(pinia)
window.addEventListener('beforeunload', event => { if (drafts.hasUnsaved) { event.preventDefault(); event.returnValue = '' } })
app.mount('#app')
