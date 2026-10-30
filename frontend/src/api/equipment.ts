import { businessMutation, businessRead, isInstant, isPage, isVersion, object, queryString } from './business'
import { isOrderSummary } from './workorder'
import type { OrderSummary } from './workorder'
import { isEquipmentReference } from './repair-request'
import type { EquipmentReference } from './repair-request'
import type { Id, PageResult } from './common'
export const facilityNames: Record<string, string> = { BUNK_DESK: '床桌组', TOILET: '马桶', SHOWER: '淋浴', BASIN: '洗手盆', AIR_CONDITIONER: '空调', AIR_CONDITIONER_GROUP: '空调组', LIGHT_GROUP: '灯组', SOCKET_GROUP: '插座组', CLASS_DESK_CHAIR_GROUP: '课桌椅组', COMPUTER: '电脑', PROJECTOR: '投影仪', OFFICE_DESK_CHAIR_GROUP: '办公桌椅组', STANDING_AC_GROUP: '立式空调组', BASKETBALL_HOOP_GROUP: '篮球架组' }
export const allowedFacilities: Record<string, string[]> = { DORM_ROOM: ['BUNK_DESK', 'TOILET', 'SHOWER', 'BASIN', 'AIR_CONDITIONER', 'LIGHT_GROUP', 'SOCKET_GROUP'], CLASSROOM: ['CLASS_DESK_CHAIR_GROUP', 'AIR_CONDITIONER_GROUP', 'LIGHT_GROUP', 'SOCKET_GROUP', 'COMPUTER', 'PROJECTOR'], OFFICE: ['OFFICE_DESK_CHAIR_GROUP', 'AIR_CONDITIONER', 'LIGHT_GROUP', 'SOCKET_GROUP'], CANTEEN_AREA: ['STANDING_AC_GROUP', 'LIGHT_GROUP'], GYM_GALLERY: ['STANDING_AC_GROUP', 'LIGHT_GROUP'], GYM_COURT: ['STANDING_AC_GROUP', 'LIGHT_GROUP', 'BASKETBALL_HOOP_GROUP'] }
export interface Equipment extends EquipmentReference { addressText: string; facilityType: string; enabled: boolean; version: number; currentWorkOrder: OrderSummary | null; allowedActions: string[] }
export interface EquipmentDetail { equipment: Equipment; history: PageResult<OrderSummary>; queriedAt: string }
export function isEquipment(v: unknown): v is Equipment { return isEquipmentReference(v) && object(v) && typeof v.addressText === 'string' && Object.keys(facilityNames).includes(String(v.facilityType)) && typeof v.enabled === 'boolean' && isVersion(v.version) && (v.currentWorkOrder === null || isOrderSummary(v.currentWorkOrder)) && Array.isArray(v.allowedActions) && v.allowedActions.every(a => typeof a === 'string') }
export interface EquipmentQuery { page?: number; pageSize?: number; keyword?: string; buildingId?: Id; floorId?: Id; locationId?: Id; enabled?: boolean; facilityType?: string }
export const fetchEquipment = (query: EquipmentQuery) => businessRead(`/api/equipment?${queryString({ ...query })}`, (v): v is PageResult<Equipment> => isPage(v, isEquipment))
export const fetchEquipmentDetail = (id: Id, page = 1) => businessRead(`/api/equipment/${encodeURIComponent(id)}?${queryString({ page, pageSize: 20 })}`, (v): v is EquipmentDetail => object(v) && isEquipment(v.equipment) && isPage(v.history, isOrderSummary) && isInstant(v.queriedAt))
export const saveEquipment = (body: Record<string, unknown>, id?: Id) => businessMutation(id ? `/api/equipment/${encodeURIComponent(id)}` : '/api/equipment', id ? 'PATCH' : 'POST', body, 'EQUIPMENT', id)
