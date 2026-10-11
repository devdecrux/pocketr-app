<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import AppSelect from '@/components/fields/AppSelect.vue'
import DashboardCardHeader from '@/views/dashboard/DashboardCardHeader.vue'
import {
  formatPeriodAxisLabel,
  REPORT_PERIOD_COUNT_OPTIONS,
  type ReportPeriodCount,
} from '@/views/dashboard/dashboardPeriods'
import { useCurrencyStore } from '@/stores/currency'
import type { RolloverExpenseReport } from '@/types/ledger'
import { formatMinor, PRIMARY_CURRENCY } from '@/utils/money'

// ECharts is page-only code: loaded with the chart, never by the app shell.
const SpendingTrendChart = defineAsyncComponent(
  () => import('@/views/dashboard/SpendingTrendChart.vue'),
)

/** "Spending over time": the primary-currency total of each period as a line chart. */
const props = defineProps<{
  reports: Array<{ period: string; report: RolloverExpenseReport }>
  loading: boolean
  periodCount: ReportPeriodCount
}>()

const emit = defineEmits<{ 'update:periodCount': [count: ReportPeriodCount] }>()

const { t } = useI18n()
const currencyStore = useCurrencyStore()

const periodCountItems = computed(() =>
  REPORT_PERIOD_COUNT_OPTIONS.map((count) => ({
    value: count,
    label: t('views.dashboard.overview.lastMonths', { count }),
  })),
)

const points = computed(() => {
  const minorUnit = currencyStore.getMinorUnit(PRIMARY_CURRENCY)
  return props.reports.map(({ period, report }) => {
    const amountMinor = report.entries
      .filter((entry) => entry.currency === PRIMARY_CURRENCY)
      .reduce((sum, entry) => sum + entry.netMinor, 0)
    return {
      label: formatPeriodAxisLabel(period),
      amountMinor,
      value: amountMinor / 10 ** minorUnit,
    }
  })
})

const hasData = computed(() => points.value.some((point) => point.amountMinor > 0))

function onSelectPeriodCount(count: unknown): void {
  const next = REPORT_PERIOD_COUNT_OPTIONS.find((option) => option === count)
  if (next) emit('update:periodCount', next)
}

function formatValue(value: number): string {
  const minorUnit = currencyStore.getMinorUnit(PRIMARY_CURRENCY)
  return formatMinor(Math.round(value * 10 ** minorUnit), PRIMARY_CURRENCY, minorUnit)
}

function formatAxis(value: number): string {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: PRIMARY_CURRENCY,
    maximumFractionDigits: 0,
  }).format(value)
}
</script>

<template>
  <UCard :ui="{ body: 'p-3 sm:p-4 lg:px-4 lg:pb-4' }">
    <DashboardCardHeader
      class="mb-2 max-lg:flex-col max-lg:items-stretch max-lg:gap-2"
      icon="i-lucide-chart-line"
      :title="t('views.dashboard.overview.spendingOverTime')"
    >
      <AppSelect
        :model-value="periodCount"
        :items="periodCountItems"
        :aria-label="t('views.dashboard.overview.chartPeriod')"
        variant="toolbar"
        class="max-lg:w-full"
        @update:model-value="onSelectPeriodCount"
      />
    </DashboardCardHeader>
    <USkeleton v-if="loading" class="h-[164px] w-full lg:h-[200px]" />
    <p v-else-if="!hasData" class="py-10 text-center text-sm text-muted">
      {{ t('views.dashboard.empty.monthlySpending') }}
    </p>
    <div v-else class="h-[164px] w-full lg:h-[200px]">
      <SpendingTrendChart
        :labels="points.map((point) => point.label)"
        :values="points.map((point) => point.value)"
        :format-value="formatValue"
        :format-axis="formatAxis"
      />
    </div>
  </UCard>
</template>
