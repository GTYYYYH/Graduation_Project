import { beforeEach, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useDraftStore } from '../../stores/drafts'
import type { ActionDraft } from '../../stores/drafts'
beforeEach(() => setActivePinia(createPinia()))
it('new_round_needs_new_result', () => {
  const store = useDraftStore(), first = store.action('9007199254740993', 'REPAIR_RESULT', '9007199254740994/1') as ActionDraft & { result?: string }
  first.result = '第一轮处理结果'
  const nextRoundDraft = store.action('9007199254740993', 'REPAIR_RESULT', '9007199254740994/2') as ActionDraft & { result?: string }
  expect(nextRoundDraft.result).toBe('')
})
