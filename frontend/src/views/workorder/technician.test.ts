import { expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { createRenderer, nextTick, ssrContextKey } from 'vue'
import { compile } from '@vue/compiler-dom'
import { compileScript, parse } from '@vue/compiler-sfc'
import ts from 'typescript'
import source from '../../components/workorder/TechnicianWorkPanel.vue?raw'
import TechnicianWorkPanel from '../../components/workorder/TechnicianWorkPanel.vue'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../../stores/auth'
import { useDraftStore } from '../../stores/drafts'
const uploads = vi.hoisted(() => vi.fn())
const api = vi.hoisted(() => ({ write: vi.fn(), lookup: vi.fn() }))
vi.mock('../../api/business', async original => ({ ...(await original<typeof import('../../api/business')>()), businessMutation: api.write, fetchOperation: api.lookup }))
vi.mock('../../composables/useImageSelection', async original => ({ ...(await original<typeof import('../../composables/useImageSelection')>()), ensureTemporaryUploaded: uploads }))
vi.mock('../../composables/useBusinessConfirmation', async () => { const { shallowRef } = await import('vue'); return { useBusinessConfirmation: () => ({ message: shallowRef(''), ask: async () => true, answer() {} }) } })
vi.mock('../../components/attachment/ImagePicker.vue', () => ({ default: { props: ['disabled'], render: () => null } }))
vi.mock('../../components/common/InlineConfirmation.vue', () => ({ default: { render: () => null } }))
interface Node { tag: string; text: string; props: Record<string, unknown>; children: Node[]; parent: Node | null; addEventListener: () => void; getRootNode: () => object }
const node = (tag: string, text = ''): Node => ({ tag, text, props: {}, children: [], parent: null, addEventListener() {}, getRootNode: () => ({}) })
const renderer = createRenderer<Node, Node>({ createElement: tag => node(tag), createText: text => node('#text', text), createComment: text => node('#comment', text),
  insert(child, parent) { child.parent = parent; parent.children.push(child) }, remove(child) { if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1) },
  setText(child, text) { child.text = text }, setElementText(child, text) { child.text = text; child.children = [] }, patchProp(child, key, _old, value) { child.props[key] = value }, parentNode: child => child.parent,
  nextSibling: child => child.parent?.children[child.parent.children.indexOf(child) + 1] ?? null, insertStaticContent(text, parent) { const child = node('#static', text); child.parent = parent; parent.children.push(child); return [child, child] } })
function all(root: Node): Node[] { return [root, ...root.children.flatMap(all)] }
it('refresh_that_removes_repair_action_keeps_input_and_file_visible_but_disables_old_submit',async()=> {
  vi.stubGlobal('Document',class {});vi.stubGlobal('ShadowRoot',class {})
  const pinia=createPinia();setActivePinia(pinia)
  useAuthStore().setConfirmedUser({id:'10',loginName:'worker',name:'维修人',phone:'13800000000',role:'TECHNICIAN',enabled:true,reviewStatus:'APPROVED',version:0,capabilities:{canBrowseCampus:true,canCreateRequest:false,canWorkOnTasks:true,canManage:false}})
  const store=useDraftStore(),draft=store.action('10','REPAIR_RESULT','44/1'),file=new File(['original'],'keep.png');draft.result='正在编辑尚未提交';draft.images.push({file,previewUrl:'blob:keep',temporaryId:null,uploadOperationId:crypto.randomUUID()})
  const descriptor=parse(source).descriptor,code=compile(descriptor.template!.content,{mode:'function',prefixIdentifiers:true,bindingMetadata:compileScript(descriptor,{id:'repair-read-change'}).bindings}).code
  const component={...TechnicianWorkPanel,render:new Function('Vue',ts.transpileModule(code,{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText)(Vue)}
  const detail=Vue.shallowRef({id:'44',workOrderNo:'WO-44',version:1,round:1,allowedActions:['PROGRESS','SUBMIT_RESULT']} as unknown as import('../../api/workorder').OrderDetail)
  const root=node('root'),app=renderer.createApp({render:()=>Vue.h(component as typeof TechnicianWorkPanel,{detail:detail.value,refresh:async()=>{}})}).use(pinia);app.provide(ssrContextKey,{modules:new Set()});app.mount(root)
  detail.value={...detail.value,version:2,status:'PENDING_ACCEPTANCE',allowedActions:[]};await nextTick()
  expect(draft.result).toBe('正在编辑尚未提交');expect(draft.images[0]?.file).toBe(file)
  const button=all(root).find(n=>n.tag==='button' && n.text.includes('上传本轮新凭证'))
  expect(button,'retained editor must remain visible with disabled old action').toBeDefined();expect(button!.props.disabled).toBeTruthy()
  expect(all(root).filter(n=>n.tag==='textarea').every(n=>n.props.disabled)).toBe(true)
  app.unmount();vi.unstubAllGlobals()
})
it('voucher_upload_locks_editor_and_duplicate_result_submission', async () => {
  vi.stubGlobal('Document', class {}); vi.stubGlobal('ShadowRoot', class {})
  const pinia = createPinia(); setActivePinia(pinia)
  const auth = useAuthStore(); auth.setConfirmedUser({ id: '10', loginName: 'worker', name: '维修人', phone: '13800000000', role: 'TECHNICIAN', enabled: true, reviewStatus: 'APPROVED', version: 0, capabilities: { canBrowseCampus: true, canCreateRequest: false, canWorkOnTasks: true, canManage: false } })
  vi.spyOn(auth, 'ensureCsrf').mockResolvedValue(true)
  const draft = useDraftStore().action('10', 'REPAIR_RESULT', '44/1'); draft.result = '本轮结果'; draft.images.push({ file: new File(['x'], 'repair.png'), previewUrl: 'blob:repair', temporaryId: null, uploadOperationId: crypto.randomUUID() })
  let release!: (value: { kind: 'FAILED'; message: string }) => void
  uploads.mockReset().mockImplementation(() => new Promise(resolve => { release = resolve }))
  const descriptor = parse(source).descriptor
  const code = compile(descriptor.template!.content, { mode: 'function', prefixIdentifiers: true, bindingMetadata: compileScript(descriptor, { id: 'technician-test' }).bindings }).code
  const component = { ...TechnicianWorkPanel, render: new Function('Vue', ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText)(Vue) }
  const detail = { id: '44', workOrderNo: 'WO-44', version: 1, round: 1, allowedActions: ['PROGRESS', 'SUBMIT_RESULT'] } as unknown as import('../../api/workorder').OrderDetail
  const root = node('root'), app = renderer.createApp(component, { detail, refresh: async () => {} }).use(pinia); app.provide(ssrContextKey, { modules: new Set() }); app.mount(root)
  const button = all(root).find(n => n.tag === 'button' && n.text.includes('上传本轮新凭证'))!
  const first = (button.props.onClick as () => Promise<void>)(); await Promise.resolve(); await Promise.resolve(); await nextTick()
  expect(uploads).toHaveBeenCalledTimes(1)
  expect(button.props.disabled).toBeTruthy()
  expect(all(root).filter(n => n.tag === 'textarea').every(n => n.props.disabled)).toBe(true)
  const second = (button.props.onClick as () => Promise<void>)(); await Promise.resolve()
  expect(uploads).toHaveBeenCalledTimes(1)
  release({ kind: 'FAILED', message: '模拟上传断网' }); await first; await second; await nextTick()
  expect(draft.result).toBe('本轮结果'); expect(draft.images).toHaveLength(1)
  expect(button.props.disabled).toBeFalsy()
  app.unmount(); vi.restoreAllMocks()
})

// The break caught here: clearing with the currently displayed round instead of
// the frozen submission's round loses another editor's new-round text and File.
it.each(['send', 'check'] as const)('late round-one result %s preserves round-two text and File', async mode => {
  vi.stubGlobal('Document', class {}); vi.stubGlobal('ShadowRoot', class {})
  const browser = new EventTarget(), refreshCount = vi.fn()
  vi.stubGlobal('window', browser); browser.addEventListener('campus:review-count-refresh', refreshCount)
  const pinia = createPinia(); setActivePinia(pinia)
  const auth = useAuthStore(); auth.setConfirmedUser({ id: '10', loginName: 'worker', name: '维修人', phone: '13800000000', role: 'TECHNICIAN', enabled: true, reviewStatus: 'APPROVED', version: 0, capabilities: { canBrowseCampus: true, canCreateRequest: false, canWorkOnTasks: true, canManage: false } })
  vi.spyOn(auth, 'ensureCsrf').mockResolvedValue(true)
  const revoke = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
  const store = useDraftStore(), old = store.action('10', 'REPAIR_RESULT', '44/1')
  old.result = '第一轮已提交结果'; old.images.push({ file: new File(['old'], 'round1.png'), previewUrl: 'blob:round1', temporaryId: '55', uploadOperationId: crypto.randomUUID() })
  uploads.mockReset().mockResolvedValue({ kind: 'SUCCESS', ids: ['55'] })
  let release!: (value: unknown) => void
  api.write.mockReset().mockImplementation(() => mode === 'send' ? new Promise(resolve => { release = resolve }) : Promise.resolve({ kind: 'UNKNOWN', operationId: null, message: '响应丢失' }))
  api.lookup.mockReset().mockImplementation(() => new Promise(resolve => { release = resolve }))
  const descriptor = parse(source).descriptor
  const code = compile(descriptor.template!.content, { mode: 'function', prefixIdentifiers: true, bindingMetadata: compileScript(descriptor, { id: 'technician-cross-round' }).bindings }).code
  const component = { ...TechnicianWorkPanel, render: new Function('Vue', ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText)(Vue) }
  const detail = Vue.shallowRef({ id: '44', workOrderNo: 'WO-44', version: 1, round: 1, allowedActions: ['PROGRESS', 'SUBMIT_RESULT'] } as unknown as import('../../api/workorder').OrderDetail)
  const root = node('root'), app = renderer.createApp({ render: () => Vue.h(component as typeof TechnicianWorkPanel, { detail: detail.value, refresh: async () => {} }) }).use(pinia)
  app.provide(ssrContextKey, { modules: new Set() }); app.mount(root)
  const submission = (all(root).find(n => n.tag === 'button' && n.text.includes('上传本轮新凭证'))!.props.onClick as () => Promise<void>)()
  for (let i = 0; i < 12; i++) { await Promise.resolve(); await nextTick() }
  const original = store.action('10', 'RESULT_SUBMITTED', '44').pending!
  expect(original.payload.round).toBe(1)
  let checking: Promise<void> | undefined
  if (mode === 'check') {
    await submission
    checking = (all(root).find(n => n.tag === 'button' && n.text === '核对原结果')!.props.onClick as () => Promise<void>)()
    await nextTick()
  }
  const next = store.action('10', 'REPAIR_RESULT', '44/2')
  next.result = '第二轮正在编辑'; const newFile = new File(['new'], 'round2.png')
  next.images.push({ file: newFile, previewUrl: 'blob:round2', temporaryId: null, uploadOperationId: crypto.randomUUID() })
  detail.value = { ...detail.value, round: 2, version: 4 }; await nextTick()
  const receipt = { operationId: original.operationId, resourceType: 'WORK_ORDER', resourceId: '44', resultVersion: 2, resultRound: 1, committedAt: '2026-10-16T00:00:00.000Z', relatedResourceType: null, relatedResourceId: null }
  expect(refreshCount).not.toHaveBeenCalled()
  release({ kind: 'SUCCESS', data: mode === 'send' ? receipt : { kind: 'SUCCEEDED', receipt }, requestId: 'controlled-late-response' })
  await submission; await checking; await nextTick()
  expect(refreshCount).toHaveBeenCalledTimes(1)
  expect(store.action('10', 'REPAIR_RESULT', '44/2').result).toBe('第二轮正在编辑')
  expect(next.images[0]?.file).toBe(newFile)
  expect(next.images[0]?.previewUrl).toBe('blob:round2')
  expect(store.action('10', 'REPAIR_RESULT', '44/1').result).toBe('')
  expect(revoke).toHaveBeenCalledWith('blob:round1')
  expect(revoke).not.toHaveBeenCalledWith('blob:round2')
  app.unmount(); vi.restoreAllMocks(); vi.unstubAllGlobals()
})

it('a later UNKNOWN progress must replace an earlier accept-success message', async () => {
  vi.stubGlobal('Document', class {}); vi.stubGlobal('ShadowRoot', class {})
  const pinia = createPinia(); setActivePinia(pinia)
  const auth = useAuthStore(); auth.setConfirmedUser({ id: '10', loginName: 'worker', name: '维修人', phone: '13800000000', role: 'TECHNICIAN', enabled: true, reviewStatus: 'APPROVED', version: 0, capabilities: { canBrowseCampus: true, canCreateRequest: false, canWorkOnTasks: true, canManage: false } })
  vi.spyOn(auth, 'ensureCsrf').mockResolvedValue(true)
  const descriptor = parse(source).descriptor
  const code = compile(descriptor.template!.content, { mode: 'function', prefixIdentifiers: true, bindingMetadata: compileScript(descriptor, { id: 'technician-feedback' }).bindings }).code
  const component = { ...TechnicianWorkPanel, render: new Function('Vue', ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText)(Vue) }
  const detail = Vue.shallowRef({ id: '44', workOrderNo: 'WO-44', version: 0, round: 0, allowedActions: ['ACCEPT', 'REFUSE'] } as unknown as import('../../api/workorder').OrderDetail)
  api.write.mockReset().mockImplementation(async (_path, _method, payload) => ({ kind: 'SUCCESS', data: { operationId: payload.operationId }, requestId: 'controlled-accept' }))
  const root=node('root'), app=renderer.createApp({ render: () => Vue.h(component as typeof TechnicianWorkPanel, { detail: detail.value, refresh: async () => { detail.value={...detail.value,version:1,round:1,allowedActions:['PROGRESS','SUBMIT_RESULT']} } }) }).use(pinia)
  app.provide(ssrContextKey,{modules:new Set()}); app.mount(root)
  await (all(root).find(n=>n.tag==='button' && n.text==='核对并接单')!.props.onClick as () => Promise<void>)(); await nextTick()
  expect(all(root).find(n=>n.props.role==='alert')?.text).toContain('操作已确认成功')
  useDraftStore().action('10','PROGRESS_DRAFT','44/1').progress='新进度'
  api.write.mockResolvedValue({kind:'UNKNOWN',operationId:null,message:'进度响应丢失，请核对原结果'})
  await (all(root).find(n=>n.tag==='button' && n.text==='确认追加进度')!.props.onClick as () => Promise<void>)(); await nextTick()
  expect(all(root).find(n=>n.props.role==='alert')?.text).toContain('进度响应丢失')
  expect(all(root).find(n=>n.props.role==='alert')?.text).not.toContain('操作已确认成功')
  app.unmount(); vi.restoreAllMocks(); vi.unstubAllGlobals()
})
