import { expect, it, vi } from 'vitest'
import { CampusRenderer } from './CampusRenderer'
import { ResourceCache, ResourceScope } from './ResourceCache'
import { createDormitoryExterior } from './DormitoryExterior'
import { bindManifest } from './templates/DormFloor'
import raw from '../../public/campus/manifest.json'
import config from '../../../config/campus.json'
import type { CatalogVo } from '../api/account'
import type { CampusManifest } from './types'

it('interior_cache_is_bounded', async () => {
 const catalog={configVersion:config.configVersion,buildings:config.buildings.map(b=>({...b,id:b.code})),floors:config.floors.map(f=>({...f,id:f.code,buildingId:f.buildingCode})),locations:config.locations.map(l=>({...l,id:l.code,floorId:l.floorCode,canCreateRequest:l.reportable}))} as CatalogVo
 const manifest=raw as unknown as CampusManifest,binding=bindManifest(manifest,catalog),pool=new ResourceCache(),exterior=createDormitoryExterior(pool)
 const subject=new CampusRenderer({catalog,reducedMotion:()=>true,onState:()=>{},onSelect:()=>{},onEnter:()=>{},onFrame:()=>{},onRestore:()=>{}})
 const internals=subject as unknown as {interiors:Map<string,{dispose:()=>void}>,floor:{dispose:()=>void}|null}
 Object.assign(subject,{pool,binding,manifest,controls:{enabled:true,maxDistance:100},renderer:{render:()=>{}},exterior,exteriors:new Map([['wb1',exterior]]),selectedBuildingId:'wb1'})
 vi.spyOn(subject as unknown as {animate:()=>Promise<boolean>},'animate').mockResolvedValue(true)
 let generation=0
 try {
  for(let i=0;i<30;i++){
   const id=`wb1-f1-r${101+i%10}`
   await subject.selectTarget({kind:'LOCATION',locationId:id},++generation)
   await subject.enterLocation(id,++generation)
   const interiorTemplateCacheCount=internals.interiors.size
   expect(interiorTemplateCacheCount).toBeLessThanOrEqual(2)
   expect(interiorTemplateCacheCount).toBeGreaterThan(0)
   expect(pool.stats().references).toBeGreaterThan(0)
  }
 }finally {for(const room of internals.interiors.values())room.dispose();internals.interiors.clear();internals.floor?.dispose();exterior.dispose()}
 expect(pool.stats()).toEqual({entries:0,references:0,estimatedBytes:0})
})
it('geometry_material_texture_release_only_after_last_scope',()=>{
 const pool=new ResourceCache(),first=new ResourceScope(pool),second=new ResourceScope(pool),freed:string[]=[]
 for(const key of ['geometry','material','texture']) { first.use(key,()=>({dispose:()=>freed.push(key)}),12);second.use(key,()=>({dispose:()=>freed.push(key)}),12) }
 first.dispose();expect(freed).toEqual([]);expect(pool.stats().references).toBe(3)
 second.dispose();expect(freed.sort()).toEqual(['geometry','material','texture']);expect(pool.stats().references).toBe(0)
})

it('dispose_stops_frame_timer_observer_and_all_registered_listeners',()=>{
 vi.useFakeTimers()
 const doc=new EventTarget(),canvas=Object.assign(new EventTarget(),{remove:vi.fn()})
 const removeDoc=vi.spyOn(doc,'removeEventListener'),removeCanvas=vi.spyOn(canvas,'removeEventListener'),cancelFrame=vi.fn(),disconnect=vi.fn(),disposeControls=vi.fn()
 vi.stubGlobal('document',doc);vi.stubGlobal('cancelAnimationFrame',cancelFrame)
 const subject=new CampusRenderer({catalog:{} as CatalogVo,reducedMotion:()=>true,onState:()=>{},onSelect:()=>{},onEnter:()=>{},onFrame:()=>{},onRestore:()=>{}})
 const callback=vi.fn(),timer=setTimeout(callback,15000),renderer={domElement:canvas,renderLists:{dispose:vi.fn()},dispose:vi.fn(),forceContextLoss:vi.fn()}
 Object.assign(subject,{renderer,controls:{enabled:true,dispose:disposeControls},observer:{disconnect},frame:77,timer})
 try {
  subject.dispose();subject.dispose()
  expect(disconnect).toHaveBeenCalledOnce();expect(disposeControls).toHaveBeenCalledOnce();expect(cancelFrame).toHaveBeenCalledWith(77)
  expect(removeDoc.mock.calls.map(c=>c[0])).toContain('visibilitychange')
  expect(removeCanvas.mock.calls.map(c=>c[0])).toEqual(expect.arrayContaining(['pointerdown','pointerup','pointercancel','webglcontextlost','webglcontextrestored']))
  expect(renderer.dispose).toHaveBeenCalledOnce();expect(renderer.forceContextLoss).toHaveBeenCalledOnce();expect(canvas.remove).toHaveBeenCalledOnce()
  vi.advanceTimersByTime(15000);expect(callback).not.toHaveBeenCalled();expect(vi.getTimerCount()).toBe(0)
 }finally{vi.useRealTimers();vi.unstubAllGlobals()}
})
