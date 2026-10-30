export interface Disposable { dispose(): void }
export interface ResourceLease<T> { value: T; release(): void }
interface Entry { value: Disposable; references: number; bytes: number }
// 仅缓存geometry/material/texture，所有实例门状态和业务身份由调用方分别持有。
export class ResourceCache {
  private entries = new Map<string, Entry>()
  private disposed = false
  acquire<T extends Disposable>(key: string, factory: () => T, bytes: number): ResourceLease<T> {
    if (this.disposed) throw new Error('resource pool disposed')
    let entry = this.entries.get(key)
    if (!entry) {
      if (!Number.isFinite(bytes) || bytes < 0 || this.stats().estimatedBytes + bytes > 96 * 1024 * 1024) throw new Error('resource budget exceeded')
      entry = { value: factory(), references: 0, bytes }; this.entries.set(key, entry)
    }
    entry.references++
    const owned = entry
    let released = false
    return { value: owned.value as T, release: () => {
      if (released) return
      released = true
      if (this.entries.get(key) !== owned) return
      if (--owned.references === 0) { this.entries.delete(key); owned.value.dispose() }
    } }
  }
  stats() {
    return { entries: this.entries.size, references: [...this.entries.values()].reduce((n, e) => n + e.references, 0),
      estimatedBytes: [...this.entries.values()].reduce((n, e) => n + e.bytes, 0) }
  }
  dispose(): void { this.disposed = true; for (const e of this.entries.values()) e.value.dispose(); this.entries.clear() }
}
// 每个实例拥有一个scope；同一scope中的同一材质只增加一次引用。
export class ResourceScope {
  private leases = new Map<string, ResourceLease<Disposable>>()
  private disposed = false
  constructor(private readonly pool: ResourceCache) {}
  use<T extends Disposable>(key: string, factory: () => T, bytes: number): T {
    if (this.disposed) throw new Error('resource scope disposed')
    let lease = this.leases.get(key)
    if (!lease) { lease = this.pool.acquire(key, factory, bytes); this.leases.set(key, lease) }
    return lease.value as T
  }
  dispose(): void { if (this.disposed) return; this.disposed = true; for (const lease of this.leases.values()) lease.release(); this.leases.clear() }
}
