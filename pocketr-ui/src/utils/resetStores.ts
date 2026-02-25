/**
 * Central session-lifecycle utility.
 *
 * Resets every domain store so the auth store (and the 401 hook) don't need
 * to know about individual stores.  Uses dynamic imports to avoid circular
 * dependency chains.
 */
export async function resetAllDomainStores(): Promise<void> {
  const [
    { useAccountStore },
    { useCategoryStore },
    { useCurrencyStore },
    { useLedgerStore },
    { useHouseholdStore },
    { useModeStore },
  ] = await Promise.all([
    import('@/stores/account'),
    import('@/stores/category'),
    import('@/stores/currency'),
    import('@/stores/ledger'),
    import('@/stores/household'),
    import('@/stores/mode'),
  ])

  useAccountStore().$reset()
  useCategoryStore().$reset()
  useCurrencyStore().$reset()
  useLedgerStore().$reset()
  useHouseholdStore().$reset()
  useModeStore().$reset()
}
