import { describe, expect, it } from 'vitest'
import raw from '../../public/campus/manifest.json'
import { bindManifest, createDormFloor } from './templates/DormFloor'
import { ResourceCache } from './ResourceCache'
import type { CatalogVo } from '../api/account'
import type { CampusManifest } from './types'

const codes = ['wb1-f1-r101', 'wb1-f1-r102', 'wb1-f1-r103', 'wb1-f1-r104', 'wb1-f1-r105', 'wb1-f1-r106', 'wb1-f1-r107', 'wb1-f1-r108', 'wb1-f1-r109', 'wb1-f1-r110', 'wb1-f1-lobby', 'wb1-f1-duty']
// Preserve the original first-floor contract fixture while production expands to all floors.
const manifest = { ...raw, contentVersion:'b03-parametric-v1', buildings: raw.buildings.filter(b => b.buildingCode === 'wb1'), locations: raw.locations.filter(l => l.floorCode === 'wb1-f1') } as unknown as CampusManifest
function catalog(): CatalogVo {
  return { configVersion: 'campus-v1', buildings: [{ id: '9007199254740993', code: 'wb1', name: '文博1', type: 'DORMITORY', displayOrder: 0 }],
    floors: ['wb1-f1', 'wb1-f2', 'wb1-f3', 'wb1-f4', 'wb1-f5'].map((code, i) => ({ id: String(100 + i), code, buildingId: '9007199254740993', level: i + 1, name: `标签${i}`, displayOrder: i })),
    locations: codes.map((code, i) => ({ id: String(9007199254740994n + BigInt(i)), code, floorId: '100', type: i === 10 ? 'AREA' : 'ROOM', use: i < 10 ? 'DORM_ROOM' : i === 10 ? 'DORM_LOBBY' : 'DORM_DUTY', name: `任意名称${i}`, reportable: i < 10, canCreateRequest: i < 10, displayOrder: i })) }
}
describe('explicit model/catalog binding', () => {
  it('first_floor_has_twelve_unique_locations', () => {
    const bound = bindManifest(manifest, catalog()), locationIds = bound.locations.map(l => l.location.id)
    expect(new Set(locationIds).size).toBe(12)
    expect(bound.locations.filter(l => l.location.reportable)).toHaveLength(10)
    expect(bound.locations.filter(l => !l.location.reportable).every(l => !l.location.canCreateRequest)).toBe(true)
    expect(bound.locations[0]?.location.id).toBe('9007199254740994')
    expect(bound.locations[0]?.location.name).toBe('任意名称0')
  })
  it('config_mismatch_and_wrong_explicit_floor_are_rejected_without_guessing', () => {
    const wrong = catalog(); wrong.locations[0]!.floorId = '101'
    expect(() => bindManifest(manifest, wrong)).toThrow()
    expect(() => bindManifest({ ...manifest, configVersion: 'different' }, catalog())).toThrow()
  })
  it('duplicate_codes_or_ids_and_public_repair_permissions_are_rejected', () => {
    const ids = catalog(); ids.locations[1]!.id = ids.locations[0]!.id
    expect(() => bindManifest(manifest, ids)).toThrow()
    const duplicates = catalog(); duplicates.locations[1]!.code = duplicates.locations[0]!.code
    expect(() => bindManifest(manifest, duplicates)).toThrow()
    const permission = catalog(); permission.locations[10]!.canCreateRequest = true
    expect(() => bindManifest(manifest, permission)).toThrow()
  })
  it('each_entrance_has_separate_pivot_and_anchors_on_two_sides', () => {
    const pool = new ResourceCache(), floor = createDormFloor(pool, manifest)
    expect(floor.entrances.size).toBe(12)
    const room101 = floor.entrances.get('wb1-f1-r101')!, room106 = floor.entrances.get('wb1-f1-r106')!
    room101.setDoorProgress(1)
    expect(room101.door.open).toBe(true); expect(room106.door.open).toBe(false)
    expect(room101.group.position.z).toBeGreaterThan(0); expect(room106.group.position.z).toBeLessThan(0)
    expect(new Set([...floor.entrances.values()].map(r => r.door.pivot.uuid)).size).toBe(12)
    floor.dispose(); expect(pool.stats().entries).toBe(0)
  })
})
