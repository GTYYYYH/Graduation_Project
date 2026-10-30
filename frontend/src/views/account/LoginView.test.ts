import { afterEach, expect, it, vi } from 'vitest'
import { createSSRApp } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { renderToString } from '@vue/server-renderer'
import LoginView from './LoginView.vue'
import { useAuthStore } from '../../stores/auth'

afterEach(() => vi.unstubAllGlobals())
it('password_changed_notice_remains_visible_after_identity_is_cleared_without_prefilling_password',async()=>{
  const pinia=createPinia();setActivePinia(pinia);const auth=useAuthStore();auth.clearIdentity();auth.passwordChangedNotice=true
  const router=createRouter({history:createMemoryHistory(),routes:[{path:'/login',component:LoginView},{path:'/register',component:LoginView}]});await router.push('/login');await router.isReady()
  const html=await renderToString(createSSRApp(LoginView).use(pinia).use(router));expect(html).toContain('密码已修改，全部原登录已失效。请使用新密码重新登录。');expect(html.match(/<input[^>]*name="password"[^>]*>/)?.[0]).toContain('value=""')
})
it('login_page_unlocks_after_unknown_result_is_verified_anonymous_and_preserves_account', async () => {
  const pinia = createPinia(); setActivePinia(pinia)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/login', component: LoginView }, { path: '/register', component: LoginView }] })
  await router.push('/login?account=synthetic_editable'); await router.isReady()
  const auth = useAuthStore(); auth.csrf = { token: 'x'.repeat(64), headerName: 'X-CSRF-TOKEN' }
  const render = () => renderToString(createSSRApp(LoginView).use(pinia).use(router))
  const submitButton = (html: string) => html.match(/<button\b[^>]*type="submit"[^>]*>/)?.[0] ?? ''
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network')))
  expect((await auth.login({ loginName: 'synthetic_editable', password: 'Synthetic1!' })).kind).toBe('UNKNOWN')
  expect(submitButton(await render())).toContain(' disabled')
  await auth.restore() // 网络失败仍待核对。
  expect(submitButton(await render())).toContain(' disabled')
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'AUTH_REQUIRED', message: '请登录', data: null, requestId: 'synthetic-id' }), { status: 401, headers: { 'Content-Type': 'application/json' } })))
  expect(await auth.restore()).toBe(false)
  const html = await render()
  expect(auth.recovery).toBe('ANONYMOUS')
  expect(submitButton(html)).not.toContain(' disabled')
  expect(html).toContain('value="synthetic_editable"')
  expect(html).not.toContain('核对当前会话</button>')
})

