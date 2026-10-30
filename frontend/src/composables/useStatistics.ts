import { computed, toValue } from 'vue'
import type { MaybeRefOrGetter } from 'vue'
import { fetchStatisticsDashboard } from '../api/statistics'
import type { StatisticsVo } from '../api/statistics'
import { useAuthStore } from '../stores/auth'
import { useCampusRefresh } from './useCampusRefresh'

export function useStatistics(month: MaybeRefOrGetter<string>) {
  const auth = useAuthStore()
  const read = useCampusRefresh<StatisticsVo>(() => auth.currentUser?.role === 'ADMIN' ? `statistics/${toValue(month)}` : null,
    signal => fetchStatisticsDashboard(toValue(month), { signal }), undefined, { poll: false })
  return { ...read, displayedMonth: computed(() => read.data.value?.month ?? null) }
}
