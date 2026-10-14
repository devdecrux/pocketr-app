<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ALL_FILTER } from '@/views/accounts/useAccountFilters'
import { pickPrimaryCurrency, totalsByCurrency } from '@/utils/currencyTotals'
import { useAccountStore } from '@/stores/account'
import { useCurrencyStore } from '@/stores/currency'
import { useModeStore } from '@/stores/mode'
import { APP_ICONS } from '@/utils/appearance/appIcons'
import { formatMoney } from '@/utils/money'

const props = defineProps<{
  balances: Map<string, number>
  sharedAccountIds: Set<string>
  /** The selected currency filter, or `ALL`. */
  currencyFilter: string
  availableCurrencies: string[]
  loading: boolean
}>()

const { t } = useI18n()
const accountStore = useAccountStore()
const currencyStore = useCurrencyStore()
const modeStore = useModeStore()

// As on the dashboard, household mode counts only the accounts shared with the household.
const summaryAccounts = computed(() =>
  modeStore.isHousehold
    ? accountStore.activeAccounts.filter((account) => props.sharedAccountIds.has(account.id))
    : accountStore.activeAccounts,
)

// The selected currency, else EUR as on the dashboard (or the first one in use without any EUR account).
const summaryCurrency = computed(() =>
  props.currencyFilter !== ALL_FILTER
    ? props.currencyFilter
    : pickPrimaryCurrency(props.availableCurrencies),
)

function summarize(type: 'ASSET' | 'LIABILITY') {
  const accounts = summaryAccounts.value.filter(
    (account) =>
      account.type === type &&
      (props.currencyFilter === ALL_FILTER || account.currency === props.currencyFilter),
  )
  const known = accounts.filter((account) => props.balances.has(account.id))
  const totals = totalsByCurrency(
    known.map((account) => ({
      currency: account.currency,
      amountMinor: props.balances.get(account.id) ?? 0,
    })),
    summaryCurrency.value,
  )
  return {
    complete: known.length === accounts.length,
    primary: totals.find((total) => total.currency === summaryCurrency.value)?.amountMinor ?? 0,
    others: totals.filter((total) => total.currency !== summaryCurrency.value),
  }
}

const summaryCards = computed(() => [
  {
    key: 'available',
    label: t('views.accounts.summary.available'),
    icon: APP_ICONS.asset,
    circleClass: 'bg-(--pocketr-icon-bg)',
    iconClass: 'text-primary',
    ...summarize('ASSET'),
  },
  {
    key: 'debt',
    label: t('views.accounts.summary.debt'),
    icon: APP_ICONS.liability,
    circleClass: 'bg-warning/10',
    iconClass: 'text-(--pocketr-warning-fg)',
    ...summarize('LIABILITY'),
  },
])
</script>

<template>
  <div class="grid gap-3 lg:grid-cols-2 lg:gap-4">
    <UCard
      v-for="card in summaryCards"
      :key="card.key"
      :ui="{
        body: 'flex items-center gap-4 p-3.5 sm:p-3.5 lg:gap-5 lg:p-5',
      }"
    >
      <span
        class="flex size-[58px] shrink-0 items-center justify-center rounded-full lg:size-[74px]"
        :class="card.circleClass"
      >
        <UIcon
          :name="card.icon"
          class="size-7 lg:size-8"
          :class="card.iconClass"
          aria-hidden="true"
        />
      </span>
      <div class="min-w-0 flex-1">
        <p class="text-sm leading-5 text-muted">{{ card.label }}</p>
        <template v-if="loading">
          <USkeleton class="mt-1 h-8 w-40" />
          <USkeleton class="mt-1.5 h-4 w-24" />
        </template>
        <template v-else>
          <p
            class="text-[26px] leading-8 font-bold text-highlighted tabular-nums lg:text-[32px] lg:leading-10"
          >
            <template v-if="card.complete">
              {{ formatMoney(card.primary, summaryCurrency, currencyStore.getMinorUnit) }}
            </template>
            <template v-else>
              ...
              <span class="sr-only">{{ t('common.feedback.loading') }}</span>
            </template>
          </p>
          <p class="text-sm leading-5 text-muted">
            {{ t('views.accounts.summary.currencyAccounts', { currency: summaryCurrency }) }}
          </p>
          <div v-if="card.complete && card.others.length" class="flex flex-wrap gap-1.5 pt-1">
            <UBadge
              v-for="amount in card.others"
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
  </div>
</template>
