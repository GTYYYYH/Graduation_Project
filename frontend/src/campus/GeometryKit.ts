import { BoxGeometry, CylinderGeometry, Group, InstancedMesh, Matrix4, Mesh, MeshStandardMaterial, Vector3 } from 'three'
import type { BufferGeometry } from 'three'
import { ResourceScope } from './ResourceCache'
import type { ResourceCache } from './ResourceCache'
import type { Vec3 } from './types'

export const COLORS = { wall: '#d9d7cd', trim: '#eef0e9', roof: '#758997', glass: '#9eb8c0', wood: '#c5a77e', steel: '#5d737e', bed: '#dde8e2', floor: '#e4ddd1', bath: '#cedddc', white: '#f7f7ee', door: '#a68b69' }
function byteSize(geometry: BufferGeometry) {
  return Object.values(geometry.attributes).reduce((n, a) => n + a.array.byteLength, 0) + (geometry.index?.array.byteLength ?? 0)
}
// 各模型scope共享静态几何/材质；实例变换和InstancedMesh缓冲由各实例拥有。
export class GeometryKit {
  private readonly scope: ResourceScope
  private instanced: InstancedMesh[] = []
  constructor(pool: ResourceCache) { this.scope = new ResourceScope(pool) }
  private geometry(w = 1, h = 1, d = 1) {
    const key = `box:${w}:${h}:${d}`
    return this.scope.use(key, () => new BoxGeometry(w, h, d), 840)
  }
  material(color: string) { return this.scope.use(`mat:${color}`, () => new MeshStandardMaterial({ color, roughness: .86 }), 256) }
  box(parent: Group, dimensions: Vec3, position: Vec3, color: string, facility?: string) {
    const mesh = new Mesh(this.geometry(), this.material(color))
    mesh.scale.set(...dimensions); mesh.position.set(...position)
    if (facility) mesh.userData.facility = facility
    parent.add(mesh); return mesh
  }
  round(parent: Group, radius: number, height: number, position: Vec3, color: string, facility?: string) {
    const geometry = this.scope.use('cylinder:12', () => new CylinderGeometry(1, 1, 1, 12), 2772)
    const mesh = new Mesh(geometry, this.material(color)); mesh.scale.set(radius, height, radius); mesh.position.set(...position)
    if (facility) mesh.userData.facility = facility
    parent.add(mesh); return mesh
  }
  boxes(parent: Group, dimensions: Vec3, positions: Vec3[], color: string) {
    const geometry = this.geometry(...dimensions), mesh = new InstancedMesh(geometry, this.material(color), positions.length)
    positions.forEach((p, i) => mesh.setMatrixAt(i, new Matrix4().makeTranslation(...p)))
    mesh.instanceMatrix.needsUpdate = true; parent.add(mesh); this.instanced.push(mesh); return mesh
  }
  dispose() { for (const mesh of this.instanced) mesh.dispose(); this.instanced = []; this.scope.dispose() }
  updateCutaway(group: Group, camera: Vec3, width: number, depth: number) {
    group.updateMatrixWorld(true)
    const local = group.worldToLocal(new Vector3(...camera))
    group.traverse(node => {
      if (node.userData.cutawaySide === 'left') node.visible = local.x >= -width / 2
      if (node.userData.cutawaySide === 'right') node.visible = local.x <= width / 2
      if (node.userData.cutawaySide === 'back') node.visible = local.z <= depth
    })
  }
  // CPU估算不称为GPU显存；单元几何实际大小可在诊断时核对。
  static geometryBytes = byteSize
}
