import { COLORS as C } from '../GeometryKit'
import type { ResourceCache } from '../ResourceCache'
import { teachingRoom } from './TeachingCommon'
export function createCafeteriaFloor(pool:ResourceCache) {
  const {kit,room}=teachingRoom(pool,28,26),group=room.group
  for(const x of [-7,0,7])for(const z of [4,8,12,16]) {
    kit.box(group,[2,.12,2],[x,.82,z],C.wood,'TABLE')
    kit.box(group,[.18,.78,.18],[x,.39,z],C.steel)
    for(const [dx,dz] of [[-1.5,0],[1.5,0],[0,-1.5],[0,1.5]]) {
      kit.box(group,[.55,.1,.55],[x+dx!,.46,z+dz!],C.wood,'CHAIR')
      kit.box(group,[.12,.42,.12],[x+dx!,.21,z+dz!],C.steel)
    }
    kit.box(group,[1.2,.12,.5],[x,3,z],C.white,'LIGHT')
  }
  for(const x of [-12,12])for(const z of [2,20])kit.box(group,[.65,1.9,.6],[x,.95,z],C.white,'AC')
  // A solid enclosed rear block has no location code, pick target or interior entry.
  kit.box(group,[27.8,2.8,3.5],[0,1.4,24],C.wall,'CLOSED_SERVICES')
  for(const x of [-9,-3,3,9]) {
    kit.box(group,[4,1.3,.08],[x,1.6,22.18],C.roof,'WINDOW')
    kit.box(group,[4.3,.15,1],[x,1.0,21.8],C.steel)
  }
  return room
}
