import type { CameraAnchor, Vec3 } from './types'
interface Motion { from: CameraAnchor; to: CameraAnchor; start: number; valid: () => boolean; progress?: (value: number) => void; resolve: (complete: boolean) => void }
export class CameraTransition {
  private motion: Motion | null = null
  get active() { return this.motion !== null }
  constructor(private readonly apply: (anchor: CameraAnchor) => void) {}
  start(from: CameraAnchor, to: CameraAnchor, now: number, reduced: boolean, valid: () => boolean, progress?: (value: number) => void): Promise<boolean> {
    this.cancel()
    if (!valid()) return Promise.resolve(false)
    if (reduced) { this.apply(to); progress?.(1); return Promise.resolve(true) }
    return new Promise(resolve => { this.motion = { from, to, start: now, valid, progress, resolve } })
  }
  tick(now: number): void {
    const motion = this.motion
    if (!motion) return
    if (!motion.valid()) { this.cancel(); return }
    const p = Math.min(1, Math.max(0, (now - motion.start) / 650)), eased = p * p * (3 - 2 * p)
    const lerp = (a: Vec3, b: Vec3): Vec3 => [a[0] + (b[0] - a[0]) * eased, a[1] + (b[1] - a[1]) * eased, a[2] + (b[2] - a[2]) * eased]
    this.apply({ position: lerp(motion.from.position, motion.to.position), target: lerp(motion.from.target, motion.to.target) })
    motion.progress?.(eased)
    if (p === 1) { this.motion = null; motion.resolve(true) }
  }
  cancel(): void {
    const motion = this.motion
    this.motion = null; motion?.resolve(false)
  }
}
