import { Group } from 'three'
import { COLORS as C, GeometryKit } from '../GeometryKit'
import type { ResourceCache } from '../ResourceCache'
export function createTeachingBuilding(pool:ResourceCache, count=5, width=74, depth=34, rise=3.15) {
  const kit=new GeometryKit(pool), group=new Group(),levels:Group[]=[],roof=new Group(),stairs=new Group(),porch=new Group()
  group.add(roof,stairs,porch)
  for(let f=0;f<count;f++) {
    const level=new Group();level.position.y=f*rise;levels.push(level);group.add(level)
    kit.box(level,[width,.18,depth],[0,-.1,0],C.floor)
    for(const sign of [-1,1])kit.box(level,[.2,2.9,depth],[sign*width/2,1.45,0],C.wall)
    for(const sign of [-1,1]) {
      kit.box(level,[width,2.9,.2],[0,1.45,sign*depth/2],C.wall)
      for(let x=-width/2+4;x<width/2;x+=6)kit.box(level,[3,1.3,.08],[x,1.7,sign*(depth/2+.12)],C.glass)
    }
    if(f<count-1) {
      const flight=new Group();flight.userData.level=f+2;stairs.add(flight)
      for(const x of [-width/2+2,width/2-2])for(let step=0;step<18;step++)kit.box(flight,[2,.13,.35],[x,f*rise+(step+1)*rise/18,-1.5+step*.17],C.trim)
    }
  }
  kit.box(roof,[width+1,.35,depth+1],[0,count*rise,0],C.roof)
  return {group,levels,roof,stairs,porch,dispose:()=>{kit.dispose();group.clear()}}
}
