import { computed, ref } from 'vue'
import { getAccountBalances } from '@/api/ledger'
import { useAccountStore } from '@/stores/account'
import { useCurrencyStore } from '@/stores/currency'
import { useHouseholdStore } from '@/stores/household'
import { useModeStore } from '@/stores/mode'

/**
 * The accounts of the active view mode and their balances, as the Accounts page and the dashboard
 * both load them. `accountIds` names the accounts whose balances are wanted; a newer balance load
 * supersedes an older one, so a slow earlier response cannot overwrite the newer balances.
 */
export function useAccountScope(accountIds: () => string[]) {
  const accountStore = useAccountStore()
  const currencyStore = useCurrencyStore()
  const householdStore = useHouseholdStore()
  const modeStore = useModeStore()

  const balances = ref<Map<string, number>>(new Map())
  const balancesLoading = ref(false)

  const sharedAccountIds = computed(
    () => new Set(householdStore.sharedAccounts.map((share) => share.accountId)),
  )

  /** Loads the accounts, the currencies and, in household mode, the household's shares. */
  async function loadAccounts(): Promise<void> {
    await Promise.all([accountStore.load(), currencyStore.load()])
    if (modeStore.isHousehold && modeStore.householdId) {
      await householdStore.loadSharedAccounts(modeStore.householdId)
    }
  }

  let balancesRequest = 0

  async function loadBalances(): Promise<void> {
    const request = (balancesRequest += 1)
    balancesLoading.value = true
    balances.value = new Map()

    try {
      const result = await getAccountBalances(
        accountIds(),
        undefined,
        modeStore.householdId ?? undefined,
      )
      if (request !== balancesRequest) return
      const loaded = new Map<string, number>()
      for (const balance of result) loaded.set(balance.accountId, balance.balanceMinor)
      balances.value = loaded
    } catch {
      // Balances stay unknown when they cannot be loaded.
    } finally {
      if (request === balancesRequest) balancesLoading.value = false
    }
  }

  return { balances, balancesLoading, sharedAccountIds, loadAccounts, loadBalances }
}
