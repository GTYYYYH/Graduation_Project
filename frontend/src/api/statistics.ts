import { businessRead, isInstant, isPage, object, queryString } from './business'
import { isId } from './account'
import { isEquipmentReference } from './repair-request'
import type { EquipmentReference } from './repair-request'
import type { Id, PageResult, RequestOptions } from './common'
import { orderStatusNames } from './workorder'
import type { OrderStatus } from './workorder'

export type StatisticsMetric = 'COMPLETED' | 'RESPONSE_DURATION' | 'REPAIR_DURATION' | 'DAILY_COMPLETED' | 'EQUIPMENT_FREQUENCY' | 'CURRENT_STATE'
export type StatisticsTab = 'overview' | 'analysis'
export interface StatisticsDetailQuery { metric: StatisticsMetric; month?: string; date?: string; equipmentId?: Id; unlinkedEquipment?: true; status?: OrderStatus; page?: number; pageSize?: number; keyword?: string }
export type StatisticsDrill = Pick<StatisticsDetailQuery, 'metric' | 'date' | 'equipmentId' | 'unlinkedEquipment' | 'status'>
export interface StatisticsVo {
  month: string; timeZone: 'Asia/Shanghai'; completedCount: number; sampleCount: number
  averageResponseSeconds: number | null; averageRepairSeconds: number | null; invalidTimeSampleCount: number
  dailyCompleted: { date: string; count: number }[]; equipmentFrequencies: { equipment: EquipmentReference | null; count: number }[]
  stateScope: 'ALL_CURRENT_STATE'; stateCounts: Record<OrderStatus, number>; queriedAt: string
}
export interface WorkOrderStatisticVo {
  id: Id; workOrderNo: string; reportedEquipmentName: string; equipment: EquipmentReference | null; status: OrderStatus
  firstSubmittedAt: string; createdAt: string; acceptedAt: string | null; completedAt: string | null
  responseSeconds: number | null; repairSeconds: number | null; hasInvalidTime: boolean
}
export interface StatisticsDetailVo { metric: StatisticsMetric; month: string | null; scope: 'MONTH_COMPLETED' | 'MONTH_CREATED' | 'ALL_CURRENT_STATE'; records: PageResult<WorkOrderStatisticVo>; queriedAt: string }
export const statisticsMetricNames: Record<StatisticsMetric, string> = { COMPLETED: '月度维修数量', RESPONSE_DURATION: '响应时长样本', REPAIR_DURATION: '维修时长样本', DAILY_COMPLETED: '每日完成工单', EQUIPMENT_FREQUENCY: '设备故障频次', CURRENT_STATE: '全部工单·当前状态' }
const count = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0
const duration = (v: unknown): v is number | null => v === null || (typeof v === 'number' && Number.isFinite(v) && v >= 0)
const instantOrNull = (v: unknown) => v === null || isInstant(v)
export const isStatisticsMonth = (v: unknown): v is string => typeof v === 'string' && /^[1-9][0-9]{3}-(0[1-9]|1[0-2])$/.test(v) && Number(v.slice(0, 4)) <= 9998
export function shanghaiMonth(now = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit' }).formatToParts(now)
  return `${parts.find(p => p.type === 'year')!.value}-${parts.find(p => p.type === 'month')!.value}`
}
export function formatSeconds(value: number | null) {
  if (value === null) return '—'
  const [divisor, unit] = value >= 86400 ? [86400, 'd'] : value >= 3600 ? [3600, 'h'] : value >= 60 ? [60, 'min'] : [1, 's']
  return `${(value / Number(divisor)).toLocaleString('zh-CN', { maximumFractionDigits: 3 })}${unit}`
}
export const statisticTime = (value: string | null) => value === null ? '—' : new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })
export function isStatistics(v: unknown): v is StatisticsVo {
  return object(v) && isStatisticsMonth(v.month) && v.timeZone === 'Asia/Shanghai' && count(v.completedCount) && v.sampleCount === v.completedCount
    && duration(v.averageResponseSeconds) && duration(v.averageRepairSeconds) && count(v.invalidTimeSampleCount) && v.invalidTimeSampleCount <= v.completedCount
    && Array.isArray(v.dailyCompleted) && v.dailyCompleted.every(d => object(d) && typeof d.date === 'string' && d.date.startsWith(`${v.month}-`) && count(d.count))
    && Array.isArray(v.equipmentFrequencies) && v.equipmentFrequencies.every(e => object(e) && (e.equipment === null || isEquipmentReference(e.equipment)) && count(e.count))
    && v.stateScope === 'ALL_CURRENT_STATE' && object(v.stateCounts) && Object.keys(v.stateCounts).length === 5 && Object.keys(orderStatusNames).every(s => count((v.stateCounts as Record<string, unknown>)[s])) && isInstant(v.queriedAt)
}
export function isWorkOrderStatistic(v: unknown): v is WorkOrderStatisticVo {
  return object(v) && isId(v.id) && typeof v.workOrderNo === 'string' && typeof v.reportedEquipmentName === 'string' && (v.equipment === null || isEquipmentReference(v.equipment))
    && Object.keys(orderStatusNames).includes(String(v.status)) && isInstant(v.firstSubmittedAt) && isInstant(v.createdAt) && instantOrNull(v.acceptedAt) && instantOrNull(v.completedAt)
    && duration(v.responseSeconds) && duration(v.repairSeconds) && typeof v.hasInvalidTime === 'boolean'
}
export function statisticsDetailParams(query: StatisticsDetailQuery) {
  const base = { metric: query.metric, page: query.page ?? 1, pageSize: query.pageSize ?? 20, keyword: query.keyword }
  if (query.metric === 'CURRENT_STATE') return { ...base, status: query.status }
  return { ...base, month: query.month, ...(query.metric === 'DAILY_COMPLETED' ? { date: query.date } : {}), ...(query.metric === 'EQUIPMENT_FREQUENCY' ? { equipmentId: query.equipmentId, unlinkedEquipment: query.unlinkedEquipment } : {}) }
}
export const fetchStatisticsDashboard = (month: string, options: RequestOptions = {}) => businessRead(`/api/statistics/dashboard?${queryString({ month })}`, (v): v is StatisticsVo => isStatistics(v) && v.month === month, options)
export function fetchStatisticsDetails(query: StatisticsDetailQuery, options: RequestOptions = {}) {
  const scope = query.metric === 'CURRENT_STATE' ? 'ALL_CURRENT_STATE' : query.metric === 'EQUIPMENT_FREQUENCY' ? 'MONTH_CREATED' : 'MONTH_COMPLETED'
  return businessRead(`/api/statistics/details?${queryString(statisticsDetailParams(query))}`, (v): v is StatisticsDetailVo => object(v) && v.metric === query.metric && v.scope === scope
    && v.month === (scope === 'ALL_CURRENT_STATE' ? null : query.month) && isPage(v.records, isWorkOrderStatistic) && isInstant(v.queriedAt), options)
}
