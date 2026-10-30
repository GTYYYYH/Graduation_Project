export interface LoadTicket { generation: number; sequence: number; target: string }
export async function withLoadTimeout<T>(load: () => Promise<T>, feedback: () => void, timeoutMs = 15000): Promise<T> {
  // 动态import不可取消；超时提供反馈，当前代次的迟到成功仍可恢复。
  const timer = setTimeout(feedback, timeoutMs)
  try { return await load() } finally { clearTimeout(timer) }
}
export class LoadCoordinator {
  private generation = -1
  private sequence = 0
  private target = ''
  private disposed = false
  begin(generation: number, target: string): LoadTicket {
    if (this.disposed || generation <= this.generation) return { generation, sequence: -1, target }
    this.generation = generation; this.target = target
    return { generation, sequence: ++this.sequence, target }
  }
  isCurrent(ticket: LoadTicket): boolean {
    return !this.disposed && ticket.sequence === this.sequence && ticket.generation === this.generation && ticket.target === this.target
  }
  cancel(): void { this.sequence++; this.target = '' }
  dispose(): void { this.cancel(); this.disposed = true }
  async run<T>(ticket: LoadTicket, load: () => Promise<T>, install: (value: T) => void, release: (value: T) => void): Promise<boolean> {
    if (!this.isCurrent(ticket)) return false
    let value: T
    try { value = await load() } catch (error) { if (this.isCurrent(ticket)) throw error; return false }
    if (!this.isCurrent(ticket)) { release(value); return false }
    install(value); return true
  }
}
