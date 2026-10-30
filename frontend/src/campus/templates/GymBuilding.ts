import { Group } from 'three'
import { COLORS as C, GeometryKit } from '../GeometryKit'
import type { ResourceCache } from '../ResourceCache'
import type { RoomInstance } from './DormRoom'
import { createTeachingBuilding } from './TeachingBuilding'
export function createGymBuilding(pool:ResourceCache) {return createTeachingBuilding(pool,2,32,34,7.5)}
export function createGymInterior(pool:ResourceCache,kind:'gym-court'|'gym-gallery'):RoomInstance {
  const group=new Group(),kit=new GeometryKit(pool),pivot=new Group(),door={open:false,pivot}
  group.userData.centralVoid=true;group.add(pivot)
  // Each view contains the whole hall. The gallery wraps an actual empty center.
  kit.box(group,[30,.18,32],[0,-.1,15],C.floor)
  kit.box(group,[16,.035,26],[0,.025,15],'#c0a078')
  for(const x of [-8,8])kit.box(group,[.08,.02,26],[x,.055,15],C.white)
  for(const z of [2,15,28])kit.box(group,[16,.02,.08],[0,.055,z],C.white)
  for(const z of [2,28]) {
    kit.box(group,[.18,3.1,.18],[0,1.55,z],C.steel,'BASKET')
    kit.box(group,[2,1.1,.1],[0,3,z],C.white)
    kit.round(group,.27,.05,[0,2.7,z+(z<15? .5:-.5)],C.steel)
  }
  for(const x of [-14,14]) {
    kit.box(group,[.15,13,32],[x,6.5,15],C.wall).userData.cutawaySide=x<0?'left':'right'
    kit.box(group,[5,.2,32],[Math.sign(x)*12,7.2,15],C.floor,'GALLERY_DECK')
    kit.box(group,[1.1,.45,26],[Math.sign(x)*11.5,7.525,14.5],C.floor,'GALLERY_TIER')
    kit.box(group,[.1,1.1,30],[Math.sign(x)*9.5,7.85,15],C.steel)
    for(let row=0;row<2;row++)for(let seat=0;seat<8;seat++) {
      const sx=Math.sign(x)*(10.4+row*1.1),z=4+seat*3
      kit.box(group,[.75,.35,.75],[sx,7.45+row*.45,z],C.roof,'SEAT')
      kit.box(group,[.15,.65,.75],[sx+Math.sign(x)*.35,7.85+row*.45,z],C.roof)
    }
    for(const z of [6,15,24])kit.box(group,[.6,.15,.6],[Math.sign(x)*12,10,z],C.white,'GALLERY_LIGHT')
    kit.box(group,[.65,1.8,.6],[Math.sign(x)*13,8.2,15],C.white,'GALLERY_AC')
  }
  for(const z of [0,30]) {
    kit.box(group,[28,.2,2],[0,7.2,z],C.floor)
    const stairs=new Group();stairs.userData.facility='STAIR';group.add(stairs)
    for(let step=0;step<30;step++)kit.box(stairs,[.4,.18,1.8],[-8+step*.4,(step+1)*7.2/30,z],C.trim)
  }
  for(const x of [-6,0,6])for(const z of [5,12,19,26])kit.box(group,[.9,.18,.5],[x,14,z],C.white,'COURT_LIGHT')
  for(const x of [-12,12])for(const z of [3,27])kit.box(group,[.65,1.8,.6],[x,.9,z],C.white,'COURT_AC')
  // Manifest gallery origin is elevated; offset the hall so its floor stays at ground.
  if(kind==='gym-gallery')group.position.y=-7.2
  const root=new Group();root.userData.centralVoid=true;root.add(group)
  return {group:root,door,setDoorProgress:p=>{door.open=p>=1},updateCutaway:camera=>kit.updateCutaway(group,camera,28,32),dispose:()=>{kit.dispose();group.clear();root.clear()}}
}
