import { COLORS as C } from '../GeometryKit'
import type { ResourceCache } from '../ResourceCache'
import { deskChair, teachingRoom, fitTeachingRoom } from './TeachingCommon'
export function createClassroom(pool: ResourceCache, size: [number, number] = [18.5,13.6]) {
  const {kit,room}=teachingRoom(pool,14,15), group=room.group
  for(let row=0;row<6;row++)for(let col=0;col<5;col++)deskChair(kit,group,(col-2)*2.3,2.6+row*1.65)
  kit.box(group,[5,.28,2],[0,.14,13.3],C.wood)
  kit.box(group,[1.7,1,.7],[0,.75,13.4],C.wood)
  kit.box(group,[.8,.5,.08],[0,1.48,13.4],C.steel,'COMPUTER')
  kit.box(group,[8,1.4,.1],[0,1.7,14.8],'#47645c')
  kit.box(group,[.25,.55,1.5],[-6.8,2.3,10],C.white,'AC')
  for(const x of [-3,3])for(const z of [3,7,11])kit.box(group,[1.3,.12,.45],[x,2.9,z],C.white,'LIGHT')
  for(const [x,z] of [[-6.8,5],[6.8,5],[-4,.2],[4,14.8]])kit.box(group,[.15,.22,.12],[x!,.55,z!],C.white,'SOCKET')
  return fitTeachingRoom(room,14,15,size)
}
