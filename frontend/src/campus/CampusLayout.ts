import { Group } from 'three'
import { GeometryKit, COLORS as C } from './GeometryKit'
import type { ResourceCache } from './ResourceCache'
export function createCampusLayout(pool:ResourceCache) {
  const group=new Group(),kit=new GeometryKit(pool)
  kit.box(group,[260,.12,195],[35,-.5,-5],'#cad5c3')
  kit.box(group,[72,.08,66],[32,-.39,-4],'#e2dfd4')
  for(const z of [-53,43])kit.box(group,[125,.06,8],[32.5,-.3,z],'#b8bcb7')
  for(const x of [-30,95])kit.box(group,[8,.06,96],[x,-.3,-5],'#b8bcb7')
  kit.box(group,[8,.06,90],[32,-.3,-5],'#e8e5d9')
  for(const x of [-16,80])for(const z of [-35,-10,15,32]) {
    kit.round(group,.35,2.2,[x,.7,z],C.wood)
    kit.round(group,2.2,3.2,[x,3,z],'#8da88c')
    kit.box(group,[3,.18,.8],[x+4,.45,z],C.wood)
  }
  return {group,dispose:()=>{kit.dispose();group.clear()}}
}
