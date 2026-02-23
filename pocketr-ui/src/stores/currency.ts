import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { listCurrencies } from '@/api/currencies'
import type { Currency } from '@/types/ledger'

export const useCurrencyStore = defineStore('currency', () => {
  const currencies = ref<Currency[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  const currencyMap = computed(() => {
    const map = new Map<string, Currency>()
    for (const c of currencies.value) {
      map.set(c.code, c)
    }
    return map
  })

  function getMinorUnit(code: string): number {
    return currencyMap.value.get(code)?.minorUnit ?? 2
  }

  async function load(): Promise<void> {
    if (currencies.value.length > 0) return
    isLoading.value = true
    error.value = null
    try {
      currencies.value = await listCurrencies()
    } catch {
      error.value = 'Failed to load currencies.'
    } finally {
      isLoading.value = false
    }
  }

  function $reset(): void {
    currencies.value = []
    isLoading.value = false
    error.value = null
  }

  return {
    currencies,
    isLoading,
    error,
    currencyMap,
    getMinorUnit,
    load,
    $reset,
  }
})
