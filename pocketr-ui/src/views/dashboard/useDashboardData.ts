import { computed, ref } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { useAccountStore } from '@/stores/account'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { useModeStore } from '@/stores/mode'
import { listTxns } from '@/api/ledger'
import { getLifetimeExpenseReport, getMonthlyReport } from '@/api/reports'
import {
  buildReportPeriods,
  currentRolloverPeriod,
  DEFAULT_REPORT_PERIOD_COUNT,
  formatPeriodLabel,
  REPORT_PERIOD_COUNT_OPTIONS,
  type ReportPeriodCount,
  rolloverPeriodRange,
  SELECTABLE_PERIOD_COUNT
} from '@/views/dashboard/dashboardPeriods'
import type { LedgerTxn, MonthlyReportEntry, RolloverExpenseReport } from '@/types/ledger'
import { formatDateRange } from '@/utils/dates'
import { useAccountScope } from '@/utils/useAccountScope'
import { useReloadOnViewMode } from '@/utils/useReloadOnViewMode'

/**
 * Everything the dashboard sections show: the selected report period, the per-period expense
 * reports, the asset balances, the recent expenses and the lifetime report. One owner keeps the
 * load order (accounts and currencies first, then the four requests) and the single reload on a
 * mode switch; the sections only receive what they render.
 */
export function useDashboardData() {
  const accountStore = useAccountStore()
  const authStore = useAuthStore()
  const modeStore = useModeStore()
  const householdStore = useHouseholdStore()

  const { balances, balancesLoading, sharedAccountIds, loadAccounts, loadBalances } =
    useAccountScope(() => visibleAssetAccounts.value.map((account) => account.id))
  const reports = ref<Array<{ period: string; report: RolloverExpenseReport }>>([])
  const reportLoading = ref(false)
  const recentExpenses = ref<LedgerTxn[]>([])
  const recentExpensesLoading = ref(false)
  const lifetimeReport = ref<MonthlyReportEntry[]>([])
  const lifetimeReportLoading = ref(false)
  const periodCount = useLocalStorage<ReportPeriodCount>(
    'pocketr-dashboard-period-count',
    DEFAULT_REPORT_PERIOD_COUNT,
    {
      serializer: {
        read: (raw) => {
          const count = Number(raw)
          return (
            REPORT_PERIOD_COUNT_OPTIONS.find((option) => option === count) ??
            DEFAULT_REPORT_PERIOD_COUNT
          )
        },
        write: (count) => String(count),
      },
    },
  )

  /** Rollover day of the active scope: the household's in household mode, otherwise the user's. */
  const rolloverDay = computed(() => {
    if (modeStore.isHousehold) {
      return householdStore.households?.find((h) => h.id === modeStore.householdId)?.rolloverDay
    }
    return authStore.user?.rolloverDay
  })

  const currentPeriod = computed(() => currentRolloverPeriod(new Date(), rolloverDay.value))
  const selectedPeriod = ref(currentPeriod.value)

  const periodItems = computed(() =>
    buildReportPeriods(currentPeriod.value, SELECTABLE_PERIOD_COUNT)
      .reverse()
      .map((period) => {
        const { start, end } = rolloverPeriodRange(period, rolloverDay.value)
        return {
          value: period,
          label: formatPeriodLabel(period),
          description: formatDateRange(start, end),
        }
      }),
  )

  const visibleAssetAccounts = computed(() => {
    const assets = accountStore.activeAccounts.filter((account) => account.type === 'ASSET')
    if (!modeStore.isHousehold) return assets
    return assets.filter((account) => sharedAccountIds.value.has(account.id))
  })

  const currentEntries = computed(
    () => reports.value[reports.value.length - 1]?.report.entries ?? [],
  )

  async function loadReports(): Promise<void> {
    reportLoading.value = true
    try {
      const periods = buildReportPeriods(selectedPeriod.value, periodCount.value)
      const loadedReports = await Promise.all(
        periods.map(async (period) => ({
          period,
          report: await getMonthlyReport({
            mode: modeStore.modeParam,
            householdId: modeStore.householdId ?? undefined,
            period,
          }),
        })),
      )
      reports.value = loadedReports
    } catch {
      reports.value = []
    } finally {
      reportLoading.value = false
    }
  }

  async function loadRecentExpenses(): Promise<void> {
    recentExpensesLoading.value = true
    try {
      const result = await listTxns({
        mode: modeStore.modeParam,
        householdId: modeStore.householdId ?? undefined,
        spendingOnly: true,
        page: 0,
        size: 10,
      })
      recentExpenses.value = result.content
    } catch {
      recentExpenses.value = []
    } finally {
      recentExpensesLoading.value = false
    }
  }

  async function loadLifetimeReport(): Promise<void> {
    lifetimeReportLoading.value = true
    try {
      lifetimeReport.value = await getLifetimeExpenseReport({
        mode: modeStore.modeParam,
        householdId: modeStore.householdId ?? undefined,
      })
    } catch {
      lifetimeReport.value = []
    } finally {
      lifetimeReportLoading.value = false
    }
  }

  async function loadAll(): Promise<void> {
    await loadAccounts()
    await Promise.all([loadBalances(), loadReports(), loadRecentExpenses(), loadLifetimeReport()])
  }

  function selectPeriod(period: string): void {
    selectedPeriod.value = period
    void loadReports()
  }

  function selectPeriodCount(count: ReportPeriodCount): void {
    periodCount.value = count
    void loadReports()
  }

  useReloadOnViewMode(loadAll, () => {
    // A mode's rollover day can differ; keep "current period" selected across the switch.
    if (!periodItems.value.some((item) => item.value === selectedPeriod.value)) {
      selectedPeriod.value = currentPeriod.value
    }
  })

  return {
    selectedPeriod,
    currentPeriod,
    periodItems,
    periodCount,
    visibleAssetAccounts,
    balances,
    balancesLoading,
    reports,
    reportLoading,
    currentEntries,
    recentExpenses,
    recentExpensesLoading,
    lifetimeReport,
    lifetimeReportLoading,
    selectPeriod,
    selectPeriodCount,
  }
}
