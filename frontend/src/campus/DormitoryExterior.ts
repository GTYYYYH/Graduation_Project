import { Group } from 'three'
import { COLORS as C, GeometryKit } from './GeometryKit'
import type { ResourceCache } from './ResourceCache'
import type { Vec3 } from './types'

export function createDormitoryExterior(pool: ResourceCache) {
  const group = new Group(), levels: Group[] = [], kit = new GeometryKit(pool), stairs = new Group(), ground = new Group(), roof = new Group()
  group.add(stairs, ground, roof)
  kit.box(ground, [47, .16, 29], [0, -.32, -1.5], '#c9d2c4')
  kit.box(ground, [4, .02, 9], [4, -.22, -15], '#e6e3d8')
  for (let level = 0; level < 5; level++) {
    const floor = new Group(), shell = new Group(); floor.position.y = level * 3.15; floor.add(shell); levels.push(floor); group.add(floor)
    kit.box(shell, [38, .18, 15.1], [0, -.14, 0], C.trim)
    kit.box(shell, [38, 2.92, .24], [0, 1.43, 7.6], C.wall)
    // 主入口x=4留空，不以完整墙体遮住门厅。
    kit.box(shell, [21.55, 2.92, .24], [-8.225, 1.43, -7.6], C.wall)
    kit.box(shell, [13.55, 2.92, .24], [12.225, 1.43, -7.6], C.wall)
    kit.box(shell, [.24, 2.92, 15.1], [-19, 1.43, 0], C.wall)
    kit.box(shell, [.24, 2.92, 15.1], [19, 1.43, 0], C.wall)
    const positions: Vec3[] = []
    for (const x of [-12, -6, 0, 8, 14]) for (const sign of [-1, 1]) positions.push([x, 1.7, sign * 7.75])
    kit.boxes(shell, [3.1, 1.35, .08], positions, C.glass)
    kit.boxes(shell, [.065, 1.42, .12], positions.map(([x, y, z]) => [x, y, z]), C.trim)
    kit.boxes(shell, [3.2, .07, .12], positions.map(([x, y, z]) => [x, y - .68, z]), C.trim)
  }
  kit.box(roof, [39, .38, 16.2], [0, 15.78, 0], C.roof)
  kit.box(roof, [38.5, .4, .18], [0, 16.08, 7.95], C.roof)
  kit.box(roof, [38.5, .4, .18], [0, 16.08, -7.95], C.roof)
  // 两端独立楼梯，五层连续；阶梯批量实例化，避免每阶新增draw call。
  for (const x of [-17.65, 17.65]) {
    for (let f = 0; f < 4; f++) {
      const flight = new Group(), positions: Vec3[] = []; flight.userData.level = f + 2; stairs.add(flight)
      for (let step = 0; step < 18; step++) positions.push([x, f * 3.15 + (step + 1) * .175, -1.3 + step * .145])
      kit.boxes(flight, [1.6, .13, .3], positions, C.trim)
    }
    for (let f = 0; f < 5; f++) {
      const landing = new Group(); landing.userData.level = f + 1; stairs.add(landing)
      kit.box(landing, [2.35, .16, 3.2], [x, f * 3.15 - .08, 0], C.floor)
    }
  }
  const porch = new Group(); group.add(porch)
  kit.box(porch, [8.2, .14, 4.7], [6.4, -.12, -9.75], C.floor)
  kit.box(porch, [8.2, .16, 4.7], [6.4, 3, -9.75], C.roof)
  kit.box(porch, [.22, 3, .22], [2.4, 1.45, -11.9], C.trim)
  kit.box(porch, [.22, 3, .22], [10.4, 1.45, -11.9], C.trim)
  return { group, levels, roof, stairs, porch, dispose: () => { kit.dispose(); group.clear() } }
}
