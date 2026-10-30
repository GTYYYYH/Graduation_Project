import { Group } from 'three'
import { COLORS as C, GeometryKit } from '../GeometryKit'
import type { ResourceCache } from '../ResourceCache'
import type { RoomInstance } from './DormRoom'

export function fitTeachingRoom(room: RoomInstance, width: number, depth: number, size: [number, number]) {
  room.group.scale.set(size[0] / width, 1, size[1] / depth)
  return room
}

export function teachingRoom(pool: ResourceCache, width: number, depth: number) {
  const group = new Group(), kit = new GeometryKit(pool), pivot = new Group()
  kit.box(group,[width,.14,depth],[0,-.07,depth/2],C.floor)
  for(const side of [-1,1])kit.box(group,[.12,2.9,depth],[side*width/2,1.45,depth/2],C.wall).userData.cutawaySide=side<0?'left':'right'
  kit.box(group,[width,2.9,.12],[0,1.45,depth],C.wall).userData.cutawaySide='back'
  for(const side of [-1,1])kit.box(group,[(width-1.4)/2,2.9,.12],[side*(width+1.4)/4,1.45,0],C.wall)
  kit.box(group,[1.4,.4,.12],[0,2.7,0],C.trim)
  pivot.position.set(-.7,0,0);group.add(pivot)
  kit.box(pivot,[1.35,2.5,.1],[.675,1.25,0],C.door)
  kit.box(group,[width*.65,1.1,.08],[0,1.8,depth-.1],C.glass)
  const door = {open:false,pivot}
  const room: RoomInstance = {group,door,setDoorProgress:p=>{door.open=p>=1;pivot.rotation.y=-Math.PI/2*Math.min(1,Math.max(0,p))},updateCutaway:camera=>kit.updateCutaway(group,camera,width,depth),dispose:()=>{kit.dispose();group.clear()}}
  return {kit,room}
}
export function deskChair(kit: GeometryKit, group: Group, x: number, z: number) {
  kit.box(group,[1.2,.1,.65],[x,.8,z],C.wood,'DESK')
  for(const side of [-1,1])kit.box(group,[.08,.75,.5],[x+side*.48,.375,z],C.steel)
  kit.box(group,[.48,.09,.5],[x,.46,z-.75],C.wood,'CHAIR')
  kit.box(group,[.48,.5,.07],[x,.75,z-.97],C.wood)
  for(const side of [-1,1])kit.box(group,[.06,.42,.4],[x+side*.18,.21,z-.75],C.steel)
}
export function createTeachingCommon(pool: ResourceCache, kind:'teaching-restroom'|'teaching-lobby', size: [number, number] = [18.5,13.6]): RoomInstance {
  const {kit,room}=teachingRoom(pool,6,7)
  if(kind==='teaching-restroom') for(const x of [-1.5,1.5]) {
    kit.box(room.group,[2.2,1.7,.12],[x,.85,4.2],C.bath,'CUBICLE')
    kit.box(room.group,[.1,1.7,2.7],[x+1.1,.85,5.5],C.bath)
    kit.box(room.group,[.8,.18,.55],[x,.85,2.2],C.white,'BASIN')
  } else {
    kit.box(room.group,[3.8,1.6,.1],[0,1.7,6.8],C.roof)
    kit.box(room.group,[3.4,1.2,.08],[0,1.7,6.7],C.white)
  }
  return fitTeachingRoom(room,6,7,size)
}
