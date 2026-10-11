<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocalStorage } from '@vueuse/core'
import DashboardCardHeader from '@/views/dashboard/DashboardCardHeader.vue'
import { pickPrimaryCurrency } from '@/utils/currencyTotals'
import { useCurrencyStore } from '@/stores/currency'
import type { MonthlyReportEntry } from '@/types/ledger'
import { APP_ICONS } from '@/utils/appearance/appIcons'

/** "Top categories": the five biggest spending categories of the selected period or of all time. */
const props = defineProps<{
  /** Expense entries of the selected period. */
  periodEntries: MonthlyReportEntry[]
  periodLoading: boolean
  lifetimeEntries: MonthlyReportEntry[]
  lifetimeLoading: boolean
}>()

const { t } = useI18n()
const currencyStore = useCurrencyStore()

// Off = the selected rollover period, on = lifetime.
const view = useLocalStorage<'rollover' | 'lifetime'>('pocketr-dashboard-category-view', 'rollover')

const entries = computed(() =>
  view.value === 'lifetime' ? props.lifetimeEntries : props.periodEntries,
)
const loading = computed(() =>
  view.value === 'lifetime' ? props.lifetimeLoading : props.periodLoading,
)

const currency = computed(() =>
  pickPrimaryCurrency([
    ...new Set(entries.value.filter((entry) => entry.netMinor > 0).map((entry) => entry.currency)),
  ]),
)

const items = computed(() => {
  const categories = new Map<string, { name: string; amountMinor: number; color: string }>()
  for (const entry of entries.value) {
    if (entry.currency !== currency.value) continue

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

const maxAmount = computed(() => items.value[0]?.amountMinor ?? 0)

/** Category totals drop trailing zero cents (e.g. "€650"), keeping real cents ("€285.50"). */
function formatCategoryAmount(amountMinor: number): string {
  const minorUnit = currencyStore.getMinorUnit(currency.value)
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: currency.value,
    minimumFractionDigits: amountMinor % 10 ** minorUnit === 0 ? 0 : minorUnit,
    maximumFractionDigits: minorUnit,
  }).format(amountMinor / 10 ** minorUnit)
}

function resolveCategoryColor(categoryName: string | null, categoryColor: string | null): string {
  if (categoryColor && /^#[0-9a-f]{6}$/i.test(categoryColor)) {
    return categoryColor
  }
  if (categoryName === t('display.transactionKinds.DEBT_PAYMENT')) {
    return '#ef4444'
  }
  return 'var(--ui-primary)'
}
</script>

<template>
  <UCard :ui="{ body: 'p-3 sm:p-4 lg:p-4' }">
    <DashboardCardHeader
      class="mb-3 lg:h-7 lg:gap-2"
      :icon="APP_ICONS.categories"
      :title="t('views.dashboard.overview.topCategories')"
    >
      <UTooltip :text="t('views.dashboard.periodViews.lifetime')">
        <USwitch
          v-model="view"
          true-value="lifetime"
          false-value="rollover"
          :label="t('views.dashboard.periodViews.lifetime')"
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
    </DashboardCardHeader>
    <div v-if="loading" class="space-y-4">
      <USkeleton v-for="index in 4" :key="index" class="h-8 w-full" />
    </div>
    <p v-else-if="items.length === 0" class="py-10 text-center text-sm text-muted lg:py-6">
      {{ t('views.dashboard.empty.monthlySpending') }}
    </p>
    <!-- One shared grid (rows use subgrid): the amount column is as wide as the widest amount,
         so every bar track starts and ends at the same x and amounts right-align. -->
    <ul v-else class="grid grid-cols-[minmax(0,1fr)_auto] gap-x-4 gap-y-3.5 lg:gap-x-2">
      <li
        v-for="item in items"
        :key="item.name"
        class="col-span-2 grid grid-cols-subgrid items-end"
      >
        <span class="truncate text-sm text-highlighted">{{ item.name }}</span>
        <span
          class="row-span-2 self-center text-right text-sm whitespace-nowrap tabular-nums text-highlighted"
        >
          {{ formatCategoryAmount(item.amountMinor) }}
        </span>
        <span class="mt-1.5 block h-2 overflow-hidden rounded-full bg-(--pocketr-track)">
          <span
            class="block h-full rounded-full"
            :style="{
              width: `${maxAmount ? (item.amountMinor / maxAmount) * 100 : 0}%`,
              backgroundColor: item.color,
            }"
          />
        </span>
      </li>
    </ul>
  </UCard>
</template>
