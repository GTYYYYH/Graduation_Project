import { afterEach, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { createRenderer, effectScope, ref, nextTick, ssrContextKey } from 'vue'
import { compile } from '@vue/compiler-dom'
import { compileScript, parse } from '@vue/compiler-sfc'
import ts from 'typescript'
import { createMemoryHistory, createRouter } from 'vue-router'
import StatisticsView from './StatisticsView.vue'
import viewSource from './StatisticsView.vue?raw'
import StatisticsDetailsView from './StatisticsDetailsView.vue'
import detailsSource from './StatisticsDetailsView.vue?raw'
import StatisticsCharts from '../../components/statistics/StatisticsCharts.vue'
import chartsSource from '../../components/statistics/StatisticsCharts.vue?raw'
import { renderToString } from '@vue/server-renderer'
import WorkOrderHistoryContent from '../../components/workorder/WorkOrderHistoryContent.vue'
import type { OrderDetail } from '../../api/workorder'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../../stores/auth'
import { useStatistics } from '../../composables/useStatistics'
import type { StatisticsVo } from '../../api/statistics'
import { formatSeconds, isStatistics, shanghaiMonth, statisticsDetailParams } from '../../api/statistics'
import { queryString } from '../../api/business'
import { businessReturnTo } from '../../router/businessNavigation'
const api = vi.hoisted(() => ({ dashboard: vi.fn(), details: vi.fn() }))
vi.mock('../../api/statistics', async original => ({ ...await original<object>(), fetchStatisticsDashboard: api.dashboard, fetchStatisticsDetails: api.details }))
const scopes: ReturnType<typeof effectScope>[] = []
const apps: Vue.App[] = []
afterEach(() => { apps.splice(0).forEach(a => a.unmount()); scopes.splice(0).forEach(s => s.stop()); vi.unstubAllGlobals(); vi.resetAllMocks() })
function deferred<T>() { let resolve!: (v: T) => void; const promise = new Promise<T>(r => { resolve = r }); return { promise, resolve } }
function statisticsFixture(month: string): StatisticsVo {
  return { month, timeZone: 'Asia/Shanghai', completedCount: 0, sampleCount: 0, averageResponseSeconds: null, averageRepairSeconds: null, invalidTimeSampleCount: 0,
    dailyCompleted: Array.from({ length: month.endsWith('09') ? 30 : 31 }, (_, i) => ({ date: `${month}-${String(i + 1).padStart(2, '0')}`, count: 0 })), equipmentFrequencies: [],
    stateScope: 'ALL_CURRENT_STATE', stateCounts: { PENDING_ACCEPT: 0, REASSIGN_PENDING: 0, IN_PROGRESS: 0, PENDING_ACCEPTANCE: 0, COMPLETED: 0 }, queriedAt: '2026-10-27T00:00:00.000Z' }
}
const success = (month: string) => ({ kind: 'SUCCESS' as const, data: statisticsFixture(month), requestId: 'synthetic-statistics' })
function setup() {
  setActivePinia(createPinia()); useAuthStore().setConfirmedUser({ id: '1', loginName: 'synthetic', name: '合成管理员', phone: '13800000000', role: 'ADMIN', enabled: true, reviewStatus: null, version: 0, capabilities: { canBrowseCampus: true, canCreateRequest: false, canWorkOnTasks: false, canManage: true } })
  const month = ref('2026-09'), scope = effectScope(); scopes.push(scope)
  const read = scope.run(() => useStatistics(month))!
  return { month, read }
}
async function settle() { await new Promise(resolve => setTimeout(resolve, 0)); await nextTick() }
it('stale_month_response_is_discarded', async () => {
  const september = deferred<ReturnType<typeof success>>(), october = deferred<ReturnType<typeof success>>()
  api.dashboard.mockImplementation((month: string) => month === '2026-09' ? september.promise : october.promise)
  const { month, read } = setup(); await settle()
  month.value = '2026-10'; await settle()
  october.resolve(success('2026-10')); await settle()
  september.resolve(success('2026-09')); await settle()
  const displayedMonth = read.displayedMonth.value
  expect(displayedMonth).toBe('2026-10')
})
it('same_month_failure_keeps_labeled_stale_data_and_switch_month_clears_it', async () => {
  api.dashboard.mockResolvedValue(success('2026-09'))
  const { month, read } = setup(); await settle()
  api.dashboard.mockResolvedValue({ kind: 'READ_FAILED', message: '合成网络故障' })
  await read.load(); expect(read.displayedMonth.value).toBe('2026-09'); expect(read.stale.value).toBe(true); expect(read.message.value).toContain('合成网络故障')
  month.value = '2026-10'; await settle()
  expect(read.data.value).toBeNull(); expect(read.displayedMonth.value).toBeNull()
})
it('forbidden_and_identity_loss_clear_statistics_and_reject_late_success', async () => {
  api.dashboard.mockResolvedValue(success('2026-09')); const { read } = setup(); await settle()
  api.dashboard.mockResolvedValue({ kind: 'HTTP_ERROR', status: 403, response: { code: 'FORBIDDEN', message: '无统计权限', data: null, requestId: 'synthetic-denied' } })
  await read.load(); expect(read.data.value).toBeNull(); expect(read.stale.value).toBe(false)
  const late = deferred<ReturnType<typeof success>>(); api.dashboard.mockReturnValue(late.promise); const pending = read.load(); await settle()
  useAuthStore().clearIdentity(); late.resolve(success('2026-09')); await pending
  expect(read.data.value).toBeNull(); expect(read.displayedMonth.value).toBeNull()
})
it('role_loss_with_same_identity_clears_statistics', async () => {
  api.dashboard.mockResolvedValue(success('2026-09')); const { read } = setup(); await settle()
  const auth = useAuthStore(); auth.setConfirmedUser({ ...auth.currentUser!, role: 'REPORTER', version: 1, capabilities: { canBrowseCampus: true, canCreateRequest: true, canWorkOnTasks: false, canManage: false } })
  expect(read.data.value).toBeNull()
})
it('current_state_http_scope_never_contains_dashboard_month_or_equipment_filters', () => {
  const params = new URLSearchParams(queryString(statisticsDetailParams({ metric: 'CURRENT_STATE', month: '2026-10', date: '2026-10-01', equipmentId: '99', unlinkedEquipment: true, status: 'IN_PROGRESS', keyword: '演示', page: 3 })))
  expect(params.has('month')).toBe(false); expect(params.has('date')).toBe(false); expect(params.has('equipmentId')).toBe(false); expect(params.has('unlinkedEquipment')).toBe(false)
  expect(params.get('status')).toBe('IN_PROGRESS'); expect(params.get('page')).toBe('3')
})
it('null_samples_schema_and_shanghai_month_are_explicit', () => {
  expect(formatSeconds(null)).toBe('—'); expect(formatSeconds(7200)).toBe('2h')
  expect(formatSeconds(1)).toBe('1s'); expect(formatSeconds(59)).toBe('59s')
  expect(formatSeconds(60)).toBe('1min'); expect(formatSeconds(3599)).toBe('59.983min')
  expect(formatSeconds(3600)).toBe('1h'); expect(formatSeconds(86400)).toBe('1d')
  const sample = statisticsFixture('2026-10'); expect(isStatistics(sample)).toBe(true)
  expect(isStatistics({ ...sample, averageResponseSeconds: -1 })).toBe(false); expect(isStatistics({ ...sample, invalidTimeSampleCount: 1 })).toBe(false)
  expect(isStatistics({ ...sample, stateScope: 'MONTH_CREATED' })).toBe(false)
  expect(shanghaiMonth(new Date('2026-09-30T15:59:59.999Z'))).toBe('2026-09'); expect(shanghaiMonth(new Date('2026-09-30T16:00:00Z'))).toBe('2026-10')
})
it('statistics_detail_return_keeps_month_tab_metric_filters_and_page', () => {
  const path = '/admin/statistics/details?month=2026-10&tab=analysis&metric=CURRENT_STATE&status=IN_PROGRESS&keyword=%E6%BC%94%E7%A4%BA&page=3&pageSize=20'
  expect(businessReturnTo(path, '/work-orders')).toBe(path)
  expect(businessReturnTo('/admin/statistics/details?metric=COMPLETED&keyword=%E7%A9%BA%E8%B0%83%231', '/work-orders')).toBe('/admin/statistics/details?metric=COMPLETED&keyword=%E7%A9%BA%E8%B0%83%231')
  expect(businessReturnTo('/admin/statistics/details?returnTo=https://invalid.example', '/work-orders')).toBe('/work-orders')
})

// Use the project's existing custom Vue renderer pattern; the real chart component's
// accessible readings render here. Canvas drawing itself is checked in a real browser.
interface Surface { tag: string; text: string; value: unknown; props: Record<string, unknown>; children: Surface[]; parent: Surface | null; tagName: string; multiple: boolean; selectedIndex: number; options: Surface[]; addEventListener: () => void; getRootNode: () => object }
function surface(tag: string, text = ''): Surface { const n = { tag, text, value: '', props: {}, children: [], parent: null, tagName: tag.toUpperCase(), multiple: false, selectedIndex: 0, addEventListener() {}, getRootNode: () => ({}) } as unknown as Surface; Object.defineProperty(n, 'options', { get: () => nodes(n).filter(c => c.tag === 'option') }); return n }
const nodes = (root: Surface): Surface[] => [root, ...root.children.flatMap(nodes)]
const visibleText = (n: Surface): string => n.text + n.children.map(visibleText).join('')
const renderer = createRenderer<Surface, Surface>({ createElement: tag => surface(tag), createText: t => surface('#text', t), createComment: t => surface('#comment', t), insert(child, parent, anchor = null) { if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1); child.parent = parent; const at = anchor ? parent.children.indexOf(anchor) : -1; if (at < 0) parent.children.push(child); else parent.children.splice(at, 0, child) }, remove(child) { if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1) }, setText(n, t) { n.text = t }, setElementText(n, t) { n.text = t; n.children = [] }, patchProp(n, key, _old, value) { n.props[key] = value; if (key === 'value') n.value = value }, parentNode: n => n.parent, nextSibling: n => n.parent?.children[n.parent.children.indexOf(n) + 1] ?? null, insertStaticContent(t, parent) { const n = surface('#static', t); n.parent = parent; parent.children.push(n); return [n, n] } })
function client(view: object, source: string) { const descriptor = parse(source).descriptor; return { ...view, render: new Function('Vue', ts.transpileModule(compile(descriptor.template!.content, { mode: 'function', prefixIdentifiers: true, bindingMetadata: compileScript(descriptor, { id: 'statistics-page-test' }).bindings }).code, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText)(Vue) } }
async function mountStatistics(path: string) {
  vi.stubGlobal('Document', class {}); vi.stubGlobal('ShadowRoot', class {})
  setActivePinia(createPinia()); const auth = useAuthStore(); auth.setConfirmedUser({ id: '1', loginName: 'synthetic', name: '合成管理员', phone: '13800000000', role: 'ADMIN', enabled: true, reviewStatus: null, version: 0, capabilities: { canBrowseCampus: true, canCreateRequest: false, canWorkOnTasks: false, canManage: true } })
  const pinia = createPinia(); setActivePinia(pinia); useAuthStore().setConfirmedUser(auth.currentUser!)
  Object.assign(StatisticsCharts, client(StatisticsCharts, chartsSource))
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/admin/statistics', component: client(StatisticsView, viewSource) }, { path: '/admin/statistics/details', component: client(StatisticsDetailsView, detailsSource) }, { path: '/work-orders/:id', component: { render: () => null } }] })
  await router.push(path); await router.isReady(); const root = surface('root'), app = renderer.createApp({ render: () => Vue.h(Vue.resolveComponent('RouterView')) }).use(pinia).use(router); app.provide(ssrContextKey, { modules: new Set() }); apps.push(app); app.mount(root); await settle(); return { root, router }
}
it('mounted_month_tabs_and_null_readings_use_the_successful_response_month', async () => {
  const september = deferred<ReturnType<typeof success>>(), october = deferred<ReturnType<typeof success>>()
  api.dashboard.mockImplementation((month: string) => month === '2026-09' ? september.promise : october.promise)
  const { root, router } = await mountStatistics('/admin/statistics?month=2026-09&tab=overview')
  const picker = nodes(root).find(n => n.tag === 'input' && n.props.type === 'month')!
  await (picker.props.onChange as (e: unknown) => unknown)({ target: { value: '2026-10' } }); await settle()
  october.resolve(success('2026-10')); await settle(); september.resolve(success('2026-09')); await settle()
  expect(visibleText(root)).toContain('当前展示月份：2026-10'); expect(visibleText(root)).not.toContain('当前展示月份：2026-09'); expect(visibleText(root)).toContain('暂无完成样本'); expect(visibleText(root)).toContain('平均维修时长—')
  const analysis = nodes(root).find(n => n.tag === 'button' && visibleText(n) === '分析')!
  await (analysis.props.onClick as () => unknown)(); await settle()
  expect(router.currentRoute.value.query.month).toBe('2026-10'); expect(visibleText(root)).toContain('暂无记录：本月没有生成工单')
})
it('mounted_details_keep_applied_filters_and_page_in_the_work_order_return_link', async () => {
  api.details.mockImplementation((q: { page?: number }) => Promise.resolve({ kind: 'SUCCESS', requestId: 'synthetic-detail', data: { metric: 'CURRENT_STATE', month: null, scope: 'ALL_CURRENT_STATE', queriedAt: '2026-10-27T00:00:00.000Z', records: { page: q.page ?? 1, pageSize: 20, total: 45, records: [{ id: '99', workOrderNo: 'WO-SYNTHETIC', reportedEquipmentName: '原报修设备', equipment: null, status: 'IN_PROGRESS', firstSubmittedAt: '2026-10-12T00:00:00.000Z', createdAt: '2026-10-12T01:00:00.000Z', acceptedAt: '2026-10-12T02:00:00.000Z', completedAt: null, responseSeconds: 7200, repairSeconds: null, hasInvalidTime: false }] } } }))
  const { root, router } = await mountStatistics('/admin/statistics/details?month=2026-10&tab=analysis&metric=CURRENT_STATE&status=IN_PROGRESS&keyword=已应用&page=1')
  expect(visibleText(root)).toContain('不按月份过滤'); expect(visibleText(root)).toContain('未关联台账 · 原报修名称：原报修设备')
  const input = nodes(root).find(n => n.tag === 'input')!; (input.props['onUpdate:modelValue'] as (v: string) => void)('未提交')
  await (nodes(root).find(n => n.tag === 'button' && visibleText(n) === '下一页')!.props.onClick as () => unknown)(); await settle()
  expect(router.currentRoute.value.query.keyword).toBe('已应用'); expect(router.currentRoute.value.query.page).toBe('2')
  const back = router.currentRoute.value.fullPath, link = nodes(root).find(n => n.tag === 'a' && visibleText(n) === 'WO-SYNTHETIC')!
  await (link.props.onClick as (e: unknown) => unknown)({ button: 0, preventDefault() {}, currentTarget: { getAttribute: () => null } }); await settle()
  expect(router.currentRoute.value.path).toBe('/work-orders/99'); expect(router.currentRoute.value.query.returnTo).toBe(back)
  await router.push(businessReturnTo(router.currentRoute.value.query.returnTo, '/work-orders')); await settle()
  expect(router.currentRoute.value.query.metric).toBe('CURRENT_STATE'); expect(router.currentRoute.value.query.status).toBe('IN_PROGRESS'); expect(router.currentRoute.value.query.page).toBe('2'); expect(router.currentRoute.value.query.tab).toBe('analysis')
})
it('original_work_order_round_times_are_visible_for_independent_repair_recalculation', async () => {
  const who = { id: '1', name: '合成维修人' }, reporter = { id: '2', name: '合成报修人' }
  const detail: OrderDetail = { id: '99', workOrderNo: 'WO-SYNTHETIC', requestId: '3', requestNo: 'RR-SYNTHETIC', reportedEquipmentName: '原报修设备', equipment: null, status: 'COMPLETED', locationMode: 'LEGACY_TEXT', location: null, locationNameSnapshot: null, createdAt: '2026-10-15T01:00:00.000Z', assignedTechnician: who, acceptedTechnician: who, version: 7, round: 2, isRework: false, allowedActions: [], historyScope: 'FULL', firstSubmittedAt: '2026-10-15T00:00:00.000Z', acceptedAt: '2026-10-15T03:00:00.000Z', completedAt: '2026-10-15T08:00:00.000Z', queriedAt: '2026-10-15T08:00:00.000Z', reporter,
    requestMaterial: { id: '4', submissionNo: 1, equipmentName: '原报修设备', equipmentCategory: null, locationMode: 'LEGACY_TEXT', location: null, locationNameSnapshot: null, addressText: '合成地址', description: '合成故障', contactName: '合成联系人', contactPhone: '13800000000', attachments: [], submittedAt: '2026-10-15T00:00:00.000Z' }, assignments: [], events: [],
    rounds: [{ id: '11', round: 1, technician: who, startedAt: '2026-10-15T03:00:00.000Z', submittedAt: '2026-10-15T04:00:00.000Z', result: '首轮结果', attachments: [], progress: [], acceptance: { passed: false, reason: '返工', reporter, acceptedAt: '2026-10-15T07:00:00.000Z' } }, { id: '12', round: 2, technician: who, startedAt: '2026-10-15T07:00:00.000Z', submittedAt: '2026-10-15T08:00:00.000Z', result: '第二轮结果', attachments: [], progress: [], acceptance: { passed: true, reason: null, reporter, acceptedAt: '2026-10-15T08:00:00.000Z' } }] }
  const html = await renderToString(Vue.createSSRApp(WorkOrderHistoryContent, { detail }))
  expect(html).toContain('开始：2026/10/15 11:00:00')
  expect(html).toContain('提交：2026/10/15 12:00:00')
  expect(html).toContain('验收：2026/10/15 15:00:00')
  expect(html).toContain('开始：2026/10/15 15:00:00')
  expect(html).toContain('提交：2026/10/15 16:00:00')
})
it('invalid_time_average_is_distinct_from_no_completed_samples', async () => {
  const data = statisticsFixture('2026-10')
  data.completedCount = 2; data.sampleCount = 2; data.invalidTimeSampleCount = 1; data.averageRepairSeconds = 5400
  data.dailyCompleted[0]!.count = 2; data.stateCounts.COMPLETED = 2
  const html = await renderToString(Vue.createSSRApp(StatisticsCharts, { data, tab: 'overview' }))
  expect(html).toContain('1 张完成样本存在时间异常')
  expect(html).not.toContain('暂无完成样本')
  expect(html).toContain('1.5h')
  expect(html).toContain('平均响应时长')
  expect(html).toContain('—')
})
