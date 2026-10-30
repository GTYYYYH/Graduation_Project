import { expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { createRenderer, nextTick, ssrContextKey } from 'vue'
import { compile } from '@vue/compiler-dom'
import { compileScript, parse } from '@vue/compiler-sfc'
import ts from 'typescript'
import source from '../../components/workorder/AcceptancePanel.vue?raw'
import AcceptancePanel from '../../components/workorder/AcceptancePanel.vue'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../../stores/auth'
import { useDraftStore } from '../../stores/drafts'
const writes = vi.hoisted(() => vi.fn())
vi.mock('../../api/business', async importOriginal => ({ ...(await importOriginal<typeof import('../../api/business')>()), businessMutation: writes }))
vi.mock('../../components/common/InlineConfirmation.vue', async () => { const vue = await import('vue'); return { default: { props: ['message'], emits: ['answer'], render(this: { message: string; $emit: (event: string, value: boolean) => void }) { return vue.h('div', [vue.h('p', this.message), ...(this.message ? [vue.h('button', { onClick: () => this.$emit('answer', true) }, '确认继续')] : [])]) } } } })
interface Node { tag: string; text: string; props: Record<string, unknown>; children: Node[]; parent: Node | null; addEventListener: () => void; getRootNode: () => object }
const node = (tag: string, text = ''): Node => ({ tag, text, props: {}, children: [], parent: null, addEventListener() {}, getRootNode: () => ({}) })
const renderer = createRenderer<Node, Node>({ createElement: tag => node(tag), createText: text => node('#text', text), createComment: text => node('#comment', text),
  insert(child, parent) { child.parent = parent; parent.children.push(child) }, remove(child) { if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1) },
  setText(child, text) { child.text = text }, setElementText(child, text) { child.text = text; child.children = [] }, patchProp(child, key, _old, value) { child.props[key] = value }, parentNode: child => child.parent,
  nextSibling: child => child.parent?.children[child.parent.children.indexOf(child) + 1] ?? null, insertStaticContent(text, parent) { const child = node('#static', text); child.parent = parent; parent.children.push(child); return [child, child] } })
function all(root: Node): Node[] { return [root, ...root.children.flatMap(all)] }
it('confirmation_precedes_acceptance', async () => {
  vi.stubGlobal('Document', class {}); vi.stubGlobal('ShadowRoot', class {})
  writes.mockReset(); const pinia = createPinia(); setActivePinia(pinia)
  const auth = useAuthStore(); auth.setConfirmedUser({ id: '10', loginName: 'owner', name: '报修人', phone: '13800000000', role: 'REPORTER', enabled: true, reviewStatus: null, version: 0, capabilities: { canBrowseCampus: true, canCreateRequest: true, canWorkOnTasks: false, canManage: false } })
  const descriptor = parse(source).descriptor
  const code = compile(descriptor.template!.content, { mode: 'function', prefixIdentifiers: true, bindingMetadata: compileScript(descriptor, { id: 'acceptance-test' }).bindings }).code
  const component = { ...AcceptancePanel, render: new Function('Vue', ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText)(Vue) }
  const detail = { id: '44', workOrderNo: 'WO-44', version: 3, round: 1, allowedActions: ['ACCEPTANCE'], rounds: [{ round: 1, result: '已经修好', submittedAt: '2026-10-15T00:00:00.000Z', attachments: [{ id: '55' }] }] } as unknown as import('../../api/workorder').OrderDetail
  const root = node('root'), app = renderer.createApp(component, { detail, refresh: async () => {} }).use(pinia); app.provide(ssrContextKey, { modules: new Set() }); app.mount(root)
  const button = all(root).find(n => n.tag === 'button' && n.text.includes('验收通过'))
  expect(button).toBeDefined(); (button?.props.onClick as (() => void))(); await nextTick()
  const acceptanceRequestsBeforeConfirmation = writes.mock.calls.length
  expect(acceptanceRequestsBeforeConfirmation).toBe(0)
  expect(all(root).some(n => n.text.includes('确认本轮验收'))).toBe(true)
  expect(all(root).filter(n => n.tag === 'textarea').every(n => n.props.disabled)).toBe(true)
  app.unmount()
})

it('late acceptance response clears the submitted round only and keeps a new-round opinion', async () => {
  vi.stubGlobal('Document', class {}); vi.stubGlobal('ShadowRoot', class {})
  const pinia = createPinia(); setActivePinia(pinia)
  const auth = useAuthStore(); auth.setConfirmedUser({ id: '10', loginName: 'owner', name: '报修人', phone: '13800000000', role: 'REPORTER', enabled: true, reviewStatus: null, version: 0, capabilities: { canBrowseCampus: true, canCreateRequest: true, canWorkOnTasks: false, canManage: false } })
  vi.spyOn(auth, 'ensureCsrf').mockResolvedValue(true)
  let release!: (value: unknown) => void
  writes.mockReset().mockImplementation(() => new Promise(resolve => { release = resolve }))
  const descriptor = parse(source).descriptor
  const code = compile(descriptor.template!.content, { mode: 'function', prefixIdentifiers: true, bindingMetadata: compileScript(descriptor, { id: 'acceptance-cross-round' }).bindings }).code
  const component = { ...AcceptancePanel, render: new Function('Vue', ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText)(Vue) }
  const detail = Vue.shallowRef({ id: '44', workOrderNo: 'WO-44', version: 3, round: 1, allowedActions: ['ACCEPTANCE'], rounds: [{ round: 1, result: '第一轮', submittedAt: '2026-10-16T00:00:00.000Z', attachments: [{ id: '55' }] }] } as unknown as import('../../api/workorder').OrderDetail)
  const store = useDraftStore(); store.action('10', 'ACCEPTANCE_DRAFT', '44/1').reason = '第一轮未修好'
  const root = node('root'), app = renderer.createApp({ render: () => Vue.h(component as typeof AcceptancePanel, { detail: detail.value, refresh: async () => {} }) }).use(pinia)
  app.provide(ssrContextKey, { modules: new Set() }); app.mount(root)
  const deciding = (all(root).find(n => n.tag === 'button' && n.text === '核对并退回返工')!.props.onClick as () => Promise<void>)()
  await nextTick(); (all(root).find(n => n.tag === 'button' && n.text === '确认继续')!.props.onClick as () => void)()
  for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() }
  const original = store.action('10', 'ACCEPTANCE', '44').pending!
  expect(original.payload.reason).toBe('第一轮未修好')
  detail.value = { ...detail.value, version: 5, round: 2, rounds: [{ ...detail.value.rounds[0]!, round: 2 }] }
  store.action('10', 'ACCEPTANCE_DRAFT', '44/2').reason = '第二轮正在核对'; await nextTick()
  release({ kind: 'SUCCESS', data: { operationId: original.operationId, resourceType: 'WORK_ORDER', resourceId: '44', resultVersion: 4, resultRound: 2, committedAt: '2026-10-16T00:00:00.000Z', relatedResourceType: null, relatedResourceId: null }, requestId: 'controlled-late-response' })
  await deciding; await nextTick()
  expect(store.action('10', 'ACCEPTANCE_DRAFT', '44/2').reason).toBe('第二轮正在核对')
  expect(store.action('10', 'ACCEPTANCE_DRAFT', '44/1').reason).toBe('')
  app.unmount(); vi.restoreAllMocks(); vi.unstubAllGlobals()
})
