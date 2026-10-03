import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { archiveAccount as deleteAccount, listAccounts } from '@/api/accounts'
import type { Account } from '@/types/ledger'
import { useModeStore } from '@/stores/mode'
import { translate } from '@/i18n/translate'

export const useAccountStore = defineStore('account', () => {
  const accounts = ref<Account[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  const accountMap = computed(() => {
    const map = new Map<string, Account>()
    for (const a of accounts.value) {
      map.set(a.id, a)
    }
    return map
  })

  // EQUITY accounts are system-managed (e.g. Opening Equity) and hidden from user account UIs.
  const activeAccounts = computed(() =>
    accounts.value.filter((account) => account.type !== 'EQUITY' && account.status === 'ACTIVE'),
  )

  async function archiveAccount(id: string): Promise<void> {
    await deleteAccount(id)
    accounts.value = accounts.value.filter((account) => account.id !== id)
  }

  async function load(): Promise<void> {
    const viewModeStore = useModeStore()
    isLoading.value = true
    error.value = null
    try {
      accounts.value = await listAccounts({
        mode: viewModeStore.modeParam,
        householdId: viewModeStore.householdId ?? undefined,
        includeArchived: true,
      })
    } catch {
      error.value = translate('errors.accounts.load')
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
    accountMap,
    activeAccounts,
    archiveAccount,
    load,
    $reset,
  }
})
