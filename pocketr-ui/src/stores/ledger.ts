import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listTxns } from '@/api/ledger'
import type { LedgerTxn, TxnQuery } from '@/types/ledger'
import { useModeStore } from '@/stores/mode'

export const useLedgerStore = defineStore('ledger', () => {
  const transactions = ref<LedgerTxn[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  const currentPage = ref(0)
  const pageSize = ref(15)
  const totalPages = ref(0)
  const totalElements = ref(0)

  async function load(
    filters?: Omit<TxnQuery, 'mode' | 'householdId' | 'page' | 'size'>,
    page = currentPage.value,
    size = pageSize.value,
  ): Promise<void> {
    const viewModeStore = useModeStore()
    isLoading.value = true
    error.value = null
    try {
      const result = await listTxns({
        mode: viewModeStore.modeParam,
        householdId: viewModeStore.householdId ?? undefined,
        page,
        size,
        ...filters,
      })
      transactions.value = result.content
      currentPage.value = result.page
      totalPages.value = result.totalPages
      totalElements.value = result.totalElements
    } catch {
      error.value = 'Failed to load transactions.'
    } finally {
      isLoading.value = false
    }
  }

  function $reset(): void {
    transactions.value = []
    isLoading.value = false
    error.value = null
    currentPage.value = 0
    pageSize.value = 15
    totalPages.value = 0
    totalElements.value = 0
  }

  return {
    transactions,
    isLoading,
    error,
    currentPage,
    pageSize,
    totalPages,
    totalElements,
    load,
    $reset,
  }
})
