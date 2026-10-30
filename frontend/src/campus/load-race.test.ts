import { expect,it,vi } from 'vitest'
import { LoadCoordinator,withLoadTimeout } from './LoadCoordinator'
function deferred<T>(){let resolve!:(v:T)=>void;const promise=new Promise<T>(r=>{resolve=r});return {promise,resolve}}
it('timeout_starts_at_actual_load_and_late_current_success_recovers',async()=>{
 vi.useFakeTimers()
 try{
  const queue=deferred<void>(),load=deferred<string>(),feedback=vi.fn();let began=false
  const pending=queue.promise.then(()=>withLoadTimeout(()=>{began=true;return load.promise},feedback))
  await vi.advanceTimersByTimeAsync(20000);expect(began).toBe(false);expect(feedback).not.toHaveBeenCalled()
  queue.resolve();await vi.advanceTimersByTimeAsync(14999);expect(feedback).not.toHaveBeenCalled()
  await vi.advanceTimersByTimeAsync(1);expect(feedback).toHaveBeenCalledOnce()
  load.resolve('current');expect(await pending).toBe('current');expect(vi.getTimerCount()).toBe(0)
 }finally{vi.useRealTimers()}
})
it('old_target_and_old_retry_release_late_values_without_install',async()=>{
 const coordinator=new LoadCoordinator(),old=deferred<string>(),shown:string[]=[],freed:string[]=[]
 const pending=coordinator.run(coordinator.begin(1,'old'),()=>old.promise,v=>shown.push(v),v=>freed.push(v))
 const current=coordinator.begin(3,'new'),retry=coordinator.begin(2,'old')
 expect(await coordinator.run(retry,async()=> 'retry',v=>shown.push(v),v=>freed.push(v))).toBe(false)
 await coordinator.run(current,async()=> 'new',v=>shown.push(v),v=>freed.push(v));old.resolve('old');await pending
 expect(shown).toEqual(['new']);expect(freed).toEqual(['old'])
})
