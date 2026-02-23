import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { listAccounts } from '@/api/accounts'
import type { Account, AccountType } from '@/types/ledger'
import { useModeStore } from '@/stores/mode'

export const useAccountStore = defineStore('account', () => {
  const accounts = ref<Account[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  const accountsByType = computed(() => {
    const map = new Map<AccountType, Account[]>()
    for (const a of accounts.value) {
      const list = map.get(a.type) ?? []
      list.push(a)
      map.set(a.type, list)
    }
    return map
  })

  const accountMap = computed(() => {
    const map = new Map<string, Account>()
    for (const a of accounts.value) {
      map.set(a.id, a)
    }
    return map
  })

  // EQUITY accounts are system-managed (e.g. Opening Equity) and hidden from user account UIs.
  const activeAccounts = computed(() =>
    accounts.value.filter((account) => account.type !== 'EQUITY'),
  )

  async function load(): Promise<void> {
    const viewModeStore = useModeStore()
    isLoading.value = true
    error.value = null
    try {
      accounts.value = await listAccounts({
        mode: viewModeStore.modeParam,
        householdId: viewModeStore.householdId ?? undefined,
      })
    } catch {
      error.value = 'Failed to load accounts.'
    } finally {
      isLoading.value = false
    }
  }

  function $reset(): void {
    accounts.value = []
    isLoading.value = false
    error.value = null
  }

  return {
    accounts,
    isLoading,
    error,
    accountsByType,
    accountMap,
    activeAccounts,
    load,
    $reset,
  }
})
