import { Color, DirectionalLight, HemisphereLight, PerspectiveCamera, Raycaster, Scene, Vector2, Vector3, WebGLRenderer, SRGBColorSpace } from 'three'
import type { Object3D } from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import type { CatalogVo } from '../api/account'
import { CameraTransition } from './CameraTransition'
import { createDormitoryExterior } from './DormitoryExterior'
import { createTeachingBuilding } from './templates/TeachingBuilding'
import { createCafeteriaBuilding } from './templates/CafeteriaBuilding'
import { createCampusLayout } from './CampusLayout'
import { LoadCoordinator } from './LoadCoordinator'
import { ResourceCache } from './ResourceCache'
import { bindManifest, createDormFloor } from './templates/DormFloor'
import type { RoomInstance } from './templates/DormRoom'
import type { CameraAnchor, CampusBinding, CampusManifest, CampusTarget, RendererState } from './types'

interface Options {
  catalog: CatalogVo; reducedMotion: () => boolean
  onState: (state: RendererState) => void; onSelect: (target: CampusTarget) => void; onEnter: (id: string) => void
  onFrame: () => void; onRestore: () => void
}
const overview: CameraAnchor = { position: [220, 180, 210], target: [35, 0, -5] }

export class CampusRenderer {
  private readonly pool = new ResourceCache()
  private readonly loads = new LoadCoordinator()
  private readonly scene = new Scene()
  private readonly camera = new PerspectiveCamera(45, 1, .07, 700)
  private readonly raycaster = new Raycaster()
  private readonly motion = new CameraTransition(anchor => this.applyCamera(anchor))
  private readonly interiors = new Map<string, RoomInstance>()
  private container: HTMLElement | null = null
  private renderer: WebGLRenderer | null = null
  private controls: OrbitControls | null = null
  private exterior: ReturnType<typeof createDormitoryExterior> | null = null
  private readonly exteriors = new Map<string, ReturnType<typeof createDormitoryExterior>>()
  private selectedBuildingId: string | null = null
  private environment: ReturnType<typeof createCampusLayout> | null = null
  private floor: ReturnType<typeof createDormFloor> | null = null
  private selectedFloorId: string | null = null
  private floorContextId: string | null = null
  private manifest: CampusManifest | null = null
  private binding: CampusBinding | null = null
  private observer: ResizeObserver | null = null
  private frame = 0
  private disposed = false
  private lost = false
  private timer: ReturnType<typeof setTimeout> | null = null
  private down: [number, number] | null = null
  private frameTimes: number[] = []
  private previousFrame = 0
  private lastDiagnostics = 0
  private state: RendererState = { level: 'OVERVIEW', locationId: null, loading: false, error: '' }
  constructor(private readonly options: Options) {}

  async mount(container: HTMLElement, manifest: CampusManifest): Promise<void> {
    if (this.disposed || this.container) throw new Error('渲染实例已挂载或释放')
    this.container = container; this.manifest = manifest
    this.binding = bindManifest(manifest, this.options.catalog)
    try {
      this.renderer = new WebGLRenderer({ antialias: true, powerPreference: 'high-performance' })
      if (!(this.renderer.getContext() instanceof WebGL2RenderingContext)) throw new Error('浏览器未提供WebGL2')
      this.renderer.outputColorSpace = SRGBColorSpace; this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5))
      const canvas = this.renderer.domElement
      canvas.setAttribute('aria-label', '校园三维示意场景，位置选择与进入按钮提供键盘等价入口')
      canvas.style.width = '100%'; canvas.style.height = '100%'; canvas.style.display = 'block'; canvas.style.touchAction = 'none'
      container.prepend(canvas)
      this.controls = new OrbitControls(this.camera, canvas)
      this.controls.enableDamping = false; this.controls.minDistance = .5; this.controls.maxDistance = 400; this.controls.maxPolarAngle = Math.PI * .49
      // 未注册OrbitControls键盘监听；输入框/选择器焦点不驱动相机。
      this.scene.background = new Color('#e8eef0')
      this.scene.add(new HemisphereLight(0xffffff, 0xc4c8bb, 2.4))
      const sun = new DirectionalLight(0xfff8ed, 2.2); sun.position.set(15, 28, -10); this.scene.add(sun)
      for (const building of this.binding.buildings) {
        const exterior = building.type === 'TEACHING' ? createTeachingBuilding(this.pool) : building.type === 'CANTEEN' ? createCafeteriaBuilding(this.pool) : building.type === 'GYM' ? createTeachingBuilding(this.pool,2,32,34,7.5) : createDormitoryExterior(this.pool)
        exterior.group.userData.buildingCode = building.code
        exterior.group.position.set(...(manifest.buildings.find(b => b.buildingCode === building.code)!.origin ?? [0,0,0]))
        this.exteriors.set(building.id, exterior); this.scene.add(exterior.group)
      }
      this.exterior = this.exteriors.get(this.binding.building.id)!
      this.environment=createCampusLayout(this.pool);this.scene.add(this.environment.group)
      this.selectedBuildingId = this.binding.building.id; this.applyCamera(overview)
      this.observer = new ResizeObserver(() => this.resize()); this.observer.observe(container); this.resize()
      canvas.addEventListener('pointerdown', this.pointerDown); canvas.addEventListener('pointerup', this.pointerUp)
      canvas.addEventListener('pointercancel', this.pointerCancel)
      canvas.addEventListener('webglcontextlost', this.contextLost); canvas.addEventListener('webglcontextrestored', this.contextRestored)
      document.addEventListener('visibilitychange', this.visibility)
      this.commit({ ...this.state, error: '' }); this.startLoop()
    } catch (error) {
      this.dispose()
      throw new Error(`3D初始化失败：${error instanceof Error ? error.message : 'WebGL2不可用'}。可继续选择位置或打开普通业务入口。`)
    }
  }
  private commit(state: RendererState) { if (this.disposed) return; this.state = state; this.options.onState({ ...state }); this.diagnostics() }
  private resize() {
    if (!this.container || !this.renderer || this.disposed) return
    const { width, height } = this.container.getBoundingClientRect()
    if (!width || !height) return
    this.renderer.setSize(width, height, false); this.camera.aspect = width / height; this.camera.updateProjectionMatrix()
  }
  private applyCamera(anchor: CameraAnchor) {
    if (!this.controls || this.disposed) return
    this.camera.position.set(...anchor.position); this.controls.target.set(...anchor.target); this.camera.lookAt(this.controls.target); this.controls.update()
  }
  private cameraAnchor(): CameraAnchor { return { position: [this.camera.position.x, this.camera.position.y, this.camera.position.z], target: [this.controls!.target.x, this.controls!.target.y, this.controls!.target.z] } }
  private animate(anchor: CameraAnchor, valid: () => boolean, progress?: (value: number) => void) {
    if (!this.controls) return Promise.resolve(false)
    this.controls.enabled = this.options.reducedMotion()
    return this.motion.start(this.cameraAnchor(), anchor, performance.now(), this.options.reducedMotion(), valid, progress)
  }
  private ensureFloor() {
    if (this.floorContextId !== this.selectedFloorId && this.floor) {
      this.scene.remove(this.floor.group); this.floor.dispose(); this.floor = null
    }
    if (!this.floor) {
      const codes = new Set(this.binding!.locations.filter(l => l.location.floorId === this.selectedFloorId).map(l => l.location.code))
      this.floor = createDormFloor(this.pool, { ...this.manifest!, locations: this.manifest!.locations.filter(l => codes.has(l.locationCode)) })
      this.floor.group.position.set(...this.buildingOrigin())
      this.floorContextId = this.selectedFloorId; this.scene.add(this.floor.group)
    }
    return this.floor
  }
  private floorAnchor(): CameraAnchor {
    const level = this.binding?.floors.find(f => f.id === this.selectedFloorId)?.level ?? 1
    const building = this.binding?.buildings.find(b=>b.id===this.selectedBuildingId)
    const y = (level - 1) * (building?.type==='GYM'?7.2:3.15)
    return this.worldAnchor(building?.type === 'TEACHING'
      ? { position: [-60, y + 70, 0], target: [0, y, 0] }
      : { position: [23, y + 28, -29], target: [0, y, -1] })
  }
  private buildingOrigin(): [number,number,number] {
    const building = this.binding!.buildings.find(b => b.id === this.selectedBuildingId)!
    return this.manifest!.buildings.find(b => b.buildingCode === building.code)!.origin ?? [0,0,0]
  }
  private worldAnchor(anchor: CameraAnchor): CameraAnchor {
    const [x,y,z] = this.buildingOrigin()
    return { position: [anchor.position[0]+x,anchor.position[1]+y,anchor.position[2]+z], target:[anchor.target[0]+x,anchor.target[1]+y,anchor.target[2]+z] }
  }
  private showFloor(interiorId: string | null = null) {
    const floor = this.ensureFloor()
    for (const [id, exterior] of this.exteriors) exterior.group.visible = id === this.selectedBuildingId
    floor.group.visible = interiorId === null
    const level = this.binding!.floors.find(f => f.id === this.selectedFloorId)!.level
    this.exterior!.levels.forEach((l, index) => { l.visible = interiorId === null && index < level })
    this.exterior!.roof.visible = false; this.exterior!.porch.visible = false
    this.exterior!.stairs.visible = interiorId === null
    this.exterior!.stairs.children.forEach(flight => { flight.visible = Number(flight.userData.level) <= level })
    for (const entry of this.binding!.locations) {
      const entrance = floor.entrances.get(entry.location.code)
      if (entrance) entrance.group.visible = entry.location.id !== interiorId
    }
    for (const [id, room] of this.interiors) room.group.visible = id === interiorId
    const kind=this.binding!.locations.find(l=>l.location.id===interiorId)?.geometry.templateKey
    this.controls!.maxDistance = interiorId ? kind==='dorm-room'?9:kind==='classroom'?40:kind==='cafeteria'||kind?.startsWith('gym-')?70:20 : 100
  }
  private showOverview() {
    if (this.floor) this.floor.group.visible = false
    for (const room of this.interiors.values()) room.group.visible = false
    for (const exterior of this.exteriors.values()) {
      exterior.group.visible = true; exterior.levels.forEach(l => { l.visible = true }); exterior.roof.visible = true; exterior.porch.visible = true
      exterior.stairs.visible = true; exterior.stairs.children.forEach(flight => { flight.visible = true })
    }
    this.controls!.maxDistance = 400
  }
  private cancel() {
    this.motion.cancel(); if (this.controls) this.controls.enabled = true
    if (this.timer) clearTimeout(this.timer); this.timer = null
  }
  async selectTarget(target: CampusTarget, generation: number): Promise<void> {
    if (this.disposed || this.lost || !this.binding) return
    const key = target.kind === 'LOCATION' ? target.locationId : target.kind === 'FLOOR' ? target.floorId : target.kind === 'BUILDING' ? target.buildingId : 'overview'
    const ticket = this.loads.begin(generation, `${target.kind}:${key}`)
    if (!this.loads.isCurrent(ticket)) return
    this.cancel()
    const start = performance.now(), bound = target.kind === 'LOCATION' ? this.binding.locations.find(l => l.location.id === target.locationId) : null
    const supported = target.kind === 'OVERVIEW' || (target.kind === 'BUILDING' && this.binding.buildings.some(b => b.id === target.buildingId))
      || (target.kind === 'FLOOR' && this.binding.floors.some(f => f.id === target.floorId)) || !!bound
    if (!supported) {
      this.showOverview(); this.applyCamera(overview)
      this.commit({ level: 'OVERVIEW', locationId: null, loading: false, error: '当前选择尚未制作3D。本批提供文博1外观及1层12处内景，可继续核对目录或打开普通业务入口。' }); return
    }
    const level = bound ? 'DOOR' : target.kind === 'FLOOR' ? 'FLOOR' : target.kind === 'BUILDING' ? 'BUILDING' : 'OVERVIEW'
    if (bound) this.selectedFloorId = bound.location.floorId
    else if (target.kind === 'FLOOR') this.selectedFloorId = target.floorId
    const selectedBuilding = target.kind === 'BUILDING' ? target.buildingId : this.binding.floors.find(f => f.id === this.selectedFloorId)?.buildingId
    if (selectedBuilding) { this.selectedBuildingId = selectedBuilding; this.exterior = this.exteriors.get(selectedBuilding)! }
    if (bound || level === 'FLOOR') this.showFloor(); else this.showOverview()
    this.commit({ level, locationId: bound?.location.id ?? null, loading: true, error: '' })
    const anchor = bound ? this.worldAnchor(bound.geometry.entryAnchor) : level === 'FLOOR' ? this.floorAnchor() : level === 'BUILDING' ? this.worldAnchor({position:[38,29,-43],target:[0,6,-1]}) : overview
    if (await this.animate(anchor, () => this.loads.isCurrent(ticket) && !this.lost)) {
      this.controls!.enabled = true
      this.commit({ level, locationId: bound?.location.id ?? null, loading: false, error: '', elapsedMs: performance.now() - start, condition: 'warm' })
    }
  }
  async enterLocation(id: string, generation: number): Promise<void> {
    if (this.disposed || this.lost || !this.binding || this.state.locationId !== id || this.state.level !== 'DOOR') return
    const bound = this.binding.locations.find(l => l.location.id === id)
    if (!bound) return
    const ticket = this.loads.begin(generation, `INTERIOR:${id}`)
    if (!this.loads.isCurrent(ticket)) return
    this.cancel()
    const start = performance.now(), condition = this.interiors.has(id) ? 'warm' : 'cold'
    this.commit({ level: 'DOOR', locationId: id, loading: true, error: '' })
    let movement: Promise<boolean> = Promise.resolve(false)
    let moduleFailed = false
    try {
      const installed = await this.loads.run(ticket, async () => {
        const cached = this.interiors.get(id)
        if (cached) return { room: cached, created: false }
        this.timer = setTimeout(() => { if (this.loads.isCurrent(ticket)) this.commit({ ...this.state, failure: 'INTERIOR', error: '此位置内景加载已超过15秒，可重试或打开普通业务入口。' }) }, 15000)
        let create: () => RoomInstance
        const kind = bound.geometry.templateKey
        try {
          if(kind==='dorm-room') {const m=await import('./templates/DormRoom');create=()=>m.createDormRoom(this.pool)}
          else if(kind==='dorm-lobby'||kind==='dorm-duty') {const m=await import('./templates/DormCommon');create=()=>m.createDormCommon(this.pool,kind)}
          else if(kind==='classroom') {const m=await import('./templates/Classroom');create=()=>m.createClassroom(this.pool,bound.geometry.roomSize)}
          else if(kind==='office') {const m=await import('./templates/Office');create=()=>m.createOffice(this.pool,bound.geometry.roomSize)}
          else if(kind==='teaching-restroom'||kind==='teaching-lobby') {const m=await import('./templates/TeachingCommon');create=()=>m.createTeachingCommon(this.pool,kind,bound.geometry.roomSize)}
          else if(kind==='cafeteria') {const m=await import('./templates/CafeteriaFloor');create=()=>m.createCafeteriaFloor(this.pool)}
          else {const m=await import('./templates/GymBuilding');create=()=>m.createGymInterior(this.pool,kind)}
        } catch (error) { moduleFailed = true; throw error }
        if (!this.loads.isCurrent(ticket)) throw new Error('cancelled target')
        const room = create()
        const [x,y,z] = this.buildingOrigin()
        room.group.position.set(bound.geometry.origin[0]+x,bound.geometry.origin[1]+y,bound.geometry.origin[2]+z); room.group.rotation.y = bound.geometry.rotationY
        return { room, created: true }
      }, ({ room, created }) => {
        if (this.timer) clearTimeout(this.timer); this.timer = null
        if (created) this.scene.add(room.group)
        this.interiors.delete(id); this.interiors.set(id, room)
        while (this.interiors.size > 2) { const oldest = this.interiors.keys().next().value!; const unused = this.interiors.get(oldest)!; this.scene.remove(unused.group); unused.dispose(); this.interiors.delete(oldest) }
        this.showFloor(id)
        const door = this.ensureFloor().entrances.get(bound.location.code)!
        this.commit({ ...this.state, error: '' })
        // 第一帧完成资源提交后才开始过渡，冷计时仍从点击开始。
        this.renderer!.render(this.scene, this.camera)
        movement = this.animate(this.worldAnchor(bound.geometry.interiorAnchor), () => this.loads.isCurrent(ticket) && !this.lost,
          p => { room.setDoorProgress(p); door.setDoorProgress(p) })
      }, value => { if (value.created) value.room.dispose() })
      if (installed && await movement && this.loads.isCurrent(ticket)) {
        this.controls!.enabled = true
        this.renderer!.render(this.scene, this.camera)
        this.commit({ level: 'INTERIOR', locationId: id, loading: false, error: '', elapsedMs: performance.now() - start, condition })
      }
    } catch (error) {
      if (this.loads.isCurrent(ticket)) {
        this.cancel(); this.showFloor()
        this.commit({ level: 'DOOR', locationId: id, loading: false, failure: moduleFailed ? 'MODULE' : 'INTERIOR', error: moduleFailed
          ? '此位置内景代码下载失败，重试将重新加载页面并保留当前位置。也可打开普通业务入口。'
          : `此位置内景失败：${error instanceof Error ? error.message : '请重试'}。目录和普通业务入口仍可使用。` })
      }
    }
  }
  returnToFloor(): void {
    if (this.disposed || this.lost || !this.exterior) return
    this.loads.cancel(); this.cancel(); this.showFloor(); this.applyCamera(this.floorAnchor())
    this.commit({ level: 'FLOOR', locationId: null, loading: false, error: '' })
  }
  resetOverview(): void {
    if (this.disposed || this.lost || !this.exterior) return
    this.loads.cancel(); this.cancel(); this.showOverview(); this.applyCamera(overview)
    this.commit({ level: 'OVERVIEW', locationId: null, loading: false, error: '' })
  }
  layoutSlot(locationId: string): number | undefined {
    return this.binding?.locations.find(l=>l.location.id===locationId)?.geometry.layoutSlot
  }
  projectAnchor(locationId: string): { x: number; y: number; visible: boolean } | null {
    if (!this.container || !this.binding || !this.floor?.group.visible || this.disposed) return null
    const bound = this.binding.locations.find(l => l.location.id === locationId)
    if (!bound || bound.location.floorId !== this.selectedFloorId) return null
    const anchor = new Vector3(0, 2.68, bound.geometry.roomSize ? bound.geometry.roomSize[1] / 2 : 0); this.floor.entrances.get(bound.location.code)!.group.localToWorld(anchor); anchor.project(this.camera)
    return { x: (anchor.x + 1) / 2 * this.container.clientWidth, y: (1 - anchor.y) / 2 * this.container.clientHeight,
      visible: anchor.z >= -1 && anchor.z <= 1 && Math.abs(anchor.x) < .96 && Math.abs(anchor.y) < .92 }
  }
  projectBuildingAnchor(id:string): {x:number;y:number;visible:boolean}|null {
    if(!this.container||this.disposed||!['OVERVIEW','BUILDING'].includes(this.state.level))return null
    const exterior=this.exteriors.get(id);if(!exterior)return null
    const anchor=new Vector3(0,18,0);exterior.group.localToWorld(anchor);anchor.project(this.camera)
    return {x:(anchor.x+1)/2*this.container.clientWidth,y:(1-anchor.y)/2*this.container.clientHeight,visible:anchor.z>=-1&&anchor.z<=1&&Math.abs(anchor.x)<.98&&Math.abs(anchor.y)<.98}
  }
  private pointerDown = (event: PointerEvent) => { this.down = [event.clientX, event.clientY] }
  private pointerCancel = () => { this.down = null }
  private pointerUp = (event: PointerEvent) => {
    const down = this.down; this.down = null
    if (!down || Math.hypot(event.clientX - down[0], event.clientY - down[1]) > 5 || this.motion.active || this.disposed || this.lost || !this.renderer) return
    const rect = this.renderer.domElement.getBoundingClientRect()
    this.raycaster.setFromCamera(new Vector2((event.clientX - rect.left) / rect.width * 2 - 1, 1 - (event.clientY - rect.top) / rect.height * 2), this.camera)
    for (const hit of this.raycaster.intersectObjects(this.scene.children, true)) {
      let node: Object3D | null = hit.object, visible = true
      while (node) { if (!node.visible) visible = false; node = node.parent }
      if (!visible) continue
      node = hit.object
      while (node) {
        if (typeof node.userData.locationCode === 'string') {
          const bound = this.binding!.locations.find(l => l.location.code === node!.userData.locationCode)
          if (bound) { if (this.state.level === 'DOOR' && this.state.locationId === bound.location.id) this.options.onEnter(bound.location.id)
            else this.options.onSelect({ kind: 'LOCATION', locationId: bound.location.id }); return }
        }
        const building = this.binding!.buildings.find(b => b.code === node!.userData.buildingCode)
        if (building) { this.options.onSelect({ kind: 'BUILDING', buildingId: building.id }); return }
        node = node.parent
      }
    }
  }
  private startLoop() { if (!this.disposed && !this.lost && !document.hidden && !this.frame) this.frame = requestAnimationFrame(this.draw) }
  private draw = (now: number) => {
    this.frame = 0
    if (this.disposed || this.lost || document.hidden) return
    this.motion.tick(now); this.controls?.update()
    for (const room of this.interiors.values()) if (room.group.visible) room.updateCutaway([this.camera.position.x, this.camera.position.y, this.camera.position.z])
    this.renderer!.render(this.scene, this.camera); this.options.onFrame()
    if (this.previousFrame && !this.state.loading) { this.frameTimes.push(now - this.previousFrame); if (this.frameTimes.length > 300) this.frameTimes.shift() }
    this.previousFrame = now
    if (now - this.lastDiagnostics > 250) { this.lastDiagnostics = now; this.diagnostics() }
    this.startLoop()
  }
  private visibility = () => { if (document.hidden) { cancelAnimationFrame(this.frame); this.frame = 0; this.previousFrame = 0 } else this.startLoop() }
  private contextLost = (event: Event) => {
    event.preventDefault(); this.lost = true; this.loads.cancel(); this.cancel(); cancelAnimationFrame(this.frame); this.frame = 0
    this.commit({ ...this.state, loading: false, failure: 'WEBGL', error: 'WebGL上下文已丢失。位置仍保留，可重试3D或打开普通业务入口。' })
  }
  private contextRestored = () => { if (!this.disposed) this.options.onRestore() }
  private diagnostics() {
    if (!import.meta.env.DEV || !this.container) return
    const ctx = this.renderer?.getContext(), ext = ctx?.getExtension('WEBGL_debug_renderer_info')
    this.container.dataset.diagnostics = JSON.stringify({ disposed: this.disposed, running: !this.disposed && !this.lost && !document.hidden,
      contextLost: ctx?.isContextLost() ?? true,
      state: this.state, camera: this.cameraAnchorSafe(), cacheEntries: this.interiors.size, resources: this.pool.stats(),
      render: this.renderer?.info.render, memory: this.renderer?.info.memory, frameTimes: this.frameTimes,
      gpu: ext && ctx ? String(ctx.getParameter(ext.UNMASKED_RENDERER_WEBGL)) : 'unavailable',
      doors: this.floor ? [...this.floor.entrances].map(([code, e]) => ({ code, open: e.door.open, angle: e.door.pivot.rotation.y })) : [] })
  }
  private cameraAnchorSafe() { return this.controls ? this.cameraAnchor() : null }
  dispose(): void {
    if (this.disposed) return
    this.disposed = true; this.loads.dispose(); this.cancel(); cancelAnimationFrame(this.frame); this.frame = 0
    this.observer?.disconnect(); this.observer = null; document.removeEventListener('visibilitychange', this.visibility)
    const canvas = this.renderer?.domElement
    canvas?.removeEventListener('pointerdown', this.pointerDown); canvas?.removeEventListener('pointerup', this.pointerUp); canvas?.removeEventListener('pointercancel', this.pointerCancel)
    canvas?.removeEventListener('webglcontextlost', this.contextLost); canvas?.removeEventListener('webglcontextrestored', this.contextRestored)
    this.controls?.dispose()
    for (const room of this.interiors.values()) room.dispose(); this.interiors.clear()
    this.floor?.dispose(); this.floor = null
    for (const exterior of this.exteriors.values()) exterior.dispose(); this.exteriors.clear(); this.exterior = null
    this.environment?.dispose();this.environment=null;this.pool.dispose(); this.scene.clear()
    this.renderer?.renderLists.dispose(); this.renderer?.dispose(); this.renderer?.forceContextLoss(); canvas?.remove()
    this.diagnostics(); this.options.onFrame()
    this.controls = null; this.renderer = null; this.binding = null; this.manifest = null; this.container = null
  }
}
