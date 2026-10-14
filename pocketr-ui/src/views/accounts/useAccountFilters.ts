import { computed, ref, watch } from 'vue'
import { useAccountStore } from '@/stores/account'

export const ALL_FILTER = 'ALL'

/** The Accounts page filters (type, currency) and the accounts they leave in the list. */
export function useAccountFilters() {
  const accountStore = useAccountStore()

  const typeFilter = ref<string>(ALL_FILTER)
  const currencyFilter = ref<string>(ALL_FILTER)

  // The filters stay out of the way until asked for; the button counts the ones in use.
  const isFiltersOpen = ref(false)

  const activeFilterCount = computed(
    () => Number(typeFilter.value !== ALL_FILTER) + Number(currencyFilter.value !== ALL_FILTER),
  )

  function clearFilters(): void {
    typeFilter.value = ALL_FILTER
    currencyFilter.value = ALL_FILTER
  }

  const filteredAccounts = computed(() => {
    let list = accountStore.activeAccounts
    if (typeFilter.value !== ALL_FILTER) list = list.filter((a) => a.type === typeFilter.value)
    if (currencyFilter.value !== ALL_FILTER) {
      list = list.filter((a) => a.currency === currencyFilter.value)
    }
    return list
  })

  const availableCurrencies = computed(() =>
    [...new Set(accountStore.activeAccounts.map((a) => a.currency))].sort(),
  )

  // An archived account can take the selected currency out of the list; fall back to all currencies.
  watch(availableCurrencies, (codes) => {
    if (currencyFilter.value !== ALL_FILTER && !codes.includes(currencyFilter.value)) {
      currencyFilter.value = ALL_FILTER
    }
  })

  return {
    typeFilter,
    currencyFilter,
    isFiltersOpen,
    activeFilterCount,
    availableCurrencies,
    filteredAccounts,
    clearFilters,
  }
}
