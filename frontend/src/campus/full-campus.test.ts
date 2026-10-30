import { expect, it } from 'vitest'
import raw from '../../public/campus/manifest.json'
import config from '../../../config/campus.json'
import { bindManifest } from './templates/DormFloor'
import type { CatalogVo } from '../api/account'
import type { CampusManifest } from './types'
const manifest=raw as unknown as CampusManifest
const catalog = { configVersion:config.configVersion,
 buildings:config.buildings.map(b=>({...b,id:b.code})),floors:config.floors.map(f=>({...f,id:f.code,buildingId:f.buildingCode})),
 locations:config.locations.map(l=>({...l,id:l.code,floorId:l.floorCode,canCreateRequest:l.reportable})) } as CatalogVo
it('ten_buildings_and_full_location_coverage',()=>{
 const bound=bindManifest(manifest,catalog),buildings=bound.buildings,locationIds=bound.locations.map(l=>l.location.id)
 expect(buildings.length).toBe(10);expect(locationIds.length).toBe(296)
 expect(bound.floors).toHaveLength(41);expect(new Set(locationIds).size).toBe(296)
 expect(bound.locations.filter(l=>l.location.reportable)).toHaveLength(257)
 expect(new Set(bound.locations.map(l=>l.location.floorId))).toEqual(new Set(bound.floors.map(f=>f.id)))
 expect(new Set(locationIds)).toEqual(new Set(catalog.locations.map(l=>l.id)))
})
it('incomplete_or_incompatible_manifest_is_rejected',()=>{
 for(const change of [
  (m:CampusManifest)=>{m.configVersion='incompatible'},
  (m:CampusManifest)=>{m.contentVersion='unknown-future-format'},
  (m:CampusManifest)=>{m.locations.pop()},
  (m:CampusManifest)=>{m.buildings.pop()},
  (m:CampusManifest)=>{m.locations[0]!.floorCode='wb2-f1'},
  (m:CampusManifest)=>{m.locations[0]!.locationCode=m.locations[1]!.locationCode},
  (m:CampusManifest)=>{m.locations[0]!.interiorAnchor.position[0]=NaN},
 ]) {const m=structuredClone(manifest);change(m);expect(()=>bindManifest(m,catalog)).toThrow()}
 expect(catalog.locations).toHaveLength(296)
})
