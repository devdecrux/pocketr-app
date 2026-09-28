<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocalStorage } from '@vueuse/core'
import AppPagePanel from '@/components/layout/AppPagePanel.vue'
import { useAccountStore } from '@/stores/account'
import { useAuthStore } from '@/stores/auth'
import { useCurrencyStore } from '@/stores/currency'
import { useHouseholdStore } from '@/stores/household'
import { useModeStore } from '@/stores/mode'
import { getAccountBalances, listTxns } from '@/api/ledger'
import { getLifetimeExpenseReport, getMonthlyReport } from '@/api/reports'
import { formatMinor } from '@/utils/money'
import { resolveSpendingAccountName } from '@/utils/txnDisplay'
import {
  buildReportPeriods,
  currentRolloverPeriod,
  daysAgo,
  DEFAULT_REPORT_PERIOD_COUNT,
  formatDayMonthYear,
  formatPeriodAxisLabel,
  formatPeriodLabel,
  REPORT_PERIOD_COUNT_OPTIONS,
  type ReportPeriodCount,
  rolloverPeriodRange,
  SELECTABLE_PERIOD_COUNT,
} from '@/utils/dashboardPeriods'
import type { LedgerTxn, MonthlyReportEntry, RolloverExpenseReport } from '@/types/ledger'

// ECharts is page-only code: loaded with the chart, never by the app shell.
const SpendingTrendChart = defineAsyncComponent(
  () => import('@/components/charts/SpendingTrendChart.vue'),
)

const DASHBOARD_CURRENCY = 'EUR'

const accountStore = useAccountStore()
const authStore = useAuthStore()
const currencyStore = useCurrencyStore()
const modeStore = useModeStore()
const householdStore = useHouseholdStore()
const { t } = useI18n()

const balances = ref<Map<string, number>>(new Map())
const balancesLoading = ref(false)
const reports = ref<Array<{ period: string; report: RolloverExpenseReport }>>([])
const reportLoading = ref(false)
const recentExpenses = ref<LedgerTxn[]>([])
const recentExpensesLoading = ref(false)
const lifetimeReport = ref<MonthlyReportEntry[]>([])
const lifetimeReportLoading = ref(false)
const categoryChartView = useLocalStorage<'rollover' | 'lifetime'>(
  'pocketr-dashboard-category-view',
  'rollover',
)
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
        description: `${formatDayMonthYear(start)} - ${formatDayMonthYear(end)}`,
      }
    }),
)

const periodCountItems = computed(() =>
  REPORT_PERIOD_COUNT_OPTIONS.map((count) => ({
    value: count,
    label: t('views.dashboard.overview.lastMonths', { count }),
  })),
)

const sharedAccountIds = computed(
  () => new Set(householdStore.sharedAccounts.map((share) => share.accountId)),
)

const visibleAssetAccounts = computed(() => {
  const assets = accountStore.activeAccounts.filter((account) => account.type === 'ASSET')
  if (!modeStore.isHousehold) return assets
  return assets.filter((account) => sharedAccountIds.value.has(account.id))
})

const currentReport = computed(() => reports.value[reports.value.length - 1]?.report ?? null)
const currentEntries = computed(() => currentReport.value?.entries ?? [])

const availableByCurrency = computed(() => {
  const totals = new Map<string, number>()
  for (const account of visibleAssetAccounts.value) {
    const balance = balances.value.get(account.id)
    if (balance === undefined) continue
    totals.set(account.currency, (totals.get(account.currency) ?? 0) + balance)
  }
  return toCurrencyAmounts(totals)
})

const currentSpendingByCurrency = computed(() => sumEntriesByCurrency(currentEntries.value))

const spendingChartPoints = computed(() => {
  const minorUnit = currencyStore.getMinorUnit(DASHBOARD_CURRENCY)
  return reports.value.map(({ period, report }) => {
    const amountMinor = sumReportCurrency(report.entries, DASHBOARD_CURRENCY)
    return {
      label: formatPeriodAxisLabel(period),
      amountMinor,
      value: amountMinor / 10 ** minorUnit,
    }
  })
})

const hasSpendingChartData = computed(() => {
  return spendingChartPoints.value.some((point) => point.amountMinor > 0)
})

const topCategoryChartEntries = computed(() => {
  return categoryChartView.value === 'lifetime' ? lifetimeReport.value : currentEntries.value
})

const topCategoryChartCurrency = computed(() => {
  const currencies = new Set(
    topCategoryChartEntries.value
      .filter((entry) => entry.netMinor > 0)
      .map((entry) => entry.currency),
  )
  if (currencies.has(DASHBOARD_CURRENCY)) return DASHBOARD_CURRENCY
  return [...currencies].sort()[0] ?? DASHBOARD_CURRENCY
})

const categoryChartLoading = computed(() => {
  return categoryChartView.value === 'lifetime' ? lifetimeReportLoading.value : reportLoading.value
})

const topCategoryChartItems = computed(() => {
  const chartCurrency = topCategoryChartCurrency.value
  const categories = new Map<string, { name: string; amountMinor: number; color: string }>()
  for (const entry of topCategoryChartEntries.value) {
    if (entry.currency !== chartCurrency) continue

    const name = entry.categoryTagName ?? t('views.dashboard.uncategorized')
    const key = entry.categoryTagId ?? name
    const existing = categories.get(key)
    const amountMinor = (existing?.amountMinor ?? 0) + entry.netMinor
    categories.set(key, {
      name,
      amountMinor,
      color: resolveCategoryColor(entry.categoryTagName, entry.categoryTagColor),
    })
  }

  return [...categories.values()]
    .filter((category) => category.amountMinor > 0)
    .sort((a, b) => b.amountMinor - a.amountMinor)
    .slice(0, 5)
})

const topCategoryMax = computed(() => topCategoryChartItems.value[0]?.amountMinor ?? 0)

const spentLabel = computed(() =>
  selectedPeriod.value === currentPeriod.value
    ? t('views.dashboard.overview.spentThisMonth')
    : t('views.dashboard.overview.spentInPeriod', {
        period: formatPeriodLabel(selectedPeriod.value),
      }),
)

async function loadBalances(): Promise<void> {
  balancesLoading.value = true
  balances.value = new Map()
  try {
    const accountIds = visibleAssetAccounts.value.map((account) => account.id)
    const result = await getAccountBalances(
      accountIds,
      undefined,
      modeStore.householdId ?? undefined,
    )
    for (const balance of result) {
      balances.value.set(balance.accountId, balance.balanceMinor)
    }
  } catch {
    balances.value = new Map()
  } finally {
    balancesLoading.value = false
  }
}

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
  await Promise.all([accountStore.load(), currencyStore.load()])
  if (modeStore.isHousehold && modeStore.householdId) {
    await householdStore.loadSharedAccounts(modeStore.householdId)
  }
  await Promise.all([loadBalances(), loadReports(), loadRecentExpenses(), loadLifetimeReport()])
}

function onSelectPeriod(period: string): void {
  selectedPeriod.value = period
  void loadReports()
}

function onSelectPeriodCount(count: ReportPeriodCount): void {
  periodCount.value = count
  void loadReports()
}

onMounted(loadAll)

watch(
  () => modeStore.viewMode,
  () => {
    // A mode's rollover day can differ; keep "current period" selected across the switch.
    if (!periodItems.value.some((item) => item.value === selectedPeriod.value)) {
      selectedPeriod.value = currentPeriod.value
    }
    void loadAll()
  },
  { deep: true },
)

function sumEntriesByCurrency(
  entries: MonthlyReportEntry[],
): Array<{ currency: string; amountMinor: number }> {
  const totals = new Map<string, number>()
  for (const entry of entries) {
    totals.set(entry.currency, (totals.get(entry.currency) ?? 0) + entry.netMinor)
  }
  return toCurrencyAmounts(totals)
}

function toCurrencyAmounts(
  totals: Map<string, number>,
): Array<{ currency: string; amountMinor: number }> {
  return [...totals.entries()]
    .map(([currency, amountMinor]) => ({ currency, amountMinor }))
    .sort((a, b) =>
      a.currency === DASHBOARD_CURRENCY
        ? -1
        : b.currency === DASHBOARD_CURRENCY
          ? 1
          : a.currency.localeCompare(b.currency),
    )
}

function sumReportCurrency(entries: MonthlyReportEntry[], currency: string): number {
  return entries
    .filter((entry) => entry.currency === currency)
    .reduce((sum, entry) => sum + entry.netMinor, 0)
}

function primaryAmount(amounts: Array<{ currency: string; amountMinor: number }>): number {
  return amounts.find((amount) => amount.currency === DASHBOARD_CURRENCY)?.amountMinor ?? 0
}

function secondaryAmounts(
  amounts: Array<{ currency: string; amountMinor: number }>,
): Array<{ currency: string; amountMinor: number }> {
  return amounts.filter((amount) => amount.currency !== DASHBOARD_CURRENCY)
}

function formatMoney(amountMinor: number, currency: string): string {
  return formatMinor(amountMinor, currency, currencyStore.getMinorUnit(currency))
}

/** Category totals drop trailing zero cents (e.g. "€650"), keeping real cents ("€285.50"). */
function formatCategoryAmount(amountMinor: number, currency: string): string {
  const minorUnit = currencyStore.getMinorUnit(currency)
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
    minimumFractionDigits: amountMinor % 10 ** minorUnit === 0 ? 0 : minorUnit,
    maximumFractionDigits: minorUnit,
  }).format(amountMinor / 10 ** minorUnit)
}

function formatChartValue(value: number): string {
  const minorUnit = currencyStore.getMinorUnit(DASHBOARD_CURRENCY)
  return formatMinor(Math.round(value * 10 ** minorUnit), DASHBOARD_CURRENCY, minorUnit)
}

function formatChartAxis(value: number): string {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: DASHBOARD_CURRENCY,
    maximumFractionDigits: 0,
  }).format(value)
}

function formatTxnAmount(txn: LedgerTxn): string {
  const targetType = txn.txnKind === 'DEBT_PAYMENT' ? 'LIABILITY' : 'EXPENSE'
  const targetSplits = txn.splits.filter(
    (split) => split.accountType === targetType && split.side === 'DEBIT',
  )
  const splits =
    targetSplits.length > 0 ? targetSplits : txn.splits.filter((split) => split.side === 'DEBIT')
  const amountMinor = splits.reduce((sum, split) => sum + split.amountMinor, 0)
  return `−${formatMoney(amountMinor, txn.currency)}`
}

/** Expense (or, for debt payments, liability) account name; the kind label when no name exists. */
function txnAccountLabel(txn: LedgerTxn): string {
  return (
    resolveSpendingAccountName(txn, (accountId) => accountStore.accountMap.get(accountId)) ??
    txnCategoryLabel(txn)
  )
}

function txnCategoryLabel(txn: LedgerTxn): string {
  if (txn.txnKind === 'DEBT_PAYMENT') return t('views.dashboard.debtPayment')
  const category = txn.splits.find(
    (split) => split.accountType === 'EXPENSE' && split.side === 'DEBIT',
  )
  return category?.categoryTagName ?? t('views.dashboard.uncategorized')
}

function txnDateLabel(txn: LedgerTxn): string {
  const age = daysAgo(txn.txnDate, new Date())
  if (age === 0) return t('views.dashboard.overview.today')
  if (age === 1) return t('views.dashboard.overview.yesterday')
  const [year = 0, month = 1, day = 1] = txn.txnDate.split('-').map(Number)
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(
    new Date(year, month - 1, day),
  )
}

function resolveCategoryColor(categoryName: string | null, categoryColor: string | null): string {
  if (categoryColor && /^#[0-9a-f]{6}$/i.test(categoryColor)) {
    return categoryColor
  }
  if (categoryName === t('views.dashboard.debtPayment')) {
    return '#ef4444'
  }
  return 'var(--ui-primary)'
}

const selectUi = {
  base: 'rounded-lg bg-default text-[13px] ring-default',
  trailingIcon: 'size-4 text-default',
}
</script>

<template>
  <AppPagePanel :title="$t('views.dashboard.title')">
    <template #context>
      <USelect
        :model-value="selectedPeriod"
        :items="periodItems"
        icon="i-lucide-calendar"
        :aria-label="$t('views.dashboard.overview.selectPeriod')"
        color="neutral"
        size="lg"
        :ui="{
          base: 'rounded-lg bg-default text-sm ring-default',
          leadingIcon: 'text-default',
          trailingIcon: 'size-4 text-default',
          itemDescription: 'text-xs',
        }"
        @update:model-value="onSelectPeriod"
      >
        <template #default>
          <span class="hidden truncate lg:inline">{{ formatPeriodLabel(selectedPeriod) }}</span>
          <span class="truncate lg:hidden">{{ formatPeriodLabel(selectedPeriod, 'short') }}</span>
        </template>
      </USelect>
    </template>

    <div class="flex items-start justify-between gap-4 lg:pb-1">
      <div class="min-w-0">
        <h1 class="text-[22px] font-bold leading-8 text-highlighted lg:hidden">
          {{ $t('views.dashboard.title') }}
        </h1>
        <h2 class="hidden text-2xl font-bold leading-8 text-highlighted lg:block">
          {{ $t('views.dashboard.overview.heading') }}
        </h2>
        <p class="text-base text-muted lg:hidden">
          {{
            modeStore.isHousehold
              ? $t('views.dashboard.subtitle.household')
              : $t('views.dashboard.overview.heading')
          }}
        </p>
        <p class="hidden text-base text-muted lg:block">
          {{
            modeStore.isHousehold
              ? $t('views.dashboard.subtitle.household')
              : $t('views.dashboard.overview.subtitle')
          }}
        </p>
      </div>
      <UButton
        to="/transactions"
        icon="i-lucide-plus"
        :label="$t('views.dashboard.overview.addTransaction')"
        size="xl"
        class="hidden h-10 shrink-0 rounded-lg px-4 text-sm lg:inline-flex"
      />
    </div>

    <!-- Desktop: the two summary cards stack in column 1; the chart spans columns 2-5 and both rows. -->
    <div class="grid gap-3 lg:grid-cols-5 lg:grid-rows-2 lg:gap-4">
      <UCard
        class="lg:col-start-1 lg:row-start-1"
        :ui="{
          root: 'rounded-xl',
          body: 'flex items-center gap-4 p-3 sm:p-4 lg:grid lg:h-full lg:grid-cols-[minmax(0,1fr)_auto] lg:content-center lg:items-center lg:gap-x-1.5 lg:gap-y-1 lg:p-3',
        }"
      >
        <span
          class="flex size-[60px] shrink-0 items-center justify-center rounded-full bg-success/10 lg:col-start-2 lg:row-start-1 lg:size-9"
        >
          <UIcon name="i-lucide-wallet" class="size-7 text-(--pocketr-success-fg) lg:size-5" />
        </span>
        <div class="min-w-0 space-y-0.5 lg:contents">
          <p class="text-sm text-muted lg:col-start-1 lg:row-start-1 lg:truncate lg:text-[13px]">
            {{ $t('views.dashboard.overview.availableBalance') }}
          </p>
          <template v-if="accountStore.isLoading || balancesLoading">
            <USkeleton class="h-9 w-40 lg:col-span-2 lg:h-8 lg:w-full" />
            <USkeleton class="h-4 w-28 lg:col-span-2" />
          </template>
          <p
            v-else-if="visibleAssetAccounts.length === 0"
            class="py-1 text-sm text-muted lg:col-span-2"
          >
            {{ $t('views.dashboard.empty.accounts') }}
          </p>
          <template v-else>
            <p
              class="text-[28px] font-bold leading-9 tabular-nums text-highlighted lg:col-span-2 lg:text-[22px] lg:leading-8 lg:[overflow-wrap:anywhere]"
            >
              {{ formatMoney(primaryAmount(availableByCurrency), DASHBOARD_CURRENCY) }}
            </p>
            <p class="text-sm text-muted lg:col-span-2 lg:truncate">
              {{ $t('views.dashboard.overview.acrossAccounts', visibleAssetAccounts.length) }}
            </p>
            <div
              v-if="secondaryAmounts(availableByCurrency).length"
              class="flex flex-wrap gap-1.5 pt-1 lg:col-span-2"
            >
              <UBadge
                v-for="amount in secondaryAmounts(availableByCurrency)"
                :key="amount.currency"
                color="neutral"
                variant="outline"
                size="sm"
              >
                {{ formatMoney(amount.amountMinor, amount.currency) }}
              </UBadge>
            </div>
          </template>
        </div>
      </UCard>

      <UCard
        class="lg:col-start-1 lg:row-start-2"
        :ui="{
          root: 'rounded-xl',
          body: 'flex items-center gap-4 p-3 sm:p-4 lg:grid lg:h-full lg:grid-cols-[minmax(0,1fr)_auto] lg:content-center lg:items-center lg:gap-x-1.5 lg:gap-y-1 lg:p-3',
        }"
      >
        <span
          class="flex size-[60px] shrink-0 items-center justify-center rounded-full bg-error/10 lg:col-start-2 lg:row-start-1 lg:size-9"
        >
          <UIcon name="i-lucide-arrow-up-right" class="size-7 text-error lg:size-5" />
        </span>
        <div class="min-w-0 space-y-0.5 lg:contents">
          <p class="text-sm text-muted lg:col-start-1 lg:row-start-1 lg:truncate lg:text-[13px]">
            {{ spentLabel }}
          </p>
          <template v-if="reportLoading">
            <USkeleton class="h-9 w-40 lg:col-span-2 lg:h-8 lg:w-full" />
            <USkeleton class="h-4 w-28 lg:col-span-2" />
          </template>
          <p
            v-else-if="currentSpendingByCurrency.length === 0"
            class="py-1 text-sm text-muted lg:col-span-2"
          >
            {{
              reports.length === 0
                ? $t('views.dashboard.empty.spendingData')
                : $t('views.dashboard.empty.spendings')
            }}
          </p>
          <template v-else>
            <p
              class="text-[28px] font-bold leading-9 tabular-nums text-highlighted lg:col-span-2 lg:text-[22px] lg:leading-8 lg:[overflow-wrap:anywhere]"
            >
              {{ formatMoney(primaryAmount(currentSpendingByCurrency), DASHBOARD_CURRENCY) }}
            </p>
            <p class="text-sm text-muted lg:col-span-2 lg:truncate">
              {{ formatPeriodLabel(selectedPeriod) }}
            </p>
            <div
              v-if="secondaryAmounts(currentSpendingByCurrency).length"
              class="flex flex-wrap gap-1.5 pt-1 lg:col-span-2"
            >
              <UBadge
                v-for="amount in secondaryAmounts(currentSpendingByCurrency)"
                :key="amount.currency"
                color="neutral"
                variant="outline"
                size="sm"
              >
                {{ formatMoney(amount.amountMinor, amount.currency) }}
              </UBadge>
            </div>
          </template>
        </div>
      </UCard>

      <UCard
        class="mt-1 lg:col-span-4 lg:col-start-2 lg:row-span-2 lg:row-start-1 lg:mt-0"
        :ui="{ root: 'rounded-xl', body: 'p-3 sm:p-4 lg:px-4 lg:pb-4' }"
      >
        <div class="mb-2 flex items-center justify-between gap-3">
          <h2 class="flex min-w-0 items-center gap-1.5 text-[15px] font-semibold text-highlighted">
            <UIcon name="i-lucide-chart-line" class="size-4 shrink-0 text-primary" />
            <span class="truncate">{{ $t('views.dashboard.overview.spendingOverTime') }}</span>
          </h2>
          <USelect
            :model-value="periodCount"
            :items="periodCountItems"
            :aria-label="$t('views.dashboard.overview.chartPeriod')"
            color="neutral"
            size="sm"
            :ui="selectUi"
            @update:model-value="onSelectPeriodCount"
          />
        </div>
        <USkeleton v-if="reportLoading" class="h-[164px] w-full lg:h-[200px]" />
        <p v-else-if="!hasSpendingChartData" class="py-10 text-center text-sm text-muted">
          {{ $t('views.dashboard.empty.monthlySpending') }}
        </p>
        <div v-else class="h-[164px] w-full lg:h-[200px]">
          <SpendingTrendChart
            :labels="spendingChartPoints.map((point) => point.label)"
            :values="spendingChartPoints.map((point) => point.value)"
            :format-value="formatChartValue"
            :format-axis="formatChartAxis"
          />
        </div>
      </UCard>
    </div>

    <!-- Desktop: same 5-column grid as the row above. Recent expenses sits under Available balance,
         Top categories under Spent this month; columns 3-5 stay empty for now. -->
    <div class="grid gap-4 lg:grid-cols-5">
      <UCard :ui="{ root: 'rounded-xl', body: 'p-3 sm:p-4 lg:p-4' }">
        <!-- Row-2 headers: title (truncates at 1/5 width) left, action right, one fixed desktop height. -->
        <div class="flex items-center justify-between gap-3 pb-3 lg:mb-3 lg:h-7 lg:gap-2 lg:pb-0">
          <h2 class="flex min-w-0 items-center gap-1.5 text-[15px] font-semibold text-highlighted">
            <UIcon name="i-lucide-receipt" class="size-4 shrink-0 text-primary" />
            <span class="truncate">{{ $t('views.dashboard.overview.recentExpenses') }}</span>
          </h2>
          <UTooltip :text="$t('views.dashboard.overview.viewAll')">
            <ULink
              to="/transactions"
              class="inline-flex shrink-0 items-center text-sm whitespace-nowrap text-primary hover:underline"
            >
              <UIcon name="i-lucide-arrow-right" class="hidden size-4 lg:max-2xl:inline-block" />
              <span class="lg:max-2xl:sr-only">{{ $t('views.dashboard.overview.viewAll') }}</span>
            </ULink>
          </UTooltip>
        </div>
        <div v-if="recentExpensesLoading" class="space-y-3">
          <USkeleton v-for="index in 3" :key="index" class="h-12 w-full" />
        </div>
        <p v-else-if="recentExpenses.length === 0" class="py-6 text-center text-sm text-muted">
          {{ $t('views.dashboard.empty.transactions') }}
        </p>
        <ul v-else class="divide-y divide-default border-t border-default">
          <li
            v-for="txn in recentExpenses"
            :key="txn.id"
            class="flex items-center gap-4 py-2 lg:grid lg:grid-cols-[minmax(0,1fr)_auto] lg:gap-x-2 lg:gap-y-0.5"
          >
            <span
              class="flex size-11 shrink-0 items-center justify-center rounded-lg bg-(--pocketr-tile-bg) lg:hidden"
            >
              <UIcon name="i-lucide-receipt" class="size-5 text-highlighted dark:text-primary" />
            </span>
            <!-- Desktop (1/5 width): description on its own line, then category · date and amount. -->
            <div class="min-w-0 flex-1 lg:contents">
              <p class="truncate text-sm font-medium text-highlighted lg:col-span-2">
                {{ txn.description }}
              </p>
              <p class="truncate text-[13px] text-muted">
                {{ txnAccountLabel(txn) }} · {{ txnDateLabel(txn) }}
              </p>
            </div>
            <span
              class="shrink-0 text-right text-[15px] font-medium whitespace-nowrap tabular-nums text-error lg:self-center lg:text-sm"
            >
              {{ formatTxnAmount(txn) }}
            </span>
          </li>
        </ul>
      </UCard>

      <UCard :ui="{ root: 'rounded-xl', body: 'p-3 sm:p-4 lg:p-4' }">
        <div class="mb-3 flex items-center justify-between gap-3 lg:mb-3 lg:h-7 lg:gap-2 lg:pb-0">
          <h2 class="flex min-w-0 items-center gap-1.5 text-[15px] font-semibold text-highlighted">
            <UIcon name="i-lucide-shapes" class="size-4 shrink-0 text-primary" />
            <span class="truncate">{{ $t('views.dashboard.overview.topCategories') }}</span>
          </h2>
          <!-- Off = current rollover period, on = lifetime (as the legacy dashboard switch). -->
          <UTooltip :text="$t('views.dashboard.periodViews.lifetime')">
            <USwitch
              v-model="categoryChartView"
              true-value="lifetime"
              false-value="rollover"
              :label="$t('views.dashboard.periodViews.lifetime')"
              size="sm"
              class="shrink-0"
              :ui="{
                root: 'flex-row-reverse items-center gap-2',
                base: 'data-[state=unchecked]:bg-(--ui-text-muted)',
                wrapper: 'ms-0',
                label: 'text-xs font-normal whitespace-nowrap text-muted lg:max-2xl:sr-only',
              }"
            />
          </UTooltip>
        </div>
        <div v-if="categoryChartLoading" class="space-y-4">
          <USkeleton v-for="index in 4" :key="index" class="h-8 w-full" />
        </div>
        <p
          v-else-if="topCategoryChartItems.length === 0"
          class="py-10 text-center text-sm text-muted lg:py-6"
        >
          {{ $t('views.dashboard.empty.monthlySpending') }}
        </p>
        <!-- One shared grid (rows use subgrid): the amount column is as wide as the widest amount,
             so every bar track starts and ends at the same x and amounts right-align. -->
        <ul v-else class="grid grid-cols-[minmax(0,1fr)_auto] gap-x-4 gap-y-3.5 lg:gap-x-2">
          <li
            v-for="item in topCategoryChartItems"
            :key="item.name"
            class="col-span-2 grid grid-cols-subgrid items-end"
          >
            <span class="truncate text-sm text-highlighted">{{ item.name }}</span>
            <span
              class="row-span-2 self-center text-right text-sm whitespace-nowrap tabular-nums text-highlighted"
            >
              {{ formatCategoryAmount(item.amountMinor, topCategoryChartCurrency) }}
            </span>
            <span class="mt-1.5 block h-2 overflow-hidden rounded-full bg-(--pocketr-track)">
              <span
                class="block h-full rounded-full"
                :style="{
                  width: `${topCategoryMax ? (item.amountMinor / topCategoryMax) * 100 : 0}%`,
                  backgroundColor: item.color,
                }"
              />
            </span>
          </li>
        </ul>
      </UCard>
    </div>

    <template #footer>
      <UButton
        to="/transactions"
        icon="i-lucide-plus"
        :label="$t('views.dashboard.overview.addTransaction')"
        size="xl"
        block
        class="h-10 rounded-lg text-[15px]"
      />
    </template>
  </AppPagePanel>
</template>
