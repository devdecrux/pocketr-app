<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import DashboardStatCard from '@/views/dashboard/DashboardStatCard.vue'
import { totalsByCurrency } from '@/utils/currencyTotals'
import { useAccountStore } from '@/stores/account'
import type { Account } from '@/types/ledger'
import { APP_ICONS } from '@/utils/appearance/appIcons'
import { PRIMARY_CURRENCY } from '@/utils/money'

/** "Available balance": the asset accounts in scope summed per currency. */
const props = defineProps<{
  /** The asset accounts in scope (the shared ones in household mode). */
  accounts: Account[]
  balances: Map<string, number>
  loading: boolean
}>()

const { t } = useI18n()
const accountStore = useAccountStore()

const amounts = computed(() =>
  totalsByCurrency(
    props.accounts.flatMap((account) => {
      const amountMinor = props.balances.get(account.id)
      return amountMinor === undefined ? [] : [{ currency: account.currency, amountMinor }]
    }),
    PRIMARY_CURRENCY,
  ),
)
</script>

<template>
  <DashboardStatCard
    :label="t('views.dashboard.overview.availableBalance')"
    :icon="APP_ICONS.asset"
    tone="success"
    :loading="accountStore.isLoading || loading"
    :empty-text="accounts.length === 0 ? t('views.dashboard.empty.accounts') : null"
    :amounts="amounts"
    :caption="t('views.dashboard.overview.acrossAccounts', accounts.length)"
  />
</template>
