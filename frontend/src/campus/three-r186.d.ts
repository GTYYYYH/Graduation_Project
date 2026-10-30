// 本批使用的r186公开API子集，与node_modules/three源码核对；不是完整@types/three替代。
declare module 'three' {
  export class Vector2 { constructor(x?: number, y?: number); x: number; y: number }
  export class Vector3 {
    constructor(x?: number, y?: number, z?: number); x: number; y: number; z: number
    set(x: number, y: number, z: number): this; copy(v: Vector3): this; clone(): Vector3
    lerpVectors(a: Vector3, b: Vector3, alpha: number): this; applyMatrix4(m: Matrix4): this; project(camera: PerspectiveCamera): this
    distanceTo(v: Vector3): number
  }
  export class Euler { x: number; y: number; z: number; set(x: number, y: number, z: number): this }
  export class Matrix4 { makeTranslation(x: number, y: number, z: number): this }
  export class Object3D {
    uuid: string; name: string; children: Object3D[]; parent: Object3D | null; position: Vector3; rotation: Euler; scale: Vector3
    visible: boolean; userData: Record<string, unknown>; matrixWorld: Matrix4
    add(...objects: Object3D[]): this; remove(...objects: Object3D[]): this; clear(): this
    traverse(callback: (object: Object3D) => void): void; updateMatrixWorld(force?: boolean): void
    localToWorld(vector: Vector3): Vector3; worldToLocal(vector: Vector3): Vector3; getWorldPosition(target: Vector3): Vector3
  }
  export class Box3 { constructor(min?: Vector3, max?: Vector3); min: Vector3; max: Vector3; setFromObject(object: Object3D, precise?: boolean): this }
  export class Group extends Object3D {}
  export class Scene extends Object3D { background: Color | null }
  export class Color { constructor(value?: string | number) }
  export class BufferGeometry { dispose(): void; attributes: Record<string, { array: { byteLength: number } }>; index: { array: { byteLength: number } } | null }
  export class BoxGeometry extends BufferGeometry { constructor(width?: number, height?: number, depth?: number) }
  export class CylinderGeometry extends BufferGeometry { constructor(radiusTop?: number, radiusBottom?: number, height?: number, radialSegments?: number) }
  export class Material { dispose(): void; color: Color }
  export class MeshStandardMaterial extends Material { constructor(options?: { color?: string | number; roughness?: number; metalness?: number; transparent?: boolean; opacity?: number; side?: number }) }
  export class Mesh extends Object3D { constructor(geometry?: BufferGeometry, material?: Material); geometry: BufferGeometry; material: Material }
  export class InstancedMesh extends Mesh { constructor(geometry: BufferGeometry, material: Material, count: number); setMatrixAt(index: number, matrix: Matrix4): void; instanceMatrix: { needsUpdate: boolean }; dispose(): void }
  export class PerspectiveCamera extends Object3D {
    constructor(fov?: number, aspect?: number, near?: number, far?: number); aspect: number
    updateProjectionMatrix(): void; lookAt(target: Vector3): void
  }
  export class HemisphereLight extends Object3D { constructor(skyColor?: number, groundColor?: number, intensity?: number) }
  export class DirectionalLight extends Object3D { constructor(color?: number, intensity?: number) }
  export class Raycaster {
    setFromCamera(coords: Vector2, camera: PerspectiveCamera): void
    intersectObjects(objects: Object3D[], recursive?: boolean): { object: Object3D; point: Vector3 }[]
  }
  export class WebGLRenderer {
    constructor(options?: { antialias?: boolean; alpha?: boolean; powerPreference?: string }); domElement: HTMLCanvasElement
    setPixelRatio(ratio: number): void; setSize(width: number, height: number, updateStyle?: boolean): void
    render(scene: Scene, camera: PerspectiveCamera): void; dispose(): void; forceContextLoss(): void
    getContext(): WebGL2RenderingContext; outputColorSpace: string
    info: { memory: { geometries: number; textures: number }; render: { calls: number; triangles: number } }
    renderLists: { dispose(): void }
  }
  export const SRGBColorSpace: string
}
declare module 'three/addons/controls/OrbitControls.js' {
  import { PerspectiveCamera, Vector3 } from 'three'
  export class OrbitControls {
    constructor(camera: PerspectiveCamera, element: HTMLElement); target: Vector3; enabled: boolean
    enableDamping: boolean; enablePan: boolean; minDistance: number; maxDistance: number; maxPolarAngle: number
    update(): boolean; dispose(): void; addEventListener(type: string, listener: () => void): void; removeEventListener(type: string, listener: () => void): void
  }
}
