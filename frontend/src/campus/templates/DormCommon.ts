import { Group } from 'three'
import { COLORS as C, GeometryKit } from '../GeometryKit'
import type { ResourceCache } from '../ResourceCache'
import type { RoomInstance } from './DormRoom'

export function createDormCommon(pool: ResourceCache, kind: 'dorm-lobby' | 'dorm-duty'): RoomInstance {
  const group = new Group(), kit = new GeometryKit(pool), pivot = new Group(), box = kit.box.bind(kit)
  group.name = kind
  box(group, [3.4, .14, 4.2], [0, -.07, 2.1], C.floor)
  box(group, [.12, 1.6, 4.2], [-1.7, .8, 2.1], C.wall).userData.cutawaySide = 'left'
  box(group, [.12, 1.6, 4.2], [1.7, .8, 2.1], C.wall).userData.cutawaySide = 'right'
  box(group, [3.4, 2.8, .12], [0, 1.4, 4.2], C.wall).userData.cutawaySide = 'back'
  pivot.position.set(-.6, 0, .02); group.add(pivot)
  box(pivot, [1.15, 2.28, .09], [.575, 1.14, 0], C.door)
  if (kind === 'dorm-duty') {
    box(group, [1.45, .09, .7], [0, .77, 2.95], C.wood, 'DUTY_DESK')
    kit.boxes(group, [.07, .73, .07], [[-.62, .365, 2.65], [.62, .365, 2.65], [-.62, .365, 3.22], [.62, .365, 3.22]], C.steel)
    const chair = new Group(); group.add(chair); chair.userData.facility = 'DUTY_CHAIR'
    box(chair, [.5, .075, .5], [0, .45, 2.08], C.wood)
    box(chair, [.5, .6, .07], [0, .77, 1.86], C.wood)
    kit.boxes(chair, [.04, .42, .04], [[-.21, .21, 1.87], [.21, .21, 1.87], [-.21, .21, 2.3], [.21, .21, 2.3]], C.steel)
  } else {
    // 仅导览/公告板，不添加公共空间报修设施。
    box(group, [1.3, 1.2, .1], [-.85, 1.6, 4.08], C.roof)
    box(group, [1.1, 1.0, .04], [-.85, 1.6, 4], C.white)
    box(group, [.85, .08, .03], [-.85, 1.85, 3.96], C.glass)
    box(group, [.85, .045, .03], [-.85, 1.52, 3.96], C.wood)
    box(group, [.85, .045, .03], [-.85, 1.27, 3.96], C.wood)
  }
  const door = { open: false, pivot }
  return { group, door, setDoorProgress: p => { door.open = p >= 1; pivot.rotation.y = -Math.PI / 2 * Math.min(1, Math.max(0, p)) }, updateCutaway: camera => kit.updateCutaway(group, camera, 3.4, 4.2), dispose: () => { kit.dispose(); group.clear() } }
}
