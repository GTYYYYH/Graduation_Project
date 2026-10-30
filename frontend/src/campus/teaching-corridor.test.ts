import { expect,it } from 'vitest'
import { Box3 } from 'three'
import manifest from '../../public/campus/manifest.json'
import config from '../../../config/campus.json'
import { ResourceCache } from './ResourceCache'
import { createClassroom } from './templates/Classroom'
import { createOffice } from './templates/Office'
import { createTeachingCommon } from './templates/TeachingCommon'
it('each teaching floor has left four, corridor, right three with fixed public slots',()=>{
 for(const b of manifest.buildings.filter(b=>['zy','js','ba'].includes(b.buildingCode)))for(const [f,code] of b.floorCodes.entries()){
  const rooms=manifest.locations.filter(l=>l.floorCode===code) as unknown as {locationCode:string;layoutSlot:number;roomSize:number[];origin:number[];rotationY:number}[]
  expect(rooms.map(l=>l.layoutSlot).sort()).toEqual([1,2,3,4,5,6,7])
  for(const room of rooms){const left=room.layoutSlot<=4, row=left?room.layoutSlot-1:room.layoutSlot-5, count=left?4:3
   expect(room.roomSize).toEqual([74/count,13.6]);expect(room.origin[0]).toBeCloseTo(37-(row+.5)*74/count);expect(room.origin[2]).toBe(left?-3.4:3.4);expect(room.rotationY).toBe(left?Math.PI:0)
   const use=config.locations.find(l=>l.code===room.locationCode)!.use
   if(room.layoutSlot===1)expect(use).toBe('RESTROOM_MALE')
   if(room.layoutSlot===4)expect(use).toBe('RESTROOM_FEMALE')
   if(room.layoutSlot===6&&f===0)expect(use).toBe('TEACHING_LOBBY')
  }
 }
})
it('all room templates fit the selected side dimensions without changing floor height',()=>{
 const pool=new ResourceCache()
 for(const dimensions of [[18.5,13.6],[74/3,13.6]] as [number,number][])for(const make of [()=>createClassroom(pool,dimensions),()=>createOffice(pool,dimensions),()=>createTeachingCommon(pool,'teaching-restroom',dimensions),()=>createTeachingCommon(pool,'teaching-lobby',dimensions)]){
  const room=make();room.group.updateMatrixWorld(true);const slab=new Box3().setFromObject(room.group.children[0]!);expect(slab.max.x-slab.min.x).toBeCloseTo(dimensions[0]);expect(slab.max.z-slab.min.z).toBeCloseTo(dimensions[1]);expect(slab.max.y-slab.min.y).toBeCloseTo(.14);room.dispose()
 }expect(pool.stats().entries).toBe(0);pool.dispose()
})
