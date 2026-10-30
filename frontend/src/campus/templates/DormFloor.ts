import { Group } from 'three'
import type { CatalogVo } from '../../api/account'
import type { CampusBinding, CampusManifest } from '../types'
import type { ResourceCache } from '../ResourceCache'
import { COLORS as C, GeometryKit } from '../GeometryKit'
export interface Entrance { group: Group; door: { open: boolean; pivot: Group }; setDoorProgress(progress: number): void }
export function bindManifest(manifest: CampusManifest, catalog: CatalogVo): CampusBinding {
  const invalid = (): never => { throw new Error('3D清单与真实目录不一致，请保留位置选择并核对配置版本') }
  if (!manifest || manifest.configVersion !== catalog.configVersion || !Array.isArray(manifest.buildings) || !manifest.buildings.length
    || !Array.isArray(manifest.locations) || !Array.isArray(manifest.resources)) return invalid()
  if(!['b03-parametric-v1','b07-dorm-v1','b07-campus-v1'].includes(manifest.contentVersion))return invalid()
  if(manifest.contentVersion==='b07-campus-v1' && (manifest.buildings.length!==10 || manifest.locations.length!==296 || catalog.buildings.length!==10 || catalog.floors.length!==41 || catalog.locations.length!==296))return invalid()
  for (const r of manifest.resources) if (!r.url.startsWith('/campus/') || /\.\.|[\\?#]/.test(r.url)) return invalid()
  const unique = <T>(values: T[], key: (v: T) => string) => new Set(values.map(key)).size === values.length
  for (const items of [catalog.buildings, catalog.floors, catalog.locations]) if (!unique<{ id: string; code: string }>(items, v => v.id) || !unique<{ id: string; code: string }>(items, v => v.code)) return invalid()
  if (!unique(manifest.buildings, v => v.buildingCode) || !unique(manifest.locations, v => v.locationCode)
    || !unique(manifest.locations.filter(v => v.doorKey !== null), v => v.doorKey!)) return invalid()
  const finiteVector = (v: unknown) => Array.isArray(v) && v.length === 3 && v.every(n => typeof n === 'number' && Number.isFinite(n) && Math.abs(n) < 1000)
  const buildings = manifest.buildings.map(definition => {
    const building = catalog.buildings.find(b => b.code === definition.buildingCode)
    const expectedFloors = building?.type === 'CANTEEN' ? 3 : building?.type === 'GYM' ? 2 : 5
    if (!building || !['DORMITORY','TEACHING','CANTEEN','GYM'].includes(building.type) || !Array.isArray(definition.floorCodes) || definition.floorCodes.length !== expectedFloors
      || new Set(definition.floorCodes).size !== expectedFloors || (definition.origin && !finiteVector(definition.origin))) return invalid()
    return building
  })
  const floors = manifest.buildings.flatMap((definition, index) => definition.floorCodes.map((code, i) => {
    const f = catalog.floors.find(f => f.code === code)
    if (!f || f.buildingId !== buildings[index]!.id || f.level !== i + 1) return invalid()
    return f
  }))
  if (!unique(floors,f=>f.id)) return invalid()
  const locations = manifest.locations.map(geometry => {
    const location = catalog.locations.find(l => l.code === geometry.locationCode), floor = floors.find(f => f.code === geometry.floorCode)
    const uses = { 'dorm-room': ['DORM_ROOM'], 'dorm-lobby': ['DORM_LOBBY'], 'dorm-duty': ['DORM_DUTY'], classroom:['CLASSROOM'],office:['OFFICE'],'teaching-restroom':['RESTROOM_MALE','RESTROOM_FEMALE'],'teaching-lobby':['TEACHING_LOBBY'],cafeteria:['CANTEEN_AREA'],'gym-court':['GYM_COURT'],'gym-gallery':['GYM_GALLERY'] }[geometry.templateKey]
    if (!location || !floor || location.floorId !== floor.id || !uses?.includes(location.use)
      || location.reportable !== ['DORM_ROOM','CLASSROOM','OFFICE','CANTEEN_AREA','GYM_COURT','GYM_GALLERY'].includes(location.use) || (!location.reportable && location.canCreateRequest)
      || !geometry.entryAnchor || !geometry.interiorAnchor || !finiteVector(geometry.origin) || !Number.isFinite(geometry.rotationY)
      || !finiteVector(geometry.entryAnchor.position) || !finiteVector(geometry.entryAnchor.target)
      || !finiteVector(geometry.interiorAnchor.position) || !finiteVector(geometry.interiorAnchor.target)) return invalid()
    return { location, geometry }
  })
  for (const floor of floors) {
    const entries = locations.filter(l => l.location.floorId === floor.id)
    const type = buildings.find(b => b.id === floor.buildingId)?.type
    if(type==='CANTEEN'||type==='GYM') {
      const use=type==='CANTEEN'?'CANTEEN_AREA':floor.level===1?'GYM_COURT':'GYM_GALLERY'
      if(entries.length!==1||entries[0]!.location.use!==use||entries[0]!.location.type!=='AREA')return invalid()
      continue
    }
    if (buildings.find(b => b.id === floor.buildingId)?.type === 'TEACHING') {
      if(entries.length!==7 || entries.filter(l=>l.location.use==='CLASSROOM').length!==(floor.level===1?3:4)
        || entries.filter(l=>l.location.use==='OFFICE').length!==1 || entries.filter(l=>l.location.use.startsWith('RESTROOM_')).length!==2
        || entries.filter(l=>l.location.use==='TEACHING_LOBBY').length!==(floor.level===1?1:0)) return invalid()
      if (new Set(entries.map(l=>l.geometry.layoutSlot)).size !== 7) return invalid()
      for (const {location,geometry:g} of entries) {
        const slot=g.layoutSlot
        if (!Number.isInteger(slot) || slot!<1 || slot!>7 || !Array.isArray(g.roomSize)) return invalid()
        const left=slot!<=4, count=left?4:3, row=left?slot!-1:slot!-5
        if (g.roomSize.length!==2 || Math.abs(g.roomSize[0]-74/count)>1e-6 || Math.abs(g.roomSize[1]-13.6)>1e-6
          || Math.abs(g.origin[0]-(37-(row+.5)*74/count))>1e-6 || g.origin[2] !== (left?-3.4:3.4)
          || g.rotationY !== (left?Math.PI:0)
          || (slot===1 && location.use!=='RESTROOM_MALE') || (slot===4 && location.use!=='RESTROOM_FEMALE')
          || (slot===6 && floor.level===1 && location.use!=='TEACHING_LOBBY')) return invalid()
      }
      continue
    }
    if (manifest.locations.length === 12 && floors.length === 5 && floor.level !== 1) continue
    if (entries.length !== 12 || entries.filter(l => l.location.reportable).length !== (floor.level === 1 ? 10 : 12)
      || entries.filter(l => l.location.use === 'DORM_LOBBY').length !== (floor.level === 1 ? 1 : 0)
      || entries.filter(l => l.location.use === 'DORM_DUTY').length !== (floor.level === 1 ? 1 : 0)) return invalid()
  }
  return { building: buildings[0]!, buildings, floors, locations }
}
export function createDormFloor(pool: ResourceCache, manifest: CampusManifest) {
  const group = new Group(), kit = new GeometryKit(pool), entrances = new Map<string, Entrance>()
  // Each call receives only the selected explicit floor; slab and doors share its elevation.
  const elevation = manifest.locations[0]?.origin[1] ?? 0
  const context = new Group(); context.position.y = elevation; group.add(context)
  const teaching = manifest.locations.some(l => ['classroom','office','teaching-restroom','teaching-lobby'].includes(l.templateKey))
  kit.box(context, [teaching ? 74 : 38, .18, teaching ? 6.8 : 3.1], [0, -.1, 0], C.floor)
  // 主入口通道位于明确房间间隙x=4，而非穿过宿舍。
  if (manifest.locations.some(l => l.templateKey === 'dorm-lobby')) kit.box(context, [2.8, .18, 6], [4, -.1, -4.5], C.floor)
  for (const def of manifest.locations) {
    const room = new Group(), pivot = new Group()
    if(def.doorKey === null) {
      room.position.set(...def.origin);group.add(room)
      const entrance=kit.box(room,[3,.15,2],[0,0,-1],C.floor);entrance.userData.locationCode=def.locationCode
      const door={pivot,open:false}
      entrances.set(def.locationCode,{group:room,door,setDoorProgress:p=>{door.open=p>=1}})
      continue
    }
    const [width,depth] = teaching ? def.roomSize! : def.templateKey === 'classroom' ? [14,15] : def.templateKey === 'office' ? [8,9] : def.templateKey.startsWith('teaching-') ? [6,7] : def.templateKey === 'dorm-room' ? [5.1,5.8] : [3.4,4.2]
    room.position.set(...def.origin); room.rotation.y = def.rotationY; group.add(room)
    const box = (dims: [number, number, number], pos: [number, number, number], color: string) => kit.box(room, dims, pos, color)
    box([width, .14, depth], [0, -.07, depth / 2], C.floor)
    // 外壳剖到腰高，前墙保留门柱/门楣，房号DOM标签安全投影在入口。
    box([.1, 1.15, depth], [-width / 2, .575, depth / 2], C.wall)
    box([.1, 1.15, depth], [width / 2, .575, depth / 2], C.wall)
    box([width, 1.15, .12], [0, .575, depth], C.wall)
    box([(width - 1.2) / 2, 2.6, .12], [-(width + 1.2) / 4, 1.3, 0], C.wall)
    box([(width - 1.2) / 2, 2.6, .12], [(width + 1.2) / 4, 1.3, 0], C.wall)
    box([1.25, .22, .18], [0, 2.5, 0], C.trim)
    pivot.position.set(-.6, 0, .02); room.add(pivot)
    const leaf = kit.box(pivot, [1.15, 2.28, .09], [.575, 1.14, 0], C.door)
    // 这里只带稳定code，运行时ID映射在控制器；装饰不产生目标。
    leaf.userData.locationCode = def.locationCode
    kit.box(pivot, [.045, .08, .14], [1, 1.08, -.08], C.steel)
    const door = { pivot, open: false }
    entrances.set(def.locationCode, { group: room, door, setDoorProgress: p => { door.open = p >= 1; pivot.rotation.y = -Math.PI / 2 * Math.min(1, Math.max(0, p)) } })
  }
  return { group, entrances, dispose: () => { kit.dispose(); group.clear(); entrances.clear() } }
}
