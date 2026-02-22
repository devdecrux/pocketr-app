<script setup lang="ts">
import { HTTPError } from 'ky'
import { computed, h, onMounted, ref, watch } from 'vue'
import { createColumnHelper, FlexRender, getCoreRowModel, getExpandedRowModel, useVueTable } from '@tanstack/vue-table'
import { createTxn } from '@/api/ledger'
import { useAccountStore } from '@/stores/account'
import { useCategoryStore } from '@/stores/category'
import { useCurrencyStore } from '@/stores/currency'
import { useHouseholdStore } from '@/stores/household'
import { useLedgerStore } from '@/stores/ledger'
import { useModeStore } from '@/stores/mode'
import type { CreateTxnRequest, LedgerSplit, LedgerTxn } from '@/types/ledger'
import { formatMinor } from '@/utils/money'
import AccountSelector from '@/components/AccountSelector.vue'
import CategoryTagSelector from '@/components/CategoryTagSelector.vue'
import DateRangePicker from '@/components/DateRangePicker.vue'
import CurrencyAmountInput from '@/components/CurrencyAmountInput.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ArrowLeftRight, ChevronDown, ChevronLeft, ChevronRight, Plus } from 'lucide-vue-next'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { initialsFromName } from '@/utils/initials'

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
const expenseMemo = ref('')

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

const PAGE_SIZE_OPTIONS = [5, 10, 15, 25, 50, 100]

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

async function onPageSizeChange(val: string): Promise<void> {
  ledgerStore.pageSize = Number(val)
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

// Reset form
function resetForms(): void {
  expenseDate.value = todayString()
  expensePayFrom.value = ''
  expenseAccount.value = ''
  expenseAmount.value = 0
  expenseCategory.value = null
  expenseDescription.value = ''
  expenseMemo.value = ''

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

  let request: CreateTxnRequest | null = null

  if (activeTab.value === 'expense') {
    if (
      !expensePayFrom.value ||
      !expenseAccount.value ||
      expenseAmount.value <= 0 ||
      !expenseDescription.value.trim()
    ) {
      submitError.value = 'Please fill in all required fields and enter a positive amount.'
      return
    }
    request = {
      mode: modeStore.modeParam,
      householdId: modeStore.householdId,
      txnDate: expenseDate.value,
      currency: expenseCurrency.value,
      description: expenseDescription.value.trim(),
      splits: [
        {
          accountId: expensePayFrom.value,
          side: 'CREDIT',
          amountMinor: expenseAmount.value,
        },
        {
          accountId: expenseAccount.value,
          side: 'DEBIT',
          amountMinor: expenseAmount.value,
          categoryTagId: expenseCategory.value,
          memo: expenseMemo.value.trim() || null,
        },
      ],
    }
  } else if (activeTab.value === 'income') {
    if (
      !incomeDeposit.value ||
      !incomeAccount.value ||
      incomeAmount.value <= 0 ||
      !incomeDescription.value.trim()
    ) {
      submitError.value = 'Please fill in all required fields and enter a positive amount.'
      return
    }
    request = {
      mode: modeStore.modeParam,
      householdId: modeStore.householdId,
      txnDate: incomeDate.value,
      currency: incomeCurrency.value,
      description: incomeDescription.value.trim(),
      splits: [
        {
          accountId: incomeDeposit.value,
          side: 'DEBIT',
          amountMinor: incomeAmount.value,
        },
        {
          accountId: incomeAccount.value,
          side: 'CREDIT',
          amountMinor: incomeAmount.value,
        },
      ],
    }
  } else if (activeTab.value === 'transfer') {
    if (
      !transferFrom.value ||
      !transferTo.value ||
      transferAmount.value <= 0 ||
      !transferDescription.value.trim()
    ) {
      submitError.value = 'Please fill in all required fields and enter a positive amount.'
      return
    }
    request = {
      mode: modeStore.modeParam,
      householdId: modeStore.householdId,
      txnDate: transferDate.value,
      currency: transferCurrency.value,
      description: transferDescription.value.trim(),
      splits: [
        {
          accountId: transferFrom.value,
          side: 'CREDIT',
          amountMinor: transferAmount.value,
        },
        {
          accountId: transferTo.value,
          side: 'DEBIT',
          amountMinor: transferAmount.value,
        },
      ],
    }
  }

  if (!request) return

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
                <div class="grid gap-2">
                  <Label for="expense-memo">Memo (optional)</Label>
                  <Input id="expense-memo" v-model="expenseMemo" placeholder="Additional notes" />
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
        <div v-else class="flex-1 min-h-0 overflow-auto rounded-md border">
          <table class="w-full text-sm">
            <thead>
              <tr
                v-for="headerGroup in table.getHeaderGroups()"
                :key="headerGroup.id"
                class="border-b bg-muted/50"
              >
                <th
                  v-for="header in headerGroup.headers"
                  :key="header.id"
                  class="px-4 py-2 text-right font-medium text-muted-foreground"
                >
                  <FlexRender
                    v-if="!header.isPlaceholder"
                    :render="header.column.columnDef.header"
                    :props="header.getContext()"
                  />
                </th>
              </tr>
            </thead>
            <tbody>
              <template v-for="row in table.getRowModel().rows" :key="row.id">
                <tr
                  class="cursor-pointer border-b last:border-0 transition-colors hover:bg-muted/50"
                  @click="row.toggleExpanded()"
                >
                  <td
                    v-for="cell in row.getVisibleCells()"
                    :key="cell.id"
                    class="px-4 py-2 text-right"
                    :class="(cell.column.columnDef.meta as any)?.tdClass"
                  >
                    <FlexRender :render="cell.column.columnDef.cell" :props="cell.getContext()" />
                  </td>
                </tr>
                <!-- Expanded splits row -->
                <tr v-if="row.getIsExpanded()">
                  <td :colspan="row.getVisibleCells().length" class="bg-muted/30 px-4 py-3">
                    <div class="space-y-1 pl-6">
                      <div
                        v-for="split in row.original.splits"
                        :key="split.id ?? split.accountId"
                        class="flex items-center justify-between text-xs"
                      >
                        <div class="flex items-center gap-2">
                          <Badge variant="outline" class="text-[10px]">
                            {{ split.side }}
                          </Badge>
                          <span>{{ splitLabel(split) }}</span>
                        </div>
                        <span class="font-mono">
                          {{ splitAmount(split, row.original.currency) }}
                        </span>
                      </div>
                      <p
                        v-for="split in row.original.splits.filter((s) => s.memo)"
                        :key="`memo-${split.id}`"
                        class="text-[10px] text-muted-foreground italic"
                      >
                        {{ split.memo }}
                      </p>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
        <!-- Pagination controls -->
        <div
          v-if="!ledgerStore.isLoading && ledgerStore.totalPages > 0"
          class="mt-4 flex items-center justify-between gap-4"
        >
          <div class="flex items-center gap-2">
            <span class="text-xs text-muted-foreground">Rows per page</span>
            <Select
              :model-value="String(ledgerStore.pageSize)"
              @update:model-value="onPageSizeChange"
            >
              <SelectTrigger class="h-8 w-20 text-xs">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem
                  v-for="opt in PAGE_SIZE_OPTIONS"
                  :key="opt"
                  :value="String(opt)"
                  class="text-xs"
                >
                  {{ opt }}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div class="flex items-center gap-3">
            <span class="text-xs text-muted-foreground">
              Page {{ ledgerStore.currentPage + 1 }} of {{ ledgerStore.totalPages }} &middot;
              {{ ledgerStore.totalElements }} total
            </span>
            <div class="flex gap-1">
              <Button
                variant="outline"
                size="icon"
                class="h-8 w-8"
                :disabled="ledgerStore.currentPage === 0"
                @click="goToPage(ledgerStore.currentPage - 1)"
              >
                <ChevronLeft class="size-4" />
              </Button>
              <Button
                variant="outline"
                size="icon"
                class="h-8 w-8"
                :disabled="ledgerStore.currentPage >= ledgerStore.totalPages - 1"
                @click="goToPage(ledgerStore.currentPage + 1)"
              >
                <ChevronRight class="size-4" />
              </Button>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  </section>
</template>
