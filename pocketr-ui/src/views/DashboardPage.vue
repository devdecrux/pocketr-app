<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowUpDown, TrendingDown, Wallet } from 'lucide-vue-next'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { useAccountStore } from '@/stores/account'
import { useCurrencyStore } from '@/stores/currency'
import { useLedgerStore } from '@/stores/ledger'
import { useModeStore } from '@/stores/mode'
import { getAccountBalance } from '@/api/ledger'
import { getMonthlyReport } from '@/api/reports'
import { formatMinor } from '@/utils/money'
import type { MonthlyReportEntry } from '@/types/ledger'

const accountStore = useAccountStore()
const currencyStore = useCurrencyStore()
const ledgerStore = useLedgerStore()
const modeStore = useModeStore()

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

const recentTransactions = computed(() => {
  return ledgerStore.transactions.slice(0, 10)
})

const spendingByCategory = computed(() => {
  const map = new Map<string, { name: string; total: number; currency: string }>()
  for (const entry of monthlyReport.value) {
    const key = entry.categoryId ?? 'uncategorized'
    const account = accountStore.accountMap.get(entry.expenseAccountId)
    const currency = account?.currency ?? ''
    const existing = map.get(key)
    if (existing) {
      existing.total += entry.netMinor
    } else {
      map.set(key, {
        name: entry.categoryName ?? 'Uncategorized',
        total: entry.netMinor,
        currency,
      })
    }
  }
  return [...map.values()].sort((a, b) => b.total - a.total)
})

const spendingByAccount = computed(() => {
  const map = new Map<string, { name: string; total: number; currency: string }>()
  for (const entry of monthlyReport.value) {
    const key = entry.expenseAccountId
    const account = accountStore.accountMap.get(key)
    const currency = account?.currency ?? ''
    const existing = map.get(key)
    if (existing) {
      existing.total += entry.netMinor
    } else {
      map.set(key, {
        name: entry.expenseAccountName,
        total: entry.netMinor,
        currency,
      })
    }
  }
  return [...map.values()].sort((a, b) => b.total - a.total)
})

async function loadBalances(): Promise<void> {
  balancesLoading.value = true
  const promises = topAccounts.value.map(async (account) => {
    try {
      const result = await getAccountBalance(account.id)
      balances.value.set(account.id, result.balanceMinor)
    } catch {
      // skip
    }
  })
  await Promise.all(promises)
  balancesLoading.value = false
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
    <h1 class="text-2xl font-semibold">Dashboard</h1>
    <p class="text-sm text-muted-foreground">
      {{ modeStore.isHousehold ? 'Household overview' : 'Your personal overview' }}
    </p>

    <div class="grid gap-4 md:grid-cols-2">
      <!-- Account Balances -->
      <Card>
        <CardHeader class="flex flex-row items-center gap-2">
          <Wallet class="size-5 text-muted-foreground" />
          <CardTitle>Account Balances</CardTitle>
        </CardHeader>
        <CardContent>
          <div v-if="accountStore.isLoading || balancesLoading" class="space-y-3">
            <Skeleton class="h-8 w-full" />
            <Skeleton class="h-8 w-full" />
            <Skeleton class="h-8 w-full" />
          </div>
          <div v-else-if="topAccounts.length === 0" class="text-sm text-muted-foreground">
            No accounts yet.
          </div>
          <ul v-else class="space-y-2">
            <li
              v-for="account in topAccounts"
              :key="account.id"
              class="flex items-center justify-between rounded-md border px-3 py-2"
            >
              <div class="flex items-center gap-2">
                <span class="text-sm font-medium">{{ account.name }}</span>
                <Badge variant="outline" class="text-xs">{{ account.type }}</Badge>
              </div>
              <span class="text-sm font-mono">
                {{ formatAccountBalance(account.id, account.currency) }}
              </span>
            </li>
          </ul>
        </CardContent>
      </Card>

      <!-- Recent Transactions -->
      <Card>
        <CardHeader class="flex flex-row items-center gap-2">
          <ArrowUpDown class="size-5 text-muted-foreground" />
          <CardTitle>Recent Transactions</CardTitle>
        </CardHeader>
        <CardContent>
          <div v-if="ledgerStore.isLoading" class="space-y-3">
            <Skeleton class="h-8 w-full" />
            <Skeleton class="h-8 w-full" />
            <Skeleton class="h-8 w-full" />
          </div>
          <div v-else-if="recentTransactions.length === 0" class="text-sm text-muted-foreground">
            No transactions yet.
          </div>
          <ul v-else class="space-y-2">
            <li
              v-for="txn in recentTransactions"
              :key="txn.id"
              class="flex items-center justify-between rounded-md border px-3 py-2"
            >
              <div>
                <span class="text-sm font-medium">{{ txn.description }}</span>
                <span class="ml-2 text-xs text-muted-foreground">{{ txn.txnDate }}</span>
              </div>
              <span class="text-sm font-mono">{{ txnAmount(txn) }}</span>
            </li>
          </ul>
        </CardContent>
      </Card>

      <!-- Monthly Spending by Category -->
      <Card>
        <CardHeader class="flex flex-row items-center gap-2">
          <TrendingDown class="size-5 text-muted-foreground" />
          <CardTitle>Spending by Category</CardTitle>
        </CardHeader>
        <CardContent>
          <p class="mb-2 text-xs text-muted-foreground">{{ currentPeriod }}</p>
          <div v-if="reportLoading" class="space-y-3">
            <Skeleton class="h-6 w-full" />
            <Skeleton class="h-6 w-full" />
          </div>
          <div v-else-if="spendingByCategory.length === 0" class="text-sm text-muted-foreground">
            No spending data for this month.
          </div>
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
        <CardHeader class="flex flex-row items-center gap-2">
          <TrendingDown class="size-5 text-muted-foreground" />
          <CardTitle>Spending by Account</CardTitle>
        </CardHeader>
        <CardContent>
          <p class="mb-2 text-xs text-muted-foreground">{{ currentPeriod }}</p>
          <div v-if="reportLoading" class="space-y-3">
            <Skeleton class="h-6 w-full" />
            <Skeleton class="h-6 w-full" />
          </div>
          <div v-else-if="spendingByAccount.length === 0" class="text-sm text-muted-foreground">
            No spending data for this month.
          </div>
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
