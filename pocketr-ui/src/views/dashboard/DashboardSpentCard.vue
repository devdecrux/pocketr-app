<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import DashboardStatCard from '@/views/dashboard/DashboardStatCard.vue'
import { totalsByCurrency } from '@/utils/currencyTotals'
import { formatPeriodLabel } from '@/views/dashboard/dashboardPeriods'
import type { MonthlyReportEntry } from '@/types/ledger'
import { APP_ICONS } from '@/utils/appearance/appIcons'
import { PRIMARY_CURRENCY } from '@/utils/money'

/** "Spent this month" / "Spent in <period>": the selected period's expenses summed per currency. */
const props = defineProps<{
  /** Expense entries of the selected period. */
  entries: MonthlyReportEntry[]
  /** Whether any report loaded at all (tells "no data" from "nothing spent"). */
  hasReports: boolean
  loading: boolean
  period: string
  currentPeriod: string
}>()

const { t } = useI18n()

const amounts = computed(() =>
  totalsByCurrency(
    props.entries.map((entry) => ({ currency: entry.currency, amountMinor: entry.netMinor })),
    PRIMARY_CURRENCY,
  ),
)

const label = computed(() =>
  props.period === props.currentPeriod
    ? t('views.dashboard.overview.spentThisMonth')
    : t('views.dashboard.overview.spentInPeriod', { period: formatPeriodLabel(props.period) }),
)

const emptyText = computed(() => {
  if (amounts.value.length > 0) return null
  return props.hasReports
    ? t('views.dashboard.empty.spendings')
    : t('views.dashboard.empty.spendingData')
})
</script>

<template>
  <DashboardStatCard
    :label="label"
    :icon="APP_ICONS.expense"
    tone="error"
    :loading="loading"
    :empty-text="emptyText"
    :amounts="amounts"
    :caption="formatPeriodLabel(period)"
  />
</template>
