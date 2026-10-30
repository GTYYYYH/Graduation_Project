import { expect, it, vi } from 'vitest'
import raw from '../../public/campus/manifest.json'
import config from '../../../config/campus.json'
import type { CatalogVo } from '../api/account'
import type { CampusManifest } from './types'
import { CampusRenderer } from './CampusRenderer'
import { ResourceCache } from './ResourceCache'
import { createDormitoryExterior } from './DormitoryExterior'
import { createTeachingBuilding } from './templates/TeachingBuilding'
import { createCafeteriaBuilding } from './templates/CafeteriaBuilding'
import { bindManifest } from './templates/DormFloor'
const manifest=raw as unknown as CampusManifest
const catalog={configVersion:config.configVersion,buildings:config.buildings.map(b=>({...b,id:b.code})),floors:config.floors.map(f=>({...f,id:f.code,buildingId:f.buildingCode})),locations:config.locations.map(l=>({...l,id:l.code,floorId:l.floorCode,canCreateRequest:l.reportable}))} as CatalogVo
const binding=bindManifest(manifest,catalog)
it.each(binding.buildings)('$code floor cutaway retains supporting walls, hides upper structure, and overview restores it',async building=>{
 const pool=new ResourceCache()
 const exterior=building.type==='DORMITORY'?createDormitoryExterior(pool):building.type==='CANTEEN'?createCafeteriaBuilding(pool):building.type==='GYM'?createTeachingBuilding(pool,2,32,34,7.5):createTeachingBuilding(pool)
 const renderer=new CampusRenderer({catalog,reducedMotion:()=>true,onState:()=>{},onSelect:()=>{},onEnter:()=>{},onFrame:()=>{},onRestore:()=>{}})
 Object.assign(renderer,{binding,manifest,controls:{enabled:true,maxDistance:100},exterior,exteriors:new Map([[building.id,exterior]]),selectedBuildingId:building.id})
 vi.spyOn(renderer as unknown as {animate:()=>Promise<boolean>},'animate').mockResolvedValue(true)
 try {
  const floors=binding.floors.filter(f=>f.buildingId===building.id)
  let generation=0
  for(const floor of [floors[0]!,floors.at(-1)!,floors[0]!]){
   await renderer.selectTarget({kind:'FLOOR',floorId:floor.id},++generation)
   expect(exterior.levels.map(l=>l.visible)).toEqual(floors.map(f=>f.level<=floor.level))
   expect(exterior.roof.visible).toBe(false)
   expect(exterior.stairs.children.every(f=>f.visible===(Number(f.userData.level)<=floor.level))).toBe(true)
  }
  await renderer.selectTarget({kind:'OVERVIEW'},++generation)
  expect(exterior.levels.every(l=>l.visible)).toBe(true)
  expect(exterior.roof.visible).toBe(true)
  expect(exterior.stairs.children.every(f=>f.visible)).toBe(true)
 } finally {exterior.dispose();pool.dispose()}
})
