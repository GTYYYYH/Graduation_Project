import { expect, it } from 'vitest'
import raw from '../../public/campus/manifest.json'
import config from '../../../config/campus.json'
import { bindManifest, createDormFloor } from './templates/DormFloor'
import { ResourceCache } from './ResourceCache'
import type { CatalogVo } from '../api/account'
import type { CampusManifest } from './types'
const manifest = { ...raw, contentVersion:'b07-dorm-v1', buildings:raw.buildings.filter(b=>b.buildingCode.startsWith('wb')),locations:raw.locations.filter(l=>l.locationCode.startsWith('wb')) } as unknown as CampusManifest
const catalog = { configVersion: config.configVersion,
  buildings: config.buildings.map((b,i) => ({ ...b, id: `building-${i}` })),
  floors: config.floors.map(f => ({ ...f, id: f.code, buildingId: `building-${config.buildings.findIndex(b => b.code === f.buildingCode)}` })),
  locations: config.locations.map((l,i) => ({ ...l, id: `location-${i}`, floorId: l.floorCode, canCreateRequest: l.reportable })) } as CatalogVo
it('same_room_label_has_distinct_ids', () => {
  const bound = bindManifest(manifest, catalog)
  const wenbo1Room101Id = bound.locations.find(l => l.location.code === 'wb1-f1-r101')?.location.id
  const wenbo2Room101Id = bound.locations.find(l => l.location.code === 'wb2-f1-r101')?.location.id
  expect(wenbo1Room101Id).toBeDefined(); expect(wenbo2Room101Id).toBeDefined()
  expect(wenbo1Room101Id).not.toBe(wenbo2Room101Id)
  expect(bound.locations.filter(l => l.location.reportable)).toHaveLength(174)
  expect(bound.locations).toHaveLength(180)
})
it('doors_and_shared_resources_have_independent_lifetimes', () => {
  const pool = new ResourceCache()
  const floors = ['wb1-f1','wb2-f1','wb3-f1'].map(code => createDormFloor(pool,{...manifest,locations:manifest.locations.filter(l=>l.floorCode===code)}))
  expect(floors.map(f=>f.entrances.size)).toEqual([12,12,12])
  const doors = floors.map((f,i)=>f.entrances.get(`wb${i+1}-f1-r101`)!)
  doors[0]!.setDoorProgress(1)
  expect(doors.map(d=>d.door.open)).toEqual([true,false,false])
  floors[0]!.dispose(); expect(pool.stats().references).toBeGreaterThan(0)
  expect(doors[1]!.door.open).toBe(false)
  floors[1]!.dispose();floors[2]!.dispose();expect(pool.stats().entries).toBe(0)
})
