import { COLORS as C } from '../GeometryKit'
import type { ResourceCache } from '../ResourceCache'
import { deskChair, teachingRoom, fitTeachingRoom } from './TeachingCommon'
export function createOffice(pool: ResourceCache, size: [number, number] = [18.5,13.6]) {
  const {kit,room}=teachingRoom(pool,8,9),group=room.group
  for(const x of [-2,2])for(const z of [3,6]) {
    deskChair(kit,group,x,z)
    kit.box(group,[.2,.2,.1],[x,.5,z+.5],C.white,'SOCKET')
  }
  kit.box(group,[1.5,.5,.25],[0,2.4,8.8],C.white,'AC')
  for(const z of [3,6])kit.box(group,[1.2,.12,.5],[0,2.9,z],C.white,'LIGHT')
  return fitTeachingRoom(room,8,9,size)
}
