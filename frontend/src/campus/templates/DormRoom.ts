import { Group } from 'three'
import type { ResourceCache } from '../ResourceCache'
import { COLORS as C, GeometryKit } from '../GeometryKit'
import type { Vec3 } from '../types'
export interface RoomInstance { group: Group; door: { open: boolean; pivot: Group }; setDoorProgress(progress: number): void; updateCutaway(camera: Vec3): void; dispose(): void }
export function createDormRoom(pool: ResourceCache): RoomInstance {
  const group = new Group(), kit = new GeometryKit(pool), pivot = new Group()
  const box = kit.box.bind(kit), round = kit.round.bind(kit)
  group.name = 'dorm-room-interior'
  // 局部入口z=0，内景沿+z展开；上下床桌两组与入口一角卫浴分开。
  box(group, [5.1, .14, 5.8], [0, -.07, 2.9], C.floor)
  box(group, [.14, 2.85, 5.8], [-2.55, 1.425, 2.9], C.wall).userData.cutawaySide = 'left'
  box(group, [.14, 2.85, 5.8], [2.55, 1.425, 2.9], C.wall).userData.cutawaySide = 'right'
  box(group, [5.1, .85, .14], [0, .425, 5.8], C.wall).userData.cutawaySide = 'back'
  box(group, [5.1, .4, .14], [0, 2.65, 5.8], C.wall).userData.cutawaySide = 'back'
  box(group, [.8, 1.6, .14], [-2.15, 1.65, 5.8], C.wall).userData.cutawaySide = 'back'
  box(group, [.8, 1.6, .14], [2.15, 1.65, 5.8], C.wall).userData.cutawaySide = 'back'
  box(group, [3.45, 1.5, .055], [0, 1.65, 5.83], C.glass).userData.cutawaySide = 'back'
  box(group, [.055, 1.6, .16], [0, 1.65, 5.76], C.trim).userData.cutawaySide = 'back'
  // 门向本房内开启。门框和独立轴不共享对象。
  pivot.position.set(-.65, 0, .05); group.add(pivot)
  box(pivot, [1.05, 2.25, .09], [.525, 1.125, 0], C.door)
  box(pivot, [.045, .08, .16], [.9, 1.02, -.08], C.steel)
  box(group, [.08, 2.4, .16], [-.71, 1.2, 0], C.trim)
  box(group, [.08, 2.4, .16], [.48, 1.2, 0], C.trim)
  box(group, [1.28, .12, .16], [-.115, 2.42, 0], C.trim)
  for (const side of [-1, 1]) {
    const x = side * 1.66, bed = new Group(); group.add(bed)
    box(bed, [1.25, .12, 2.15], [x, 1.9, 3.65], C.wood, 'BED')
    box(bed, [1.15, .16, 2.02], [x, 2.05, 3.65], C.bed)
    box(bed, [.85, .09, .38], [x, 2.16, 4.35], C.white)
    box(bed, [1.25, .09, 1.45], [x, .78, 3.6], C.wood, 'DESK')
    box(bed, [.46, .7, .65], [x + side * .36, .36, 4.2], C.wood)
    for (const dx of [-.55, .55]) for (const z of [2.65, 4.65]) box(bed, [.055, 2.3, .055], [x + dx, 1.15, z], C.steel)
    box(bed, [.035, .42, 2.1], [x - side * .6, 2.3, 3.65], C.steel)
    const ladder = [-.44, .44].map(dx => [x + dx, 1.02, 2.52] as [number, number, number])
    kit.boxes(bed, [.045, 2, .045], ladder, C.steel)
    kit.boxes(bed, [.85, .04, .09], [.3, .65, 1, 1.35, 1.7].map(y => [x, y, 2.52]), C.steel)
    box(group, [.19, .12, .055], [side * 2.44, 1.05, 3.5], C.white, 'SOCKET')
    box(group, [.018, .04, .06], [side * 2.4, 1.05, 3.5], C.steel)
  }
  // 独立卫浴置于入口左角，低剖墙让三件设施及独立灯可辨。
  box(group, [1.75, .05, 1.85], [-1.6, .025, 1.02], C.bath)
  box(group, [.09, 1.4, 1.85], [-.76, .7, 1.02], C.bath)
  box(group, [1.75, 1.4, .09], [-1.6, .7, 1.95], C.bath)
  const toilet = new Group(); group.add(toilet); toilet.userData.facility = 'TOILET'
  round(toilet, .22, .42, [-2.05, .24, .75], C.white)
  round(toilet, .3, .09, [-2.05, .51, .75], C.white)
  box(toilet, [.46, .5, .17], [-2.05, .52, .4], C.white)
  const shower = new Group(); group.add(shower); shower.userData.facility = 'SHOWER'
  round(shower, .02, 1.85, [-2.4, 1.03, 1.56], C.steel)
  box(shower, [.42, .04, .28], [-2.25, 2, 1.56], C.steel)
  box(shower, [.07, .08, .09], [-2.38, 1.1, 1.56], C.steel)
  const basin = new Group(); group.add(basin); basin.userData.facility = 'BASIN'
  box(basin, [.57, .12, .4], [-1.15, .86, .45], C.white)
  box(basin, [.14, .72, .14], [-1.15, .4, .45], C.white)
  box(basin, [.04, .2, .05], [-1.15, 1.01, .32], C.steel)
  box(group, [1.06, .35, .25], [1.55, 2.45, 5.6], C.white, 'AC')
  box(group, [.8, .045, .27], [1.55, 2.32, 5.58], C.steel)
  round(group, .25, .065, [0, 2.8, 3.3], C.white, 'ROOM_LIGHT')
  round(group, .13, .055, [-1.6, 2.7, 1.05], C.white, 'BATH_LIGHT')
  const door = { open: false, pivot }
  return { group, door, setDoorProgress: progress => { const p = Math.min(1, Math.max(0, progress)); pivot.rotation.y = -Math.PI / 2 * p; door.open = p === 1 },
    updateCutaway: camera => kit.updateCutaway(group, camera, 5.1, 5.8), dispose: () => { group.remove(...group.children); kit.dispose() } }
}
