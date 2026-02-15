import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listTxns } from '@/api/ledger'
import type { LedgerTxn, TxnQuery } from '@/types/ledger'
import { useModeStore } from '@/stores/mode'

export const useLedgerStore = defineStore('ledger', () => {
  const transactions = ref<LedgerTxn[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  async function load(filters?: Omit<TxnQuery, 'mode' | 'householdId'>): Promise<void> {
    const viewModeStore = useModeStore()
    isLoading.value = true
    error.value = null
    try {
      transactions.value = await listTxns({
        mode: viewModeStore.modeParam,
        householdId: viewModeStore.householdId ?? undefined,
        ...filters,
      })
    } catch {
      error.value = 'Failed to load transactions.'
    } finally {
      isLoading.value = false
    }
  }

  return {
    transactions,
    isLoading,
    error,
    load,
  }
})
