import { computed, ref } from 'vue'
import { useAccountStore } from '@/stores/account'
import { useAccountScope } from '@/utils/useAccountScope'
import { useReloadOnViewMode } from '@/utils/useReloadOnViewMode'

/**
 * What the Accounts page shows: the accounts, their balances and the household shares. Loads on
 * mount and whenever the view mode changes; `loadAll` reloads after an account is created or archived.
 */
export function useAccountsData() {
  const accountStore = useAccountStore()
  const { balances, balancesLoading, sharedAccountIds, loadAccounts, loadBalances } =
    useAccountScope(() => accountStore.activeAccounts.map((account) => account.id))

  // Rows are expanded by account id on the phone list, which shows the row actions in place.
  const expanded = ref<Record<string, boolean>>({})

  // Only the first load swaps the page for placeholders; later reloads keep the rows on screen.
  const isInitialLoad = computed(
    () => (accountStore.isLoading && accountStore.accounts.length === 0) || balancesLoading.value,
  )

  async function loadAll(): Promise<void> {
    expanded.value = {}
    await loadAccounts()
    await loadBalances()
  }

  useReloadOnViewMode(loadAll)

  return { balances, expanded, sharedAccountIds, isInitialLoad, loadAll }
}
