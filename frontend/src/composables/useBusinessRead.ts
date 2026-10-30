import type { MaybeRefOrGetter } from 'vue'
import type { ReadResult } from '../api/common'
import { useCampusRefresh } from './useCampusRefresh'
export function useBusinessRead<T>(loader: (signal: AbortSignal) => Promise<ReadResult<T>>, onForbidden?: () => void, options: { target?: MaybeRefOrGetter<string | null>; poll?: boolean } = {}) {
  // Existing callers still control their initial read; details opt into periodic checks.
  return useCampusRefresh(options.target ?? 'business-read', loader, onForbidden, {
    poll: options.poll ?? false, immediate: options.poll ?? false,
    reconcile(previous, next) {
      // Confirmations/uploads compare the authorized snapshot by reference. A read
      // timestamp alone is not a business change; all other fields remain guarded.
      if (previous && typeof previous === 'object' && next && typeof next === 'object') {
        const { queriedAt: beforeTime, ...before } = previous as Record<string, unknown>
        const { queriedAt: nextTime, ...after } = next as Record<string, unknown>
        if (JSON.stringify(before) === JSON.stringify(after)) {
          if (beforeTime !== undefined && nextTime !== undefined) Object.assign(previous, { queriedAt: nextTime })
          return previous
        }
      }
      return next
    },
  })
}
