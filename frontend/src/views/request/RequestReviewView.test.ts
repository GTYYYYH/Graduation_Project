import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { createRenderer, h, nextTick, ssrContextKey } from 'vue'
import { compile } from '@vue/compiler-dom'
import { compileScript, parse } from '@vue/compiler-sfc'
import ts from 'typescript'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import ReviewView from './RequestReviewView.vue'
import source from './RequestReviewView.vue?raw'
import AssignmentPanel from '../../components/workorder/AssignmentPanel.vue'
import panelSource from '../../components/workorder/AssignmentPanel.vue?raw'
import AssignmentCandidateList from '../../components/workorder/AssignmentCandidateList.vue'
import candidateSource from '../../components/workorder/AssignmentCandidateList.vue?raw'
import { useAuthStore } from '../../stores/auth'
import { useDraftStore } from '../../stores/drafts'

const api = vi.hoisted(() => ({ request: vi.fn(), equipment: vi.fn(), detail: vi.fn(), eligible: vi.fn(), recommend: vi.fn() }))
vi.mock('../../api/repair-request', () => ({ fetchRequest: api.request }))
vi.mock('../../api/equipment', () => ({ fetchEquipment: api.equipment, fetchEquipmentDetail: api.detail, facilityNames: { TOILET: '马桶' } }))
vi.mock('../../components/request/RequestMaterialContent.vue', () => ({ default: { render: () => null } }))
vi.mock('../../api/workorder', () => ({ fetchAssignmentEligible: api.eligible, fetchAssignmentCandidates: api.recommend }))
vi.mock('../../components/common/InlineConfirmation.vue', () => ({ default: { render: () => null } }))
const descriptor = parse(source).descriptor
const clientCode = compile(descriptor.template!.content, { mode: 'function', prefixIdentifiers: true,
  bindingMetadata: compileScript(descriptor, { id: 'review-page-test' }).bindings }).code
const component = { ...ReviewView, render: new Function('Vue', ts.transpileModule(clientCode, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText)(Vue) }
const panelDescriptor = parse(panelSource).descriptor
AssignmentPanel.render = new Function('Vue', ts.transpileModule(compile(panelDescriptor.template!.content, { mode: 'function', prefixIdentifiers: true,
  bindingMetadata: compileScript(panelDescriptor, { id: 'assignment-panel-test' }).bindings }).code, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText)(Vue)
const candidateDescriptor=parse(candidateSource).descriptor
AssignmentCandidateList.render=new Function('Vue',ts.transpileModule(compile(candidateDescriptor.template!.content,{mode:'function',prefixIdentifiers:true,bindingMetadata:compileScript(candidateDescriptor,{id:'candidate-ui-test'}).bindings}).code,{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText)(Vue)
interface Node { tag: string; text: string; value: unknown; props: Record<string, unknown>; children: Node[]; parent: Node | null; addEventListener: () => void; getRootNode: () => object }
function node(tag: string, text = ''): Node { return { tag, text, value: '', props: {}, children: [], parent: null, addEventListener() {}, getRootNode: () => ({}) } }
const renderer = createRenderer<Node, Node>({
  createElement: tag => node(tag), createText: text => node('#text', text), createComment: text => node('#comment', text),
  insert(child, parent, anchor = null) { if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1); child.parent = parent; const i = anchor ? parent.children.indexOf(anchor) : -1; if (i < 0) parent.children.push(child); else parent.children.splice(i, 0, child) },
  remove(child) { if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1) },
  setText(child, text) { child.text = text }, setElementText(child, text) { child.text = text; child.children = [] },
  patchProp(child, key, _old, value) { child.props[key] = value; if (key === 'value') child.value = value }, parentNode: child => child.parent,
  nextSibling: child => child.parent?.children[child.parent.children.indexOf(child) + 1] ?? null,
  insertStaticContent(text, parent) { const child = node('#static', text); child.parent = parent; parent.children.push(child); return [child, child] },
})
const success = <T,>(data: T) => ({ kind: 'SUCCESS' as const, data, requestId: 'synthetic-review-ui' })
const equipment = { id: '41', equipmentNo: 'EQ-41', name: '马桶', locationId: '31', addressText: '101', facilityType: 'TOILET', enabled: true, version: 0, currentWorkOrder: null, allowedActions: [] }
const pending = { operationId: '11111111-1111-4111-8111-111111111111', operation: 'DUPLICATE_CLOSE', path: '/api/repair-requests/11/duplicate-close', method: 'POST' as const,
  payload: { operationId: '11111111-1111-4111-8111-111111111111', version: 0, equipmentId: '41', reason: '核实为同一设备重复申请' }, resourceType: 'REPAIR_REQUEST', resourceId: '11', phase: 'UNKNOWN' as const }
const person = { id: '51', name: '维修人员A', enabled: true, reviewStatus: 'APPROVED', eligible: true, refusal: [] }
const apps: Vue.App[] = []
function all(root: Node): Node[] { return [root, ...root.children.flatMap(all)] }
async function settle() { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
async function mount(query = '', recover = false) {
  const pinia = createPinia(); setActivePinia(pinia)
  useAuthStore().setConfirmedUser({ id: '91', loginName: 'synthetic_admin', name: '管理员', phone: '13800000000', role: 'ADMIN', enabled: true, reviewStatus: null, version: 0,
    capabilities: { canBrowseCampus: true, canCreateRequest: false, canWorkOnTasks: false, canManage: true } })
  if (recover) useDraftStore().action('91', 'REQUEST_REVIEW', '11').pending = structuredClone(pending)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/admin/requests/:id', component }, { path: '/equipment/new', component: { render: () => h('p') } }] })
  await router.push(`/admin/requests/11${query}`); await router.isReady()
  const root = node('root'), app = renderer.createApp(component).use(pinia).use(router); app.provide(ssrContextKey, { modules: new Set() }); app.mount(root); apps.push(app); await settle()
  return root
}
beforeEach(() => {
  vi.stubGlobal('Document', class {}); vi.stubGlobal('ShadowRoot', class {})
  api.request.mockReset().mockResolvedValue(success({ id: '11', requestNo: 'RR-11', status: 'PENDING_REVIEW', version: 0, locationMode: 'STRUCTURED', location: { id: '31' }, locationNameSnapshot: '101', workOrder: null, allowedActions: ['APPROVE_AND_ASSIGN', 'RETURN_REQUEST', 'CLOSE_DUPLICATE'] }))
  api.equipment.mockReset().mockResolvedValue(success({ records: [equipment], total: 1, page: 1, pageSize: 20 }))
  api.detail.mockReset().mockResolvedValue(success({ equipment, history: { records: [], total: 0, page: 1, pageSize: 20 }, queriedAt: '2026-10-15T00:00:00Z' }))
  api.recommend.mockReset().mockImplementation(async()=>{const result=await api.eligible();if(result.kind!=='SUCCESS')return result;const records=result.data.candidates.records.map((p:object)=>({...p,W:0,I:0,R:0,A:0,load:0,todayCompleted:0,lastAssignedAt:null,reason:'负荷排序'}));return success({...result.data,top5:records.slice(0,5),candidates:{...result.data.candidates,records},preferredTotal:result.data.eligibleTotal,businessDate:'2026-10-24'})})
  api.eligible.mockReset().mockResolvedValue(success({ candidates: { records: [person], total: 1, page: 1, pageSize: 20 }, eligibleTotal: 1, queriedAt: '2026-10-15T00:00:00Z' }))
})
afterEach(() => { for (const app of apps.splice(0)) app.unmount(); vi.unstubAllGlobals() })
it('reopening an UNKNOWN review keeps its frozen reason and selected equipment visible', async () => {
  const root = await mount('', true), nodes = all(root)
  expect(nodes.find(n => n.tag === 'textarea')?.value).toBe(pending.payload.reason)
  expect(nodes.find(n => n.tag === 'select')?.props.value).toBe('41')
  expect(useDraftStore().action('91', 'REQUEST_REVIEW', '11').pending).toEqual(pending)
})
it('loads the canonical newly added equipment even when it is outside the current page', async () => {
  api.equipment.mockResolvedValue(success({ records: [], total: 21, page: 1, pageSize: 20 }))
  const root = await mount('?createdEquipmentId=41')
  expect(api.detail).toHaveBeenCalledWith('41')
  expect(all(root).find(n => n.tag === 'select')?.props.value).toBe('41')
  expect(all(root).some(n => n.tag === 'option' && n.props.value === '41')).toBe(true)
})
it('focus_never_reads_an_empty_recovery_equipment_id',async()=>{
  const page=new EventTarget() as EventTarget & {visibilityState:string};page.visibilityState='visible';const browser=new EventTarget()
  vi.stubGlobal('document',page);vi.stubGlobal('window',browser)
  await mount();browser.dispatchEvent(new Event('focus'));await settle()
  expect(api.detail).not.toHaveBeenCalled()
})
it('refreshing a complete eligibility list clears a selected person who lost qualification', async () => {
  const root = await mount()
  const choose = all(root).find(n => n.tag === 'button' && n.props['aria-pressed'] === false)!
  ;(choose.props.onClick as () => void)(); await settle()
  expect(all(root).some(n => n.tag === 'strong' && n.text === person.name)).toBe(true)
  api.eligible.mockResolvedValue(success({ candidates: { records: [{ ...person, id: '52', name: '维修人员B' }], total: 1, page: 1, pageSize: 20 }, eligibleTotal: 1 }))
  const refresh = all(root).find(n => n.tag === 'button' && n.text === '刷新资格与推荐')!
  ;(refresh.props.onClick as () => void)(); await settle()
  expect(all(root).some(n => n.tag === 'strong' && n.text === '尚未主动选择')).toBe(true)
})
it('a now-empty second page does not clear a qualified selection from the first page', async () => {
  const people=Array.from({length:21},(_,i)=>({...person,id:String(51+i),name:i===0?person.name:`维修人员${i}`}))
  api.eligible.mockResolvedValue(success({candidates:{records:people,total:21,page:1,pageSize:100},eligibleTotal:21,queriedAt:'2026-10-24T00:00:00.000Z'}))
  const root = await mount()
  ;(all(root).find(n => n.tag === 'button' && n.props['aria-pressed'] === false)!.props.onClick as () => void)(); await settle()
  ;(all(root).find(n=>n.tag==='button' && n.text.includes('展开全部合格人员'))!.props.onClick as ()=>void)();await settle()
  api.recommend.mockResolvedValue(success({top5:[],candidates:{records:[],total:20,page:2,pageSize:20},eligibleTotal:21,preferredTotal:21,queriedAt:'2026-10-24T00:00:00.000Z',businessDate:'2026-10-24'}))
  ;(all(root).find(n => n.tag === 'button' && n.text === '下一页名单')!.props.onClick as () => void)(); await settle()
  expect(all(root).some(n => n.tag === 'strong' && n.text === person.name)).toBe(true)
})
it('choosing a technician enables approval even if the equipment service fails', async () => {
  api.equipment.mockResolvedValue({ kind: 'NETWORK_ERROR' })
  const root = await mount()
  ;(all(root).find(n => n.tag === 'button' && n.props['aria-pressed'] === false)!.props.onClick as () => void)(); await settle()
  const approve = all(root).find(n => n.tag === 'button' && n.text === '审核通过并首次派单')!
  expect(approve.props.disabled).toBe(false)
})
it('fallback_name_search_counts_and_displays_the_same_case_insensitive_results', async()=> {
  api.recommend.mockResolvedValue({kind:'READ_FAILED',message:'推荐暂不可用'})
  api.eligible.mockResolvedValue(success({candidates:{records:[{...person,name:'Maintenance Jane'}],total:1,page:1,pageSize:100},eligibleTotal:1,queriedAt:'2026-10-24T00:00:00.000Z'}))
  const root=await mount(),panel=all(root).find(n=>n.props['aria-label']==='人工选择合格维修人员')!
  ;(all(panel).find(n=>n.tag==='button' && n.text.includes('展开全部合格人员'))!.props.onClick as ()=>void)();await settle()
  const input=all(panel).find(n=>n.tag==='input')!
  ;(input.props['onUpdate:modelValue'] as (value:string)=>void)('jane')
  ;(all(panel).find(n=>n.tag==='form')!.props.onSubmit as (e:{preventDefault:()=>void})=>void)({preventDefault(){}});await settle()
  expect(all(panel).some(n=>n.tag==='p' && n.text==='符合搜索条件1名。')).toBe(true)
  expect(all(panel).some(n=>n.tag==='strong' && n.text==='Maintenance Jane')).toBe(true)
})
