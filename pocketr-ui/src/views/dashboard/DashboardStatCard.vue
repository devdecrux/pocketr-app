<script setup lang="ts">
import { computed } from 'vue'
import { useCurrencyStore } from '@/stores/currency'
import type { CurrencyAmount } from '@/utils/currencyTotals'
import { formatMoney, PRIMARY_CURRENCY } from '@/utils/money'

/**
 * A summary card: icon tile, label, then the amount (primary currency large, other currencies as
 * badges) with a caption, a skeleton while loading or an empty message. Mobile lays the icon left
 * of the text; desktop (1/5 width) puts it top right beside the label.
 */
const props = defineProps<{
  label: string
  icon: string
  tone: 'success' | 'error'
  loading: boolean
  /** Shown instead of the amount when set. */
  emptyText: string | null
  amounts: CurrencyAmount[]
  caption: string
}>()

const currencyStore = useCurrencyStore()

const primaryAmountMinor = computed(
  () => props.amounts.find((amount) => amount.currency === PRIMARY_CURRENCY)?.amountMinor ?? 0,
)
const otherAmounts = computed(() =>
  props.amounts.filter((amount) => amount.currency !== PRIMARY_CURRENCY),
)

const TONE_CLASSES = {
  success: { tile: 'bg-success/10', icon: 'text-(--pocketr-success-fg)' },
  error: { tile: 'bg-error/10', icon: 'text-error' },
}
</script>

<template>
  <UCard
    :ui="{
      body: 'flex items-center gap-4 p-3 sm:p-4 lg:grid lg:h-full lg:grid-cols-[minmax(0,1fr)_auto] lg:content-center lg:items-center lg:gap-x-1.5 lg:gap-y-1 lg:p-3',
    }"
  >
    <span
      class="flex size-[60px] shrink-0 items-center justify-center rounded-full lg:col-start-2 lg:row-start-1 lg:size-9"
      :class="TONE_CLASSES[tone].tile"
    >
      <UIcon :name="icon" class="size-7 lg:size-5" :class="TONE_CLASSES[tone].icon" />
    </span>
    <div class="min-w-0 space-y-0.5 lg:contents">
      <p class="text-sm text-muted lg:col-start-1 lg:row-start-1 lg:truncate lg:text-[13px]">
        {{ label }}
      </p>
      <template v-if="loading">
        <USkeleton class="h-9 w-40 lg:col-span-2 lg:h-8 lg:w-full" />
        <USkeleton class="h-4 w-28 lg:col-span-2" />
      </template>
      <p v-else-if="emptyText" class="py-1 text-sm text-muted lg:col-span-2">
        {{ emptyText }}
      </p>
      <template v-else>
        <p
          class="text-[28px] font-bold leading-9 tabular-nums text-highlighted lg:col-span-2 lg:text-[22px] lg:leading-8 lg:[overflow-wrap:anywhere]"
        >
          {{ formatMoney(primaryAmountMinor, PRIMARY_CURRENCY, currencyStore.getMinorUnit) }}
        </p>
        <p class="text-sm text-muted lg:col-span-2 lg:truncate">{{ caption }}</p>
        <div v-if="otherAmounts.length > 0" class="flex flex-wrap gap-1.5 pt-1 lg:col-span-2">
          <UBadge
            v-for="amount in otherAmounts"
            :key="amount.currency"
            color="neutral"
            variant="outline"
            size="sm"
          >
            {{ formatMoney(amount.amountMinor, amount.currency, currencyStore.getMinorUnit) }}
          </UBadge>
        </div>
      </template>
    </div>
  </UCard>
</template>
