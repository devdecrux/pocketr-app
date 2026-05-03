<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowUpDown, TrendingDown, Wallet } from 'lucide-vue-next'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { useAccountStore } from '@/stores/account'
import { useCurrencyStore } from '@/stores/currency'
import { useLedgerStore } from '@/stores/ledger'
import { useModeStore } from '@/stores/mode'
import { useHouseholdStore } from '@/stores/household'
import { getAccountBalances } from '@/api/ledger'
import { getMonthlyReport } from '@/api/reports'
import { formatMinor } from '@/utils/money'
import type { MonthlyReportEntry } from '@/types/ledger'
import { AppCardHeader, AppListItem, AppPageHeader, AppStateMessage } from '@/components/app'
import { useI18n } from 'vue-i18n'

const accountStore = useAccountStore()
const currencyStore = useCurrencyStore()
const ledgerStore = useLedgerStore()
const modeStore = useModeStore()
const householdStore = useHouseholdStore()
const { t } = useI18n()

const balances = ref<Map<string, number>>(new Map())
const balancesLoading = ref(false)
const monthlyReport = ref<MonthlyReportEntry[]>([])
const reportLoading = ref(false)

const currentPeriod = computed(() => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
})

const topAccounts = computed(() => {
  return accountStore.activeAccounts
    .filter((a) => a.type === 'ASSET' || a.type === 'LIABILITY')
    .slice(0, 6)
})

const sharedAccountIds = computed(() => {
  const ids = new Set<string>()
  for (const share of householdStore.sharedAccounts) {
    ids.add(share.accountId)
  }
  return ids
})

const recentTransactions = computed(() => {
  return ledgerStore.transactions.slice(0, 10)
})

const spendingByCategory = computed(() => {
  const map = new Map<string, { name: string; total: number; currency: string }>()
  for (const entry of monthlyReport.value) {
    const key =
      entry.categoryTagId ??
      (entry.categoryTagName ? `named:${entry.categoryTagName}` : 'uncategorized')
    const existing = map.get(key)
    if (existing) {
      existing.total += entry.netMinor
    } else {
      map.set(key, {
        name: entry.categoryTagName ?? t('views.dashboard.uncategorized'),
        total: entry.netMinor,
        currency: entry.currency,
      })
    }
  }
  return [...map.values()].sort((a, b) => b.total - a.total)
})

const spendingByAccount = computed(() => {
  const map = new Map<string, { name: string; total: number; currency: string }>()
  for (const entry of monthlyReport.value) {
    const key = entry.expenseAccountId
    const existing = map.get(key)
    if (existing) {
      existing.total += entry.netMinor
    } else {
      map.set(key, {
        name: entry.expenseAccountName,
        total: entry.netMinor,
        currency: entry.currency,
      })
    }
  }
  return [...map.values()].sort((a, b) => b.total - a.total)
})

async function loadBalances(): Promise<void> {
  balancesLoading.value = true
  balances.value = new Map()
  const accountIds = modeStore.isHousehold
    ? topAccounts.value
        .map((account) => account.id)
        .filter((accountId) => sharedAccountIds.value.has(accountId))
    : topAccounts.value.map((account) => account.id)

  try {
    const result = await getAccountBalances(
      accountIds,
      undefined,
      modeStore.householdId ?? undefined,
    )
    for (const balance of result) {
      balances.value.set(balance.accountId, balance.balanceMinor)
    }
  } catch {
    // skip
  } finally {
    balancesLoading.value = false
  }
}

async function loadReport(): Promise<void> {
  reportLoading.value = true
  try {
    monthlyReport.value = await getMonthlyReport({
      mode: modeStore.modeParam,
      householdId: modeStore.householdId ?? undefined,
      period: currentPeriod.value,
    })
  } catch {
    monthlyReport.value = []
  } finally {
    reportLoading.value = false
  }
}

async function loadAll(): Promise<void> {
  await Promise.all([accountStore.load(), currencyStore.load(), ledgerStore.load()])
  if (modeStore.isHousehold && modeStore.householdId) {
    await householdStore.loadSharedAccounts(modeStore.householdId)
  }
  await Promise.all([loadBalances(), loadReport()])
}

onMounted(loadAll)

watch(() => modeStore.viewMode, loadAll, { deep: true })

function formatAccountBalance(accountId: string, currency: string): string {
  const bal = balances.value.get(accountId)
  if (bal === undefined) return '...'
  const minorUnit = currencyStore.getMinorUnit(currency)
  return formatMinor(bal, currency, minorUnit)
}

function formatSpending(amountMinor: number, currency: string): string {
  if (!currency) {
    const minorUnit = 2
    return (amountMinor / 10 ** minorUnit).toFixed(minorUnit)
  }
  const minorUnit = currencyStore.getMinorUnit(currency)
  return formatMinor(amountMinor, currency, minorUnit)
}

function txnAmount(txn: (typeof recentTransactions.value)[number]): string {
  const totalMinor = txn.splits
    .filter((s) => s.side === 'DEBIT')
    .reduce((sum, s) => sum + s.amountMinor, 0)
  const minorUnit = currencyStore.getMinorUnit(txn.currency)
  return formatMinor(totalMinor, txn.currency, minorUnit)
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

    <div class="grid gap-4 md:grid-cols-2">
      <!-- Account Balances -->
      <Card>
        <AppCardHeader
          :title="$t('views.dashboard.cards.accountBalances')"
          class="justify-start gap-2"
        >
          <template #leading>
            <Wallet class="size-5 text-muted-foreground" />
          </template>
        </AppCardHeader>
        <CardContent>
          <div v-if="accountStore.isLoading || balancesLoading" class="space-y-3">
            <Skeleton class="h-8 w-full" />
            <Skeleton class="h-8 w-full" />
            <Skeleton class="h-8 w-full" />
          </div>
          <AppStateMessage v-else-if="topAccounts.length === 0">
            {{ $t('views.dashboard.empty.accounts') }}
          </AppStateMessage>
          <ul v-else class="space-y-2">
            <AppListItem v-for="account in topAccounts" :key="account.id" as="li">
              <div class="flex items-center gap-2">
                <span class="text-sm font-medium">{{ account.name }}</span>
                <Badge variant="outline" class="text-xs">{{
                  $t(`display.accountTypes.${account.type}`)
                }}</Badge>
              </div>
              <template #actions>
                <span class="text-sm font-mono">
                  {{ formatAccountBalance(account.id, account.currency) }}
                </span>
              </template>
            </AppListItem>
          </ul>
        </CardContent>
      </Card>

      <!-- Recent Transactions -->
      <Card>
        <AppCardHeader
          :title="$t('views.dashboard.cards.recentTransactions')"
          class="justify-start gap-2"
        >
          <template #leading>
            <ArrowUpDown class="size-5 text-muted-foreground" />
          </template>
        </AppCardHeader>
        <CardContent>
          <div v-if="ledgerStore.isLoading" class="space-y-3">
            <Skeleton class="h-8 w-full" />
            <Skeleton class="h-8 w-full" />
            <Skeleton class="h-8 w-full" />
          </div>
          <AppStateMessage v-else-if="recentTransactions.length === 0">
            {{ $t('views.dashboard.empty.transactions') }}
          </AppStateMessage>
          <ul v-else class="space-y-2">
            <AppListItem v-for="txn in recentTransactions" :key="txn.id" as="li">
              <div>
                <span class="text-sm font-medium">{{ txn.description }}</span>
                <span class="ml-2 text-xs text-muted-foreground">{{ txn.txnDate }}</span>
              </div>
              <template #actions>
                <span class="text-sm font-mono">{{ txnAmount(txn) }}</span>
              </template>
            </AppListItem>
          </ul>
        </CardContent>
      </Card>

      <!-- Monthly Spending by Category -->
      <Card>
        <AppCardHeader
          :title="$t('views.dashboard.cards.spendingByCategory')"
          class="justify-start gap-2"
        >
          <template #leading>
            <TrendingDown class="size-5 text-muted-foreground" />
          </template>
        </AppCardHeader>
        <CardContent>
          <p class="mb-2 text-xs text-muted-foreground">{{ currentPeriod }}</p>
          <div v-if="reportLoading" class="space-y-3">
            <Skeleton class="h-6 w-full" />
            <Skeleton class="h-6 w-full" />
          </div>
          <AppStateMessage v-else-if="spendingByCategory.length === 0">
            {{ $t('views.dashboard.empty.monthlySpending') }}
          </AppStateMessage>
          <ul v-else class="space-y-2">
            <li
              v-for="cat in spendingByCategory"
              :key="cat.name"
              class="flex items-center justify-between text-sm"
            >
              <span>{{ cat.name }}</span>
              <span class="font-mono">{{ formatSpending(cat.total, cat.currency) }}</span>
            </li>
          </ul>
        </CardContent>
      </Card>

      <!-- Monthly Spending by Expense Account -->
      <Card>
        <AppCardHeader
          :title="$t('views.dashboard.cards.spendingByAccount')"
          class="justify-start gap-2"
        >
          <template #leading>
            <TrendingDown class="size-5 text-muted-foreground" />
          </template>
        </AppCardHeader>
        <CardContent>
          <p class="mb-2 text-xs text-muted-foreground">{{ currentPeriod }}</p>
          <div v-if="reportLoading" class="space-y-3">
            <Skeleton class="h-6 w-full" />
            <Skeleton class="h-6 w-full" />
          </div>
          <AppStateMessage v-else-if="spendingByAccount.length === 0">
            {{ $t('views.dashboard.empty.monthlySpending') }}
          </AppStateMessage>
          <ul v-else class="space-y-2">
            <li
              v-for="acc in spendingByAccount"
              :key="acc.name"
              class="flex items-center justify-between text-sm"
            >
              <span>{{ acc.name }}</span>
              <span class="font-mono">{{ formatSpending(acc.total, acc.currency) }}</span>
            </li>
          </ul>
        </CardContent>
      </Card>
    </div>
  </section>
</template>
