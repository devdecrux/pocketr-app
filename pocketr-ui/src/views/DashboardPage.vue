<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowUpDown, TrendingDown, Wallet } from 'lucide-vue-next'
import { use } from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { useAccountStore } from '@/stores/account'
import { useCurrencyStore } from '@/stores/currency'
import { useModeStore } from '@/stores/mode'
import { useHouseholdStore } from '@/stores/household'
import { getAccountBalances, listTxns } from '@/api/ledger'
import { getLifetimeExpenseReport, getMonthlyReport } from '@/api/reports'
import { formatMinor } from '@/utils/money'
import type { LedgerTxn, MonthlyReportEntry, RolloverExpenseReport } from '@/types/ledger'
import { AppCardHeader, AppPageHeader, AppStateMessage } from '@/components/app'
import { useI18n } from 'vue-i18n'

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent])

const DASHBOARD_CURRENCY = 'EUR'
const REPORT_PERIOD_COUNT = 6
const SPENDING_CHART_THEMES = {
  light: {
    color: ['#dc2626'],
    tooltip: {
      backgroundColor: '#ffffff',
      borderColor: 'rgba(220, 38, 38, 0.28)',
      textStyle: { color: '#111827' },
    },
    line: {
      symbolSize: 6,
      lineStyle: { width: 3 },
      areaStyle: { color: 'rgba(220, 38, 38, 0.16)' },
    },
    bar: {
      label: { color: '#4b5563' },
    },
    categoryAxis: {
      axisLine: { lineStyle: { color: '#d1d5db' } },
      axisLabel: { color: '#4b5563' },
    },
    valueAxis: {
      axisLabel: { color: '#4b5563' },
      splitLine: { lineStyle: { color: '#e5e7eb' } },
    },
  },
  dark: {
    color: ['#f87171'],
    tooltip: {
      backgroundColor: '#111827',
      borderColor: 'rgba(248, 113, 113, 0.35)',
      textStyle: { color: '#f9fafb' },
    },
    line: {
      symbolSize: 6,
      lineStyle: { width: 3 },
      areaStyle: { color: 'rgba(248, 113, 113, 0.24)' },
    },
    bar: {
      label: { color: '#cbd5e1' },
    },
    categoryAxis: {
      axisLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.36)' } },
      axisLabel: { color: '#cbd5e1' },
    },
    valueAxis: {
      axisLabel: { color: '#cbd5e1' },
      splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.16)' } },
    },
  },
} as const

const accountStore = useAccountStore()
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
const categoryChartView = ref<'rollover' | 'lifetime'>('rollover')
const isLifetimeCategoryView = computed({
  get: () => categoryChartView.value === 'lifetime',
  set: (value: boolean) => {
    categoryChartView.value = value ? 'lifetime' : 'rollover'
  },
})
const isDarkMode = ref(false)
let themeObserver: MutationObserver | null = null

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
  return reports.value.map(({ period, report }) => ({
    period,
    label: formatPeriodLabel(period),
    amountMinor: sumReportCurrency(report.entries, DASHBOARD_CURRENCY),
  }))
})

const hasSpendingChartData = computed(() => {
  return spendingChartPoints.value.some((point) => point.amountMinor > 0)
})

const spendingChartTheme = computed(() => {
  return isDarkMode.value ? SPENDING_CHART_THEMES.dark : SPENDING_CHART_THEMES.light
})

const spendingChartOptions = computed(() => {
  const minorUnit = currencyStore.getMinorUnit(DASHBOARD_CURRENCY)
  return {
    grid: { left: 8, right: 12, top: 20, bottom: 28, containLabel: true },
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value: number) =>
        formatMinor(Math.round(value * 10 ** minorUnit), DASHBOARD_CURRENCY, minorUnit),
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: spendingChartPoints.value.map((point) => point.label),
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: {
        formatter: (value: number) => formatCompactMajor(value, DASHBOARD_CURRENCY),
      },
    },
    series: [
      {
        name: DASHBOARD_CURRENCY,
        type: 'line',
        smooth: true,
        areaStyle: {},
        emphasis: { focus: 'series' },
        data: spendingChartPoints.value.map((point) => minorToMajor(point.amountMinor, minorUnit)),
      },
    ],
  }
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

const hasTopCategoryChartData = computed(() => {
  return topCategoryChartItems.value.length > 0
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

const topCategoryChartOptions = computed(() => {
  const chartCurrency = topCategoryChartCurrency.value
  const minorUnit = currencyStore.getMinorUnit(chartCurrency)
  const items = topCategoryChartItems.value
  return {
    grid: { left: 8, right: 40, top: 8, bottom: 12, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: (value: number) =>
        formatMinor(Math.round(value * 10 ** minorUnit), chartCurrency, minorUnit),
    },
    xAxis: {
      type: 'value',
      axisLabel: {
        formatter: (value: number) => formatCompactMajor(value, chartCurrency),
      },
    },
    yAxis: {
      type: 'category',
      inverse: true,
      axisTick: { show: false },
      data: items.map((item) => item.name),
    },
    series: [
      {
        type: 'bar',
        realtimeSort: true,
        barMaxWidth: 18,
        label: {
          show: true,
          position: 'right',
          formatter: ({ value }: { value: number }) =>
            formatMinor(Math.round(value * 10 ** minorUnit), chartCurrency, minorUnit),
        },
        animationDuration: 300,
        animationDurationUpdate: 600,
        animationEasing: 'linear',
        animationEasingUpdate: 'linear',
        data: items.map((item) => ({
          name: item.name,
          value: minorToMajor(item.amountMinor, minorUnit),
          itemStyle: { color: item.color },
        })),
      },
    ],
  } as unknown as EChartsOption
})

const reportPeriodLabel = computed(() => {
  if (!currentReport.value) return formatYearMonth(new Date())
  return `${formatIsoDate(currentReport.value.periodStart)} - ${formatIsoDate(currentReport.value.periodEnd)}`
})

const currentMonthLabel = computed(() => {
  const currentPeriod =
    reports.value[reports.value.length - 1]?.period ?? formatYearMonth(new Date())
  return formatPeriodMonthName(currentPeriod)
})

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
    const periods = buildReportPeriods(new Date())
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

function syncDarkMode(): void {
  isDarkMode.value = document.documentElement.classList.contains('dark')
}

onMounted(() => {
  loadAll()
  syncDarkMode()
  themeObserver = new MutationObserver(syncDarkMode)
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
})

onBeforeUnmount(() => {
  themeObserver?.disconnect()
  themeObserver = null
})

watch(() => modeStore.viewMode, loadAll, { deep: true })

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

function formatTxnAmount(txn: LedgerTxn): string {
  const targetType = txn.txnKind === 'DEBT_PAYMENT' ? 'LIABILITY' : 'EXPENSE'
  const targetSplits = txn.splits.filter(
    (split) => split.accountType === targetType && split.side === 'DEBIT',
  )
  const splits =
    targetSplits.length > 0 ? targetSplits : txn.splits.filter((split) => split.side === 'DEBIT')
  const amountMinor = splits.reduce((sum, split) => sum + split.amountMinor, 0)
  return formatMoney(amountMinor, txn.currency)
}

function txnCategoryLabel(txn: LedgerTxn): string {
  if (txn.txnKind === 'DEBT_PAYMENT') return t('views.dashboard.debtPayment')
  const category = txn.splits.find(
    (split) => split.accountType === 'EXPENSE' && split.side === 'DEBIT',
  )
  return category?.categoryTagName ?? t('views.dashboard.uncategorized')
}

function resolveCategoryColor(categoryName: string | null, categoryColor: string | null): string {
  if (categoryColor && /^#[0-9a-f]{6}$/i.test(categoryColor)) {
    return categoryColor
  }
  if (categoryName === t('views.dashboard.debtPayment')) {
    return '#ef4444'
  }
  return '#64748b'
}

function buildReportPeriods(currentDate: Date): string[] {
  return Array.from({ length: REPORT_PERIOD_COUNT }, (_, index) => {
    const offset = REPORT_PERIOD_COUNT - index - 1
    return formatYearMonth(new Date(currentDate.getFullYear(), currentDate.getMonth() - offset, 1))
  })
}

function formatYearMonth(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

function formatPeriodLabel(period: string): string {
  const [year = new Date().getFullYear(), month = 1] = period.split('-').map(Number)
  return new Intl.DateTimeFormat(undefined, { month: 'short' }).format(new Date(year, month - 1, 1))
}

function formatPeriodMonthName(period: string): string {
  const [year = new Date().getFullYear(), month = 1] = period.split('-').map(Number)
  return new Intl.DateTimeFormat(undefined, { month: 'long' }).format(new Date(year, month - 1, 1))
}

function formatIsoDate(value: string): string {
  const [year, month, day] = value.split('-')
  return `${day}-${month}-${year}`
}

function minorToMajor(amountMinor: number, minorUnit: number): number {
  return amountMinor / 10 ** minorUnit
}

function formatCompactMajor(amount: number, currency: string): string {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(amount)
}
</script>

<template>
  <section class="grid gap-4">
    <AppPageHeader
      :title="$t('views.dashboard.title')"
      :subtitle="
        modeStore.isHousehold
          ? $t('views.dashboard.subtitle.household')
          : $t('views.dashboard.subtitle.individual')
      "
    />

    <div class="grid gap-3 md:grid-cols-2">
      <Card class="gap-2 rounded-md py-3">
        <AppCardHeader
          :title="$t('views.dashboard.cards.availableMoney')"
          class="justify-start gap-2 px-4 py-0"
          title-class="text-sm"
        >
          <template #leading>
            <Wallet class="size-4 text-muted-foreground" />
          </template>
        </AppCardHeader>
        <CardContent class="px-4">
          <div v-if="accountStore.isLoading || balancesLoading" class="space-y-2">
            <Skeleton class="h-6 w-32" />
            <Skeleton class="h-5 w-44" />
          </div>
          <AppStateMessage v-else-if="visibleAssetAccounts.length === 0">
            {{ $t('views.dashboard.empty.accounts') }}
          </AppStateMessage>
          <div v-else class="space-y-2">
            <p
              class="text-xl font-semibold leading-7 text-[var(--app-transaction-amount-positive-fg)]"
            >
              {{ formatMoney(primaryAmount(availableByCurrency), DASHBOARD_CURRENCY) }}
            </p>
            <div v-if="secondaryAmounts(availableByCurrency).length" class="flex flex-wrap gap-1.5">
              <Badge
                v-for="amount in secondaryAmounts(availableByCurrency)"
                :key="amount.currency"
                variant="outline"
                class="h-5 px-1.5 text-[11px]"
              >
                {{ formatMoney(amount.amountMinor, amount.currency) }}
              </Badge>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card class="gap-2 rounded-md py-3">
        <AppCardHeader
          :title="$t('views.dashboard.cards.spendingsForMonth', { month: currentMonthLabel })"
          class="justify-start gap-2 px-4 py-0"
          title-class="text-sm"
        >
          <template #leading>
            <TrendingDown class="size-4 text-muted-foreground" />
          </template>
        </AppCardHeader>
        <CardContent class="px-4">
          <div v-if="reportLoading" class="space-y-2">
            <Skeleton class="h-6 w-32" />
            <Skeleton class="h-5 w-44" />
          </div>
          <div v-else class="space-y-2">
            <p
              class="text-xl font-semibold leading-7 text-[var(--app-transaction-amount-negative-fg)]"
            >
              {{ formatMoney(primaryAmount(currentSpendingByCurrency), DASHBOARD_CURRENCY) }}
            </p>
            <div class="flex flex-wrap items-center gap-1.5">
              <Badge variant="secondary" class="h-5 px-1.5 text-[11px]">{{
                reportPeriodLabel
              }}</Badge>
              <Badge
                v-for="amount in secondaryAmounts(currentSpendingByCurrency)"
                :key="amount.currency"
                variant="outline"
                class="h-5 px-1.5 text-[11px]"
              >
                {{ formatMoney(amount.amountMinor, amount.currency) }}
              </Badge>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>

    <Card>
      <AppCardHeader :title="$t('views.dashboard.cards.spendingTrend')" class="justify-start gap-2">
        <template #leading>
          <TrendingDown class="size-5 text-muted-foreground" />
        </template>
      </AppCardHeader>
      <CardContent>
        <div v-if="reportLoading" class="space-y-3">
          <Skeleton class="h-64 w-full" />
        </div>
        <AppStateMessage v-else-if="!hasSpendingChartData">
          {{ $t('views.dashboard.empty.monthlySpending') }}
        </AppStateMessage>
        <div v-else class="h-64 min-h-64 w-full">
          <VChart
            class="w-full"
            :style="{ height: '16rem', minHeight: '16rem' }"
            :theme="spendingChartTheme"
            :option="spendingChartOptions"
            :autoresize="{ throttle: 50 }"
          />
        </div>
      </CardContent>
    </Card>

    <div class="grid gap-4 lg:grid-cols-2">
      <section class="space-y-2">
        <div class="flex items-center gap-2">
          <ArrowUpDown class="size-4 text-muted-foreground" />
          <h2 class="text-base font-semibold">{{ $t('views.dashboard.cards.recentExpenses') }}</h2>
        </div>
        <div>
          <div v-if="recentExpensesLoading" class="space-y-1.5">
            <Skeleton class="h-8 w-full" />
            <Skeleton class="h-8 w-full" />
            <Skeleton class="h-8 w-full" />
          </div>
          <AppStateMessage v-else-if="recentExpenses.length === 0">
            {{ $t('views.dashboard.empty.transactions') }}
          </AppStateMessage>
          <ul v-else class="space-y-1.5">
            <li v-for="txn in recentExpenses" :key="txn.id">
              <Card class="gap-0 rounded-md py-0 shadow-none">
                <CardContent class="px-2.5 py-2">
                  <div class="flex items-center justify-between gap-2">
                    <div class="min-w-0">
                      <div class="flex min-w-0 items-center gap-1.5">
                        <span class="truncate text-xs font-medium leading-5">{{
                          txn.description
                        }}</span>
                        <Badge variant="outline" class="h-5 shrink-0 px-1.5 text-[11px]">
                          {{ txnCategoryLabel(txn) }}
                        </Badge>
                      </div>
                      <span class="text-[11px] leading-4 text-muted-foreground">{{
                        txn.txnDate
                      }}</span>
                    </div>
                    <span class="shrink-0 text-xs font-mono">{{ formatTxnAmount(txn) }}</span>
                  </div>
                </CardContent>
              </Card>
            </li>
          </ul>
        </div>
      </section>

      <section class="space-y-2">
        <div class="flex flex-wrap items-center gap-3">
          <TrendingDown class="size-4 text-muted-foreground" />
          <h2 class="text-base font-semibold">
            {{ $t('views.dashboard.cards.topSpendingCategories') }}
          </h2>
          <label class="flex items-center gap-2 text-xs text-muted-foreground">
            <span>{{ $t('views.dashboard.periodViews.lifetime') }}</span>
            <Switch v-model="isLifetimeCategoryView" />
          </label>
        </div>
        <div>
          <div v-if="categoryChartLoading" class="space-y-1.5">
            <Skeleton class="h-64 w-full" />
          </div>
          <AppStateMessage v-else-if="!hasTopCategoryChartData">
            {{ $t('views.dashboard.empty.monthlySpending') }}
          </AppStateMessage>
          <div v-else class="h-64 min-h-64 w-full">
            <VChart
              class="w-full"
              :style="{ height: '16rem', minHeight: '16rem' }"
              :theme="spendingChartTheme"
              :option="topCategoryChartOptions"
              :autoresize="{ throttle: 50 }"
            />
          </div>
        </div>
      </section>
    </div>
  </section>
</template>
