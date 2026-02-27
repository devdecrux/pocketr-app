<script setup lang="ts">
import { HTTPError } from 'ky'
import { computed, h, onMounted, ref, watch } from 'vue'
import {
  createColumnHelper,
  getCoreRowModel,
  getExpandedRowModel,
  useVueTable,
} from '@tanstack/vue-table'
import { createTxn } from '@/api/ledger'
import { useAccountStore } from '@/stores/account'
import { useCategoryStore } from '@/stores/category'
import { useCurrencyStore } from '@/stores/currency'
import { useHouseholdStore } from '@/stores/household'
import { useLedgerStore } from '@/stores/ledger'
import { useModeStore } from '@/stores/mode'
import type { LedgerSplit, LedgerTxn } from '@/types/ledger'
import { expenseStrategy, incomeStrategy, transferStrategy } from '@/utils/txnStrategies'
import { formatMinor } from '@/utils/money'
import AccountSelector from '@/components/AccountSelector.vue'
import CategoryTagSelector from '@/components/CategoryTagSelector.vue'
import DateRangePicker from '@/components/DateRangePicker.vue'
import CurrencyAmountInput from '@/components/CurrencyAmountInput.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ArrowLeftRight, ChevronDown, ChevronRight, Plus } from 'lucide-vue-next'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { initialsFromName } from '@/utils/initials'
import DataTable from '@/components/DataTable.vue'

const ledgerStore = useLedgerStore()
const accountStore = useAccountStore()
const categoryStore = useCategoryStore()
const currencyStore = useCurrencyStore()
const modeStore = useModeStore()
const householdStore = useHouseholdStore()

// Filters
const filterDateFrom = ref<string | undefined>(undefined)
const filterDateTo = ref<string | undefined>(undefined)
const filterAccountId = ref('')
const filterCategoryId = ref<string | null>(null)
const filterSearch = ref('')

// Create dialog
const dialogOpen = ref(false)
const activeTab = ref('expense')
const isSubmitting = ref(false)
const submitError = ref('')

// Expense form
const expenseDate = ref(todayString())
const expensePayFrom = ref('')
const expenseAccount = ref('')
const expenseAmount = ref(0)
const expenseCategory = ref<string | null>(null)
const expenseDescription = ref('')

// Income form
const incomeDate = ref(todayString())
const incomeDeposit = ref('')
const incomeAccount = ref('')
const incomeAmount = ref(0)
const incomeDescription = ref('')

// Transfer form
const transferDate = ref(todayString())
const transferFrom = ref('')
const transferTo = ref('')
const transferAmount = ref(0)
const transferDescription = ref('')

function todayString(): string {
  return new Date().toISOString().slice(0, 10)
}

// Derived currency for expense (from pay-from account)
const expenseCurrency = computed(() => {
  const acc = accountStore.accountMap.get(expensePayFrom.value)
  return acc?.currency ?? ''
})

const expenseMinorUnit = computed(() => currencyStore.getMinorUnit(expenseCurrency.value))

// Derived currency for income (from deposit account)
const incomeCurrency = computed(() => {
  const acc = accountStore.accountMap.get(incomeDeposit.value)
  return acc?.currency ?? ''
})

const incomeMinorUnit = computed(() => currencyStore.getMinorUnit(incomeCurrency.value))

// Derived currency for transfer (from "from" account)
const transferCurrency = computed(() => {
  const acc = accountStore.accountMap.get(transferFrom.value)
  return acc?.currency ?? ''
})

const transferMinorUnit = computed(() => currencyStore.getMinorUnit(transferCurrency.value))

// Cross-user transfer info
const isCrossUserTransfer = computed(() => {
  if (!modeStore.isHousehold) return false
  const from = accountStore.accountMap.get(transferFrom.value)
  const to = accountStore.accountMap.get(transferTo.value)
  if (!from || !to) return false
  return from.ownerUserId !== to.ownerUserId
})

// --- Transaction tab strategies (dispatch table) ---
function getStrategyResult(): {
  error: string | null
  request?: ReturnType<typeof expenseStrategy.buildRequest>
} {
  const ctx = { mode: modeStore.modeParam, householdId: modeStore.householdId }

  if (activeTab.value === 'expense') {
    const fields = {
      date: expenseDate.value,
      payFrom: expensePayFrom.value,
      account: expenseAccount.value,
      amount: expenseAmount.value,
      currency: expenseCurrency.value,
      description: expenseDescription.value,
      categoryTagId: expenseCategory.value,
    }
    const error = expenseStrategy.validate(fields)
    return error ? { error } : { error: null, request: expenseStrategy.buildRequest(ctx, fields) }
  }

  if (activeTab.value === 'income') {
    const fields = {
      date: incomeDate.value,
      deposit: incomeDeposit.value,
      account: incomeAccount.value,
      amount: incomeAmount.value,
      currency: incomeCurrency.value,
      description: incomeDescription.value,
    }
    const error = incomeStrategy.validate(fields)
    return error ? { error } : { error: null, request: incomeStrategy.buildRequest(ctx, fields) }
  }

  if (activeTab.value === 'transfer') {
    const fields = {
      date: transferDate.value,
      from: transferFrom.value,
      to: transferTo.value,
      amount: transferAmount.value,
      currency: transferCurrency.value,
      description: transferDescription.value,
    }
    const error = transferStrategy.validate(fields)
    return error ? { error } : { error: null, request: transferStrategy.buildRequest(ctx, fields) }
  }

  return { error: 'Unknown tab.' }
}

// Derive txn kind display label from the backend-provided txnKind field
function deriveTxnKind(txn: LedgerTxn): string {
  switch (txn.txnKind) {
    case 'EXPENSE':
      return 'Expense'
    case 'INCOME':
      return 'Income'
    default:
      return 'Transfer'
  }
}

function txnKindVariant(kind: string) {
  if (kind === 'Expense') return 'destructive' as const
  if (kind === 'Income') return 'default' as const
  return 'secondary' as const
}

// Unique categories across all splits of a transaction
function txnCategories(txn: LedgerTxn): { name: string; color?: string | null }[] {
  const seen = new Set<string>()
  const result: { name: string; color?: string | null }[] = []
  for (const split of txn.splits) {
    if (split.categoryTagId) {
      const cat = categoryStore.categories.find((c) => c.id === split.categoryTagId)
      if (cat && !seen.has(cat.name)) {
        seen.add(cat.name)
        result.push({ name: cat.name, color: cat.color })
      }
    }
  }
  return result
}

// Total amount for display
function txnDisplayAmount(txn: LedgerTxn): string {
  const total = txn.splits.reduce((sum, s) => {
    if (s.side === 'DEBIT') return sum + s.amountMinor
    return sum
  }, 0)
  const minorUnit = currencyStore.getMinorUnit(txn.currency)
  return formatMinor(total, txn.currency, minorUnit)
}

function txnAmountClass(txn: LedgerTxn): string {
  switch (txn.txnKind) {
    case 'EXPENSE':
      return 'text-red-500'
    case 'INCOME':
      return 'text-green-500'
    default:
      return 'text-muted-foreground'
  }
}

// Filtered transactions
const filteredTransactions = computed(() => {
  let txns = ledgerStore.transactions
  if (filterSearch.value.trim()) {
    const q = filterSearch.value.trim().toLowerCase()
    txns = txns.filter((t) => t.description.toLowerCase().includes(q))
  }
  return txns
})

// TanStack table
const columnHelper = createColumnHelper<LedgerTxn>()

const columns = computed(() => {
  const cols = [
    columnHelper.display({
      id: 'expand',
      meta: { tdClass: 'w-8' },
      cell: ({ row }) =>
        h(row.getIsExpanded() ? ChevronDown : ChevronRight, {
          class: 'size-4 text-muted-foreground',
        }),
    }),
    columnHelper.accessor('txnDate', {
      header: 'Date',
      meta: { tdClass: 'whitespace-nowrap' },
    }),
    columnHelper.accessor('description', {
      header: 'Description',
    }),
    columnHelper.display({
      id: 'kind',
      header: 'Type',
      cell: ({ row }) =>
        h(Badge, { variant: txnKindVariant(deriveTxnKind(row.original)), class: 'text-xs' }, () =>
          deriveTxnKind(row.original),
        ),
    }),
    columnHelper.display({
      id: 'categories',
      header: 'Category',
      cell: ({ row }) => {
        const cats = txnCategories(row.original)
        if (!cats.length) return null
        return h(
          'div',
          { class: 'flex flex-wrap justify-end gap-1' },
          cats.map((cat) =>
            h(
              'span',
              {
                key: cat.name,
                class: [
                  'inline-block rounded-md px-2 py-1 text-xs font-medium',
                  !cat.color ? 'bg-secondary text-secondary-foreground' : '',
                ],
                style: cat.color ? { backgroundColor: cat.color + '33', color: cat.color } : {},
              },
              cat.name,
            ),
          ),
        )
      },
    }),
    columnHelper.display({
      id: 'amount',
      header: 'Amount',
      cell: ({ row }) =>
        h(
          'span',
          {
            class: `inline-flex items-center justify-end gap-1 whitespace-nowrap font-medium ${txnAmountClass(row.original)}`,
          },
          [
            row.original.txnKind === 'TRANSFER'
              ? h(ArrowLeftRight, { class: 'size-3' })
              : h('span', {}, row.original.txnKind === 'EXPENSE' ? '-' : '+'),
            txnDisplayAmount(row.original),
          ],
        ),
    }),
  ]

  if (modeStore.isHousehold) {
    cols.push(
      columnHelper.display({
        id: 'member',
        header: 'Member',
        cell: ({ row }) => {
          const creator = row.original.createdBy
          if (!creator) return null
          const name =
            [creator.firstName, creator.lastName].filter(Boolean).join(' ') || creator.email
          return h('div', { class: 'flex items-center justify-end gap-2' }, [
            h('div', { class: 'grid text-right text-sm leading-tight' }, [
              h('span', { class: 'truncate text-xs font-semibold' }, name),
              h('span', { class: 'truncate text-[10px] text-muted-foreground' }, creator.email),
            ]),
            h(Avatar, { class: 'h-8 w-8 shrink-0 rounded-lg border border-border' }, () => [
              creator.avatar ? h(AvatarImage, { src: creator.avatar }) : null,
              h(AvatarFallback, { class: 'rounded-lg text-xs' }, () =>
                initialsFromName(creator.firstName, creator.lastName),
              ),
            ]),
          ])
        },
      }),
    )
  }

  return cols
})

const table = useVueTable({
  get data() {
    return filteredTransactions.value
  },
  get columns() {
    return columns.value
  },
  getCoreRowModel: getCoreRowModel(),
  getExpandedRowModel: getExpandedRowModel(),
  getRowCanExpand: () => true,
})

// Load data on mount and mode change
async function loadData(resetPage = false): Promise<void> {
  const filters: Record<string, string | undefined> = {}
  if (filterDateFrom.value) filters.dateFrom = filterDateFrom.value
  if (filterDateTo.value) filters.dateTo = filterDateTo.value
  if (filterAccountId.value) filters.accountId = filterAccountId.value
  if (filterCategoryId.value) filters.categoryId = filterCategoryId.value

  const page = resetPage ? 0 : ledgerStore.currentPage
  if (resetPage) ledgerStore.currentPage = 0
  await ledgerStore.load(filters, page, ledgerStore.pageSize)
}

async function goToPage(page: number): Promise<void> {
  ledgerStore.currentPage = page
  await loadData()
}

async function changePageSize(size: number): Promise<void> {
  ledgerStore.pageSize = size
  ledgerStore.currentPage = 0
  await loadData()
}

onMounted(async () => {
  await Promise.all([
    accountStore.load(),
    categoryStore.load(),
    currencyStore.load(),
    householdStore.loadHouseholds(),
    loadData(),
  ])
})

watch(
  [() => modeStore.viewMode, filterDateFrom, filterDateTo, filterAccountId, filterCategoryId],
  () => {
    void loadData(true)
  },
)

// Split display helper
function splitLabel(split: LedgerSplit): string {
  const acc = accountStore.accountMap.get(split.accountId)
  return acc?.name ?? split.accountId
}

function splitAmount(split: LedgerSplit, currency: string): string {
  const minorUnit = currencyStore.getMinorUnit(currency)
  const prefix = split.side === 'DEBIT' ? '+' : '-'
  return `${prefix}${formatMinor(split.amountMinor, currency, minorUnit)}`
}

function orderedSplits(txn: LedgerTxn): LedgerSplit[] {
  const sideRank: Record<'CREDIT' | 'DEBIT', number> = {
    CREDIT: 0,
    DEBIT: 1,
  }
  return [...txn.splits].sort((a, b) => sideRank[a.side] - sideRank[b.side])
}

// Reset form
function resetForms(): void {
  expenseDate.value = todayString()
  expensePayFrom.value = ''
  expenseAccount.value = ''
  expenseAmount.value = 0
  expenseCategory.value = null
  expenseDescription.value = ''

  incomeDate.value = todayString()
  incomeDeposit.value = ''
  incomeAccount.value = ''
  incomeAmount.value = 0
  incomeDescription.value = ''

  transferDate.value = todayString()
  transferFrom.value = ''
  transferTo.value = ''
  transferAmount.value = 0
  transferDescription.value = ''

  submitError.value = ''
}

// Submit transaction
async function submitTransaction(): Promise<void> {
  submitError.value = ''

  const { error: validationError, request } = getStrategyResult()
  if (validationError || !request) {
    submitError.value = validationError ?? ''
    return
  }

  isSubmitting.value = true

  try {
    await createTxn(request)
    dialogOpen.value = false
    resetForms()
    await loadData()
  } catch (error: unknown) {
    if (error instanceof HTTPError) {
      const payload = await error.response.json<{ message?: string }>().catch(() => null)
      submitError.value = payload?.message?.trim() || 'Failed to create transaction.'
    } else {
      submitError.value = 'Failed to create transaction.'
    }
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <section class="flex h-full flex-col gap-4">
    <Card class="flex-1 min-h-0">
      <CardHeader class="flex flex-row items-center justify-between">
        <CardTitle class="text-2xl">Transactions</CardTitle>
        <Dialog v-model:open="dialogOpen">
          <DialogTrigger as-child>
            <Button size="sm" @click="resetForms">
              <Plus class="mr-1 size-4" />
              New Transaction
            </Button>
          </DialogTrigger>
          <DialogContent class="max-w-lg">
            <DialogHeader>
              <DialogTitle>Create Transaction</DialogTitle>
              <DialogDescription> Record an expense, income, or transfer. </DialogDescription>
            </DialogHeader>

            <Tabs v-model="activeTab" class="w-full">
              <TabsList class="w-full">
                <TabsTrigger value="expense" class="flex-1">Expense</TabsTrigger>
                <TabsTrigger value="income" class="flex-1">Income</TabsTrigger>
                <TabsTrigger value="transfer" class="flex-1">Transfer</TabsTrigger>
              </TabsList>

              <!-- Expense Tab -->
              <TabsContent value="expense" class="space-y-4 pt-4">
                <div class="grid gap-2">
                  <Label for="expense-date">Date</Label>
                  <Input id="expense-date" v-model="expenseDate" type="date" />
                </div>
                <div class="grid gap-2">
                  <Label>Pay from (Asset/Liability)</Label>
                  <AccountSelector
                    v-model="expensePayFrom"
                    :allowed-types="['ASSET', 'LIABILITY']"
                    placeholder="Select pay-from account"
                  />
                </div>
                <div class="grid gap-2">
                  <Label>Expense account</Label>
                  <AccountSelector
                    v-model="expenseAccount"
                    :allowed-types="['EXPENSE']"
                    placeholder="Select expense account"
                  />
                </div>
                <div class="grid gap-2">
                  <Label>Amount</Label>
                  <CurrencyAmountInput
                    v-model="expenseAmount"
                    :minor-unit="expenseMinorUnit"
                    :currency-code="expenseCurrency"
                  />
                </div>
                <div class="grid gap-2">
                  <Label>Category</Label>
                  <CategoryTagSelector v-model="expenseCategory" />
                </div>
                <div class="grid gap-2">
                  <Label for="expense-desc">Description</Label>
                  <Input
                    id="expense-desc"
                    v-model="expenseDescription"
                    placeholder="What was this for?"
                  />
                </div>
              </TabsContent>

              <!-- Income Tab -->
              <TabsContent value="income" class="space-y-4 pt-4">
                <div class="grid gap-2">
                  <Label for="income-date">Date</Label>
                  <Input id="income-date" v-model="incomeDate" type="date" />
                </div>
                <div class="grid gap-2">
                  <Label>Deposit to (Asset)</Label>
                  <AccountSelector
                    v-model="incomeDeposit"
                    :allowed-types="['ASSET']"
                    placeholder="Select deposit account"
                  />
                </div>
                <div class="grid gap-2">
                  <Label>Income account</Label>
                  <AccountSelector
                    v-model="incomeAccount"
                    :allowed-types="['INCOME']"
                    placeholder="Select income account"
                  />
                </div>
                <div class="grid gap-2">
                  <Label>Amount</Label>
                  <CurrencyAmountInput
                    v-model="incomeAmount"
                    :minor-unit="incomeMinorUnit"
                    :currency-code="incomeCurrency"
                  />
                </div>
                <div class="grid gap-2">
                  <Label for="income-desc">Description</Label>
                  <Input id="income-desc" v-model="incomeDescription" placeholder="Income source" />
                </div>
              </TabsContent>

              <!-- Transfer Tab -->
              <TabsContent value="transfer" class="space-y-4 pt-4">
                <div
                  v-if="modeStore.isHousehold"
                  class="rounded-md border border-blue-200 bg-blue-50 px-3 py-2 text-xs text-blue-700 dark:border-blue-900 dark:bg-blue-950 dark:text-blue-300"
                >
                  Transfers between household accounts are allowed. You cannot post expenses to
                  other users' accounts.
                </div>
                <div class="grid gap-2">
                  <Label for="transfer-date">Date</Label>
                  <Input id="transfer-date" v-model="transferDate" type="date" />
                </div>
                <div class="grid gap-2">
                  <Label>From account (Asset)</Label>
                  <AccountSelector
                    v-model="transferFrom"
                    :allowed-types="['ASSET']"
                    placeholder="Select source account"
                  />
                </div>
                <div class="grid gap-2">
                  <Label>To account (Asset)</Label>
                  <AccountSelector
                    v-model="transferTo"
                    :allowed-types="['ASSET']"
                    :currency="transferCurrency || undefined"
                    placeholder="Select destination account"
                  />
                </div>
                <div class="grid gap-2">
                  <Label>Amount</Label>
                  <CurrencyAmountInput
                    v-model="transferAmount"
                    :minor-unit="transferMinorUnit"
                    :currency-code="transferCurrency"
                  />
                </div>
                <div class="grid gap-2">
                  <Label for="transfer-desc">Description</Label>
                  <Input
                    id="transfer-desc"
                    v-model="transferDescription"
                    placeholder="Transfer reason"
                  />
                </div>
                <div
                  v-if="isCrossUserTransfer"
                  class="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-300"
                >
                  This is a cross-user transfer between household members.
                </div>
              </TabsContent>
            </Tabs>

            <p v-if="submitError" class="text-sm text-red-600">{{ submitError }}</p>

            <DialogFooter>
              <Button :disabled="isSubmitting" @click="submitTransaction">
                {{ isSubmitting ? 'Creating...' : 'Create Transaction' }}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </CardHeader>

      <CardContent class="flex flex-1 flex-col min-h-0 pb-6">
        <!-- Filters -->
        <div class="mb-4 flex flex-wrap items-end gap-3">
          <div class="grid gap-1">
            <Label class="text-xs">Date range</Label>
            <DateRangePicker
              :from="filterDateFrom"
              :to="filterDateTo"
              @update:from="filterDateFrom = $event"
              @update:to="filterDateTo = $event"
            />
          </div>
          <div class="grid gap-1">
            <Label class="text-xs">Account</Label>
            <div class="w-48">
              <AccountSelector v-model="filterAccountId" placeholder="All accounts" />
            </div>
          </div>
          <div class="grid gap-1">
            <Label class="text-xs">Category</Label>
            <div class="w-48">
              <CategoryTagSelector v-model="filterCategoryId" />
            </div>
          </div>
          <div class="grid gap-1">
            <Label class="text-xs">Search</Label>
            <Input
              v-model="filterSearch"
              type="text"
              placeholder="Search descriptions..."
              class="h-8 w-48 text-xs"
            />
          </div>
        </div>

        <!-- Loading state -->
        <div
          v-if="ledgerStore.isLoading"
          class="flex flex-1 items-center justify-center text-sm text-muted-foreground"
        >
          Loading transactions...
        </div>

        <!-- Empty state -->
        <div
          v-else-if="filteredTransactions.length === 0"
          class="flex flex-1 items-center justify-center text-sm text-muted-foreground"
        >
          No transactions found. Create your first transaction to get started.
        </div>

        <!-- Error state (only reached when transactions exist but a reload failed) -->
        <div
          v-else-if="ledgerStore.error"
          class="flex flex-1 items-center justify-center text-sm text-red-600"
        >
          {{ ledgerStore.error }}
        </div>

        <!-- Transaction table -->
        <DataTable
          v-else
          :table="table"
          sticky-header
          clickable
          class="flex-1 min-h-0"
          empty-text="No transactions found. Create your first transaction to get started."
          :pagination="{
            page: ledgerStore.currentPage,
            pageSize: ledgerStore.pageSize,
            totalPages: ledgerStore.totalPages,
            totalElements: ledgerStore.totalElements,
          }"
          @row-click="(row) => row.toggleExpanded()"
          @update:page="goToPage"
          @update:page-size="changePageSize"
        >
          <template #expanded="{ row }">
            <div class="space-y-1 pl-6">
              <div
                v-for="split in orderedSplits(row.original)"
                :key="split.id ?? split.accountId"
                class="flex items-center justify-between text-xs"
              >
                <div class="flex items-center gap-2">
                  <Badge variant="outline" class="text-[10px]">{{ split.side }}</Badge>
                  <span>{{ splitLabel(split) }}</span>
                </div>
                <span class="font-mono">{{ splitAmount(split, row.original.currency) }}</span>
              </div>
            </div>
          </template>
        </DataTable>
      </CardContent>
    </Card>
  </section>
</template>
