import { computed, onMounted, ref, watch } from 'vue'
import { useAccountStore } from '@/stores/account'
import { useCategoryStore } from '@/stores/category'
import { useCurrencyStore } from '@/stores/currency'
import { useHouseholdStore } from '@/stores/household'
import { useLedgerStore } from '@/stores/ledger'
import { useModeStore } from '@/stores/mode'

/**
 * State of the transactions page that several sections share: the filters, the loaded and paged
 * rows, and the expanded rows (reset on every load). Loads the reference data on mount and reloads
 * from the first page whenever the view mode or a server-side filter changes.
 */
export function useTransactionsList() {
  const ledgerStore = useLedgerStore()
  const accountStore = useAccountStore()
  const categoryStore = useCategoryStore()
  const currencyStore = useCurrencyStore()
  const householdStore = useHouseholdStore()
  const modeStore = useModeStore()

  const filterDateFrom = ref<string | undefined>(undefined)
  const filterDateTo = ref<string | undefined>(undefined)
  const filterAccountId = ref('')
  const filterCategoryId = ref<string | null>(null)
  const filterSearch = ref('')

  // The button that opens the filters counts the ones in use.
  const activeFilterCount = computed(
    () =>
      Number(Boolean(filterSearch.value.trim())) +
      Number(Boolean(filterDateFrom.value || filterDateTo.value)) +
      Number(Boolean(filterAccountId.value)) +
      Number(Boolean(filterCategoryId.value)),
  )

  function clearFilters(): void {
    filterSearch.value = ''
    filterDateFrom.value = undefined
    filterDateTo.value = undefined
    filterAccountId.value = ''
    filterCategoryId.value = null
  }

  // Rows are expanded by transaction id, shared by both list variants.
  const expanded = ref<Record<string, boolean>>({})

  async function loadData(resetPage = false): Promise<void> {
    const filters: Record<string, string | undefined> = {}
    if (filterDateFrom.value) filters.dateFrom = filterDateFrom.value
    if (filterDateTo.value) filters.dateTo = filterDateTo.value
    if (filterAccountId.value) filters.accountId = filterAccountId.value
    if (filterCategoryId.value) filters.categoryId = filterCategoryId.value

    const page = resetPage ? 0 : ledgerStore.currentPage
    if (resetPage) ledgerStore.currentPage = 0
    expanded.value = {}
    await ledgerStore.load(filters, page, ledgerStore.pageSize)
  }

  async function goToPage(page: number): Promise<void> {
    ledgerStore.currentPage = page
    await loadData()
  }

  async function changePageSize(size: number): Promise<void> {
    ledgerStore.pageSize = size
    ledgerStore.currentPage = 0
    await loadData()
  }

  onMounted(async () => {
    await Promise.all([
      accountStore.load(),
      categoryStore.load(),
      currencyStore.load(),
      householdStore.loadHouseholds(),
      loadData(),
    ])
  })

  watch(
    [() => modeStore.viewMode, filterDateFrom, filterDateTo, filterAccountId, filterCategoryId],
    () => {
      void loadData(true)
    },
  )

  // The description search only narrows the page that is already loaded.
  const filteredTransactions = computed(() => {
    const query = filterSearch.value.trim().toLowerCase()
    if (!query) return ledgerStore.transactions
    return ledgerStore.transactions.filter((txn) => txn.description.toLowerCase().includes(query))
  })

  const pagination = computed(() => ({
    page: ledgerStore.currentPage,
    pageSize: ledgerStore.pageSize,
    totalPages: ledgerStore.totalPages,
    totalElements: ledgerStore.totalElements,
  }))

  return {
    filterDateFrom,
    filterDateTo,
    filterAccountId,
    filterCategoryId,
    filterSearch,
    activeFilterCount,
    clearFilters,
    expanded,
    loadData,
    goToPage,
    changePageSize,
    filteredTransactions,
    pagination,
  }
}
