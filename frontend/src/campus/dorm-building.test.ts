import { expect, it } from 'vitest'
import raw from '../../public/campus/manifest.json'
import { bindManifest, createDormFloor } from './templates/DormFloor'
import config from '../../../config/campus.json'
import type { CatalogVo } from '../api/account'
import { ResourceCache } from './ResourceCache'
import type { CampusManifest } from './types'

const manifest = { ...raw, contentVersion:'b07-dorm-v1', buildings: raw.buildings.filter(b => b.buildingCode === 'wb1'), locations: raw.locations.filter(l => l.locationCode.startsWith('wb1-')) } as unknown as CampusManifest
const catalog = { configVersion: config.configVersion,
  buildings: config.buildings.map(b => ({ ...b, id: b.code })),
  floors: config.floors.map(f => ({ ...f, id: f.code, buildingId: f.buildingCode })),
  locations: config.locations.map(l => ({ ...l, id: l.code, floorId: l.floorCode, canCreateRequest: l.reportable })) } as CatalogVo
it('all_five_floors_bind_by_explicit_catalog_relationships', () => {
  const bound = bindManifest(manifest, catalog)
  expect(bound.locations).toHaveLength(60)
  expect(new Set(bound.locations.map(l => l.location.floorId)).size).toBe(5)
  const wrong = structuredClone(catalog)
  wrong.locations.find(l => l.code === 'wb1-f2-r201')!.floorId = 'wb1-f1'
  expect(() => bindManifest(manifest, wrong)).toThrow()
})
it('complete_building_has_fifty_eight_rooms', () => {
  const dormRooms = manifest.locations.filter(l => l.locationCode.startsWith('wb1-') && l.templateKey === 'dorm-room')
  expect(dormRooms.length).toBe(58)
  expect(manifest.locations.filter(l => l.locationCode.startsWith('wb1-'))).toHaveLength(60)
  const building = manifest.buildings.find(b => b.buildingCode === 'wb1')!
  expect(building.floorCodes).toHaveLength(5)
  expect(building.floorCodes.map(code => dormRooms.filter(l => l.floorCode === code).length)).toEqual([10, 12, 12, 12, 12])
})
it('standard_floor_entrances_are_independent_and_released', () => {
  const locations = manifest.locations.filter(l => l.floorCode === 'wb1-f2')
  expect(locations).toHaveLength(12)
  const pool = new ResourceCache(), floor = createDormFloor(pool, { ...manifest, locations })
  expect(floor.entrances.size).toBe(12)
  const entries = [...floor.entrances.values()]
  entries[0]!.setDoorProgress(1)
  expect(entries[0]!.door.open).toBe(true)
  expect(entries.slice(1).every(e => !e.door.open)).toBe(true)
  expect(new Set(entries.map(e => e.door.pivot.uuid)).size).toBe(12)
  expect(new Set(locations.map(l => l.origin[0])).size).toBe(6)
  expect(locations.filter(l => l.origin[2] > 0)).toHaveLength(6)
  expect(locations.filter(l => l.origin[2] < 0)).toHaveLength(6)
  floor.dispose(); expect(pool.stats().entries).toBe(0)
})
