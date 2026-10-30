import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { createRenderer, defineComponent, h, nextTick, ssrContextKey } from 'vue'
import type { App } from 'vue'
import { compile } from '@vue/compiler-dom'
import { compileScript, parse } from '@vue/compiler-sfc'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import MainLayout from './MainLayout.vue'
import mainLayoutSource from './MainLayout.vue?raw'
import { useAuthStore } from '../stores/auth'
import { requestReviewCountRefresh } from '../composables/useReviewNotifications'

// Vitest transforms SFC imports for SSR in this node project. Compile the same
// template for the client renderer while retaining the real imported setup.
const descriptor = parse(mainLayoutSource).descriptor
const bindingMetadata = compileScript(descriptor, { id: 'shell-test' }).bindings
const ClientMainLayout = { ...MainLayout, render: new Function('Vue',
  compile(descriptor.template!.content, { mode: 'function', prefixIdentifiers: true, bindingMetadata }).code)(Vue) }

// A Vue renderer exercises the real shell and its lifecycle without adding a DOM library.
// CSS scrolling and actual viewport layout are verified separately in the real browser.
interface Surface {
  kind: string
  text: string
  props: Record<string, unknown>
  children: Surface[]
  parent: Surface | null
  getBoundingClientRect: () => DOMRect
}
let metrics: { header: number; sidebar: number }
const applications: App[] = []
function surface(kind: string, text = ''): Surface {
  return { kind, text, props: {}, children: [], parent: null,
    getBoundingClientRect: () => ({ height: kind === 'header' ? metrics.header : metrics.sidebar } as DOMRect) }
}
function insert(child: Surface, parent: Surface, anchor: Surface | null = null) {
  if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1)
  child.parent = parent
  const index = anchor ? parent.children.indexOf(anchor) : -1
  if (index < 0) parent.children.push(child); else parent.children.splice(index, 0, child)
}
const renderer = createRenderer<Surface, Surface>({
  createElement: tag => surface(tag), createText: text => surface('#text', text),
  createComment: text => surface('#comment', text), insert,
  remove(node) { if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1); node.parent = null },
  setText(node, text) { node.text = text }, setElementText(node, text) { node.text = text; node.children = [] },
  patchProp(node, key, _previous, value) { node.props[key] = value },
  parentNode: node => node.parent, nextSibling: node => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null,
  insertStaticContent(content, parent, anchor) { const node = surface('#static', content); insert(node, parent, anchor); return [node, node] },
})
class Page extends EventTarget { visibilityState = 'visible' }
class ShellResizeObserver {
  static active: ShellResizeObserver[] = []
  disconnected = false
  constructor(readonly notify: ResizeObserverCallback) { ShellResizeObserver.active.push(this) }
  observe() {}
  disconnect() { this.disconnected = true }
}
function all(root: Surface): Surface[] { return [root, ...root.children.flatMap(all)] }
function hasClass(node: Surface, name: string) { return typeof node.props.class === 'string' && node.props.class.split(' ').includes(name) }
function text(node: Surface): string { return node.text + node.children.map(text).join('') }
function counted(count: number) {
  return new Response(JSON.stringify({ code: 'OK', message: '合成反馈', requestId: 'shell-count', data: { userId: '94', role: 'ADMIN', count } }), { headers: { 'Content-Type': 'application/json' } })
}
function json(total: number) {
  return new Response(JSON.stringify({ code: 'OK', message: '合成反馈', requestId: 'synthetic-shell',
    data: { records: [], total, page: 1, pageSize: 1 } }), { status: 200, headers: { 'Content-Type': 'application/json' } })
}
async function settle() { await nextTick(); await vi.advanceTimersByTimeAsync(0); await nextTick() }
async function mountShell(role: 'ADMIN' | 'TECHNICIAN' | 'REPORTER' = 'ADMIN') {
  const pinia = createPinia(); setActivePinia(pinia)
  const auth = useAuthStore()
  auth.setConfirmedUser({ id: '94', loginName: 'synthetic_shell', name: '合成用户', phone: '13800000000', role,
    enabled: true, reviewStatus: role === 'TECHNICIAN' ? 'APPROVED' : null, version: 0,
    capabilities: { canBrowseCampus: true, canCreateRequest: role !== 'TECHNICIAN', canWorkOnTasks: role === 'TECHNICIAN', canManage: role === 'ADMIN' } })
  const view = defineComponent({ render: () => h('p', '合成业务页面') })
  const router = createRouter({ history: createMemoryHistory(), routes: ['/campus', '/list', '/requests/new', '/requests',
    '/account/review', '/tasks', '/work-orders', '/equipment', '/admin/requests', '/admin/personnel', '/admin/work-orders', '/login']
    .map(path => ({ path, component: view, meta: { entryLabel: '合成页面' } })) })
  await router.push('/campus'); await router.isReady()
  const root = surface('root'), app = renderer.createApp(ClientMainLayout).use(pinia).use(router)
  app.provide(ssrContextKey, { modules: new Set() })
  app.mount(root); applications.push(app); await settle()
  return { root, app, auth }
}
beforeEach(() => {
  vi.useFakeTimers(); metrics = { header: 136, sidebar: 188 }; ShellResizeObserver.active = []
  vi.stubGlobal('window', new EventTarget()); vi.stubGlobal('document', new Page())
  vi.stubGlobal('ResizeObserver', ShellResizeObserver)
  vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) => Promise.resolve(url === '/api/work-orders/navigation-count' ? counted(0) : json(7))))
})
afterEach(() => {
  for (const app of applications.splice(0)) app.unmount()
  vi.clearAllTimers(); vi.useRealTimers(); vi.unstubAllGlobals()
})

it('reserves_the_measured_header_and_navigation_heights_in_the_shell', async () => {
  const { root } = await mountShell()
  const shell = all(root).find(node => hasClass(node, 'app-shell'))
  const style = shell?.props.style as Record<string, string> | undefined
  expect(style?.['--header-height']).toBe('136px')
  expect(style?.['--sidebar-height']).toBe('188px')
})
it('updates_shell_spacing_when_wrapped_header_or_navigation_height_changes', async () => {
  const { root, app } = await mountShell()
  metrics = { header: 168, sidebar: 216 }
  for (const observer of ShellResizeObserver.active) observer.notify([], observer as unknown as ResizeObserver)
  await settle()
  const style = all(root).find(node => hasClass(node, 'app-shell'))?.props.style as Record<string, string> | undefined
  expect(style?.['--header-height']).toBe('168px'); expect(style?.['--sidebar-height']).toBe('216px')
  app.unmount(); applications.splice(applications.indexOf(app), 1)
  expect(ShellResizeObserver.active.length).toBeGreaterThan(0)
  expect(ShellResizeObserver.active.every(observer => observer.disconnected)).toBe(true)
})
it('renders_the_pending_review_count_as_an_accessible_admin_navigation_badge', async () => {
  const { root } = await mountShell()
  const badge = all(root).find(node => hasClass(node, 'review-badge'))
  expect(badge && text(badge)).toBe('7')
  expect(badge?.props['aria-label']).toContain('7项待审核')
})
it('hides_the_navigation_badge_for_a_confirmed_zero', async () => {
  vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) => Promise.resolve(url === '/api/work-orders/navigation-count' ? counted(0) : json(0))))
  const { root } = await mountShell()
  expect(all(root).some(node => hasClass(node, 'review-badge'))).toBe(false)
  expect(text(root)).not.toContain('数量暂未知')
})
it('renders a separate accessible pending personnel badge beside personnel management', async () => {
  vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) => Promise.resolve(json(url.startsWith('/api/admin/registration-reviews?') ? 4 : 7))))
  const { root } = await mountShell()
  const badge = all(root).find(node => hasClass(node, 'personnel-badge'))
  expect(badge && text(badge)).toBe('4')
  expect(badge?.props['aria-label']).toContain('4名待审核维修人员')
  expect(text(badge!.parent!)).toContain('人员管理')
})
it('hides only the personnel badge when personnel is zero and requests remain pending', async () => {
  vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) => Promise.resolve(json(url.startsWith('/api/admin/registration-reviews?') ? 0 : 7))))
  const { root } = await mountShell()
  expect(all(root).some(node => hasClass(node, 'personnel-badge'))).toBe(false)
  expect(all(root).find(node => hasClass(node, 'review-badge'))?.text).toBe('7')
})
it('marks_the_rendered_previous_count_expired_after_an_unsuccessful_refresh', async () => {
  const reads = vi.fn().mockImplementationOnce(() => Promise.resolve(json(7))).mockRejectedValue(new TypeError('synthetic network'))
  vi.stubGlobal('fetch', reads); const { root } = await mountShell()
  requestReviewCountRefresh(); await settle()
  const badge = all(root).find(node => hasClass(node, 'review-badge'))
  expect(badge && text(badge)).toBe('7'); expect(text(root)).toContain('数量已过期')
  expect(badge?.props['aria-label']).toContain('已过期')
})
it('shows_an_unknown_quantity_after_the_initial_read_fails', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('synthetic network')))
  const { root } = await mountShell()
  expect(all(root).some(node => hasClass(node, 'review-badge'))).toBe(false)
  expect(text(root)).toContain('数量暂未知')
})
it.each(['TECHNICIAN', 'REPORTER'] as const)('renders the %s work-order count beside the correct navigation entry', async role => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'OK', message: '合成反馈', requestId: 'navigation-count', data: { userId: '94', role, count: 4 } }), { headers: { 'Content-Type': 'application/json' } })))
  const { root } = await mountShell(role)
  const badge = all(root).find(node => hasClass(node, 'work-order-badge'))
  expect(badge && text(badge)).toBe('4'); expect(badge?.props['aria-label']).toContain(role === 'TECHNICIAN' ? '待处理工单' : '待验收工单')
  expect(badge && text(badge.parent!)).toContain(role === 'TECHNICIAN' ? '我的任务' : '我的工单')
  expect(all(root).filter(node => hasClass(node, 'work-order-badge'))).toHaveLength(1)
})

it('renders admin unfinished quantity beside work-order management and hides it when complete', async () => {
  let unfinished = 3
  vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) => Promise.resolve(url === '/api/work-orders/navigation-count' ? counted(unfinished) : json(0))))
  const { root } = await mountShell()
  let badge = all(root).find(node => hasClass(node, 'management-order-badge'))
  expect(badge && text(badge)).toBe('3'); expect(badge?.props['aria-label']).toBe('3项未完成工单')
  expect(badge && text(badge.parent!)).toContain('工单管理')
  unfinished = 2; requestReviewCountRefresh(); await settle()
  badge = all(root).find(node => hasClass(node, 'management-order-badge')); expect(badge && text(badge)).toBe('2')
  unfinished = 0; requestReviewCountRefresh(); await settle()
  expect(all(root).some(node => hasClass(node, 'management-order-badge'))).toBe(false)
  expect(text(root)).not.toContain('数量暂未知')
})
