<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import DashboardCardHeader from '@/views/dashboard/DashboardCardHeader.vue'
import { useAccountStore } from '@/stores/account'
import { useCurrencyStore } from '@/stores/currency'
import type { LedgerTxn } from '@/types/ledger'
import { APP_ICONS } from '@/utils/appearance/appIcons'
import { daysAgo, formatIsoDate } from '@/utils/dates'
import { formatMoney } from '@/utils/money'
import { resolveSpendingAccountName } from '@/utils/txnAccounts'

/** "Recent expenses": the latest spending transactions with a link to all transactions. */
defineProps<{ expenses: LedgerTxn[]; loading: boolean }>()

const { t } = useI18n()
const accountStore = useAccountStore()
const currencyStore = useCurrencyStore()

function formatTxnAmount(txn: LedgerTxn): string {
  const targetType = txn.txnKind === 'DEBT_PAYMENT' ? 'LIABILITY' : 'EXPENSE'
  const targetSplits = txn.splits.filter(
    (split) => split.accountType === targetType && split.side === 'DEBIT',
  )
  const splits =
    targetSplits.length > 0 ? targetSplits : txn.splits.filter((split) => split.side === 'DEBIT')
  const amountMinor = splits.reduce((sum, split) => sum + split.amountMinor, 0)
  return `−${formatMoney(amountMinor, txn.currency, currencyStore.getMinorUnit)}`
}

/** Expense (or, for debt payments, liability) account name; the kind label when no name exists. */
function txnAccountLabel(txn: LedgerTxn): string {
  return (
    resolveSpendingAccountName(txn, (accountId) => accountStore.accountMap.get(accountId)) ??
    txnCategoryLabel(txn)
  )
}

function txnCategoryLabel(txn: LedgerTxn): string {
  if (txn.txnKind === 'DEBT_PAYMENT') return t('display.transactionKinds.DEBT_PAYMENT')
  const category = txn.splits.find(
    (split) => split.accountType === 'EXPENSE' && split.side === 'DEBIT',
  )
  return category?.categoryTagName ?? t('views.dashboard.uncategorized')
}

function txnDateLabel(txn: LedgerTxn): string {
  const age = daysAgo(txn.txnDate, new Date())
  if (age === 0) return t('views.transactions.groups.today')
  if (age === 1) return t('views.transactions.groups.yesterday')
  return formatIsoDate(txn.txnDate)
}
</script>

<template>
  <UCard :ui="{ body: 'p-3 sm:p-4 lg:p-4' }">
    <!-- Row-2 headers: title (truncates at 1/5 width) left, action right, one fixed desktop height. -->
    <DashboardCardHeader
      class="pb-3 lg:mb-3 lg:h-7 lg:gap-2 lg:pb-0"
      :icon="APP_ICONS.expense"
      :title="t('views.dashboard.overview.recentExpenses')"
    >
      <UTooltip :text="t('views.dashboard.overview.viewAll')">
        <ULink
          to="/transactions"
          class="inline-flex shrink-0 items-center text-sm whitespace-nowrap text-primary hover:underline"
        >
          <UIcon name="i-lucide-arrow-right" class="hidden size-4 lg:max-2xl:inline-block" />
          <span class="lg:max-2xl:sr-only">{{ t('views.dashboard.overview.viewAll') }}</span>
        </ULink>
      </UTooltip>
    </DashboardCardHeader>
    <div v-if="loading" class="space-y-3">
      <USkeleton v-for="index in 3" :key="index" class="h-12 w-full" />
    </div>
    <p v-else-if="expenses.length === 0" class="py-6 text-center text-sm text-muted">
      {{ t('views.dashboard.empty.transactions') }}
    </p>
    <ul v-else class="divide-y divide-default border-t border-default">
      <li
        v-for="txn in expenses"
        :key="txn.id"
        class="flex items-center gap-4 py-2 lg:grid lg:grid-cols-[minmax(0,1fr)_auto] lg:gap-x-2 lg:gap-y-0.5"
      >
        <span
          class="flex size-11 shrink-0 items-center justify-center rounded-lg bg-(--pocketr-tile-bg) lg:hidden"
        >
          <UIcon :name="APP_ICONS.expense" class="size-5 text-highlighted dark:text-primary" />
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
</template>
