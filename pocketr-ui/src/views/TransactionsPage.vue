<script setup lang="ts">
import { HTTPError } from 'ky'
import { computed, h, onMounted, ref, watch } from 'vue'
import {
  createColumnHelper,
  getCoreRowModel,
  getExpandedRowModel,
  useVueTable,
} from '@tanstack/vue-table'
import { createTxn, deleteTxn } from '@/api/ledger'
import { useAccountStore } from '@/stores/account'
import { useCategoryStore } from '@/stores/category'
import { useCurrencyStore } from '@/stores/currency'
import { useHouseholdStore } from '@/stores/household'
import { useLedgerStore } from '@/stores/ledger'
import { useModeStore } from '@/stores/mode'
import type { LedgerSplit, LedgerTxn } from '@/types/ledger'
import {
  debtPaymentStrategy,
  expenseStrategy,
  incomeStrategy,
  transferStrategy,
} from '@/utils/txnStrategies'
import { getTxnPresentation } from '@/utils/txnPresentation'
import { formatMinor } from '@/utils/money'
import AccountSelector from '@/components/AccountSelector.vue'
import CategoryTagSelector from '@/components/CategoryTagSelector.vue'
import DateRangePicker from '@/components/DateRangePicker.vue'
import CurrencyAmountInput from '@/components/CurrencyAmountInput.vue'
import {
  AppCardHeader,
  AppDialogContent,
  AppFilterBar,
  AppFormField,
  AppNotice,
  AppStateMessage,
  AppStatusText,
} from '@/components/app'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Dialog, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  ArrowLeftRight,
  ChevronDown,
  ChevronRight,
  Minus,
  Plus,
  Trash2,
  TrendingDown,
} from 'lucide-vue-next'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { initialsFromName } from '@/utils/initials'
import DataTable from '@/components/DataTable.vue'
import { translate } from '@/i18n/translate'

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
const deleteError = ref('')
const deletingTxnId = ref<string | null>(null)

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

// Debt payment form
const debtPaymentDate = ref(todayString())
const debtPaymentPayFrom = ref('')
const debtPaymentLiabilityAccount = ref('')
const debtPaymentAmount = ref(0)
const debtPaymentDescription = ref('')

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

// Derived currency for debt payment (from asset account)
const debtPaymentCurrency = computed(() => {
  const acc = accountStore.accountMap.get(debtPaymentPayFrom.value)
  return acc?.currency ?? ''
})

const debtPaymentMinorUnit = computed(() => currencyStore.getMinorUnit(debtPaymentCurrency.value))

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

  if (activeTab.value === 'debt-payment') {
    const fields = {
      date: debtPaymentDate.value,
      payFrom: debtPaymentPayFrom.value,
      liabilityAccount: debtPaymentLiabilityAccount.value,
      amount: debtPaymentAmount.value,
      currency: debtPaymentCurrency.value,
      description: debtPaymentDescription.value,
    }
    const error = debtPaymentStrategy.validate(fields)
    return error
      ? { error }
      : { error: null, request: debtPaymentStrategy.buildRequest(ctx, fields) }
  }

  return { error: translate('validation.transactions.unknownTab') }
}

const isFormValid = computed(() => getStrategyResult().error === null)

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

function txnPresentation(txn: LedgerTxn) {
  return getTxnPresentation(txn.txnKind)
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
      header: translate('common.table.date'),
      meta: { tdClass: 'whitespace-nowrap' },
    }),
    columnHelper.accessor('description', {
      header: translate('common.table.description'),
    }),
    columnHelper.display({
      id: 'kind',
      header: translate('common.table.type'),
      cell: ({ row }) => {
        const presentation = txnPresentation(row.original)
        return h(
          Badge,
          { variant: presentation.badgeVariant, class: 'text-xs' },
          () => presentation.label,
        )
      },
    }),
    columnHelper.display({
      id: 'categories',
      header: translate('common.table.category'),
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
      header: translate('common.table.amount'),
      cell: ({ row }) => {
        const presentation = txnPresentation(row.original)
        return h(
          'span',
          {
            class: `inline-flex items-center justify-end gap-1 whitespace-nowrap font-medium ${presentation.amountClass}`,
          },
          [
            presentation.indicator === 'transfer'
              ? h(ArrowLeftRight, { class: 'size-3' })
              : h('span', {}, presentation.indicator === 'minus' ? '-' : '+'),
            txnDisplayAmount(row.original),
          ],
        )
      },
    }),
  ]

  if (modeStore.isHousehold) {
    cols.push(
      columnHelper.display({
        id: 'member',
        header: translate('common.table.member'),
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

  cols.push(
    columnHelper.display({
      id: 'actions',
      header: '',
      cell: ({ row }) =>
        h(
          Button,
          {
            variant: 'ghost',
            size: 'icon',
            'data-table-action': 'delete',
            disabled: deletingTxnId.value === row.original.id,
            onClick: (event: MouseEvent) => {
              event.stopPropagation()
              void deleteTransaction(row.original)
            },
          },
          () => h(Trash2, { class: 'size-4' }),
        ),
    }),
  )

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

  debtPaymentDate.value = todayString()
  debtPaymentPayFrom.value = ''
  debtPaymentLiabilityAccount.value = ''
  debtPaymentAmount.value = 0
  debtPaymentDescription.value = ''

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
      submitError.value = payload?.message?.trim() || translate('errors.transactions.create')
    } else {
      submitError.value = translate('errors.transactions.create')
    }
  } finally {
    isSubmitting.value = false
  }
}

async function deleteTransaction(txn: LedgerTxn): Promise<void> {
  deleteError.value = ''

  const confirmed = window.confirm(
    translate('views.transactions.confirmDelete', { description: txn.description }),
  )
  if (!confirmed) return

  deletingTxnId.value = txn.id

  try {
    await deleteTxn(txn.id)
    await loadData()
  } catch (error: unknown) {
    if (error instanceof HTTPError) {
      const payload = await error.response.json<{ message?: string }>().catch(() => null)
      deleteError.value = payload?.message?.trim() || translate('errors.transactions.delete')
    } else {
      deleteError.value = translate('errors.transactions.delete')
    }
  } finally {
    deletingTxnId.value = null
  }
}
</script>

<template>
  <section class="flex flex-col gap-4">
    <Card>
      <AppCardHeader :title="$t('views.transactions.title')" title-class="text-2xl">
        <Dialog v-model:open="dialogOpen">
          <DialogTrigger as-child>
            <Button size="sm" @click="resetForms">
              <Plus class="mr-1 size-4" />
              {{ $t('views.transactions.actions.new') }}
            </Button>
          </DialogTrigger>
          <AppDialogContent
            :title="$t('views.transactions.create.title')"
            :description="$t('views.transactions.create.description')"
            class="max-w-lg"
          >
            <Tabs v-model="activeTab" class="w-full">
              <TabsList>
                <TabsTrigger value="expense">
                  <Minus class="size-4 shrink-0" />
                  <span class="text-center leading-tight">{{
                    $t('views.transactions.create.tabs.expense')
                  }}</span>
                </TabsTrigger>
                <TabsTrigger value="income">
                  <Plus class="size-4 shrink-0" />
                  <span class="text-center leading-tight">{{
                    $t('views.transactions.create.tabs.income')
                  }}</span>
                </TabsTrigger>
                <TabsTrigger value="transfer">
                  <ArrowLeftRight class="size-4 shrink-0" />
                  <span class="text-center leading-tight">{{
                    $t('views.transactions.create.tabs.transfer')
                  }}</span>
                </TabsTrigger>
                <TabsTrigger value="debt-payment">
                  <TrendingDown class="size-4 shrink-0" />
                  <span class="text-center leading-tight">{{
                    $t('views.transactions.create.tabs.debtPayment')
                  }}</span>
                </TabsTrigger>
              </TabsList>

              <!-- Expense Tab -->
              <TabsContent value="expense" class="space-y-4 pt-4">
                <div class="grid grid-cols-2 gap-4">
                  <AppFormField :label="$t('common.fields.date')" control-id="expense-date">
                    <Input id="expense-date" v-model="expenseDate" type="date" />
                  </AppFormField>
                  <AppFormField :label="$t('common.fields.amount')">
                    <CurrencyAmountInput
                      v-model="expenseAmount"
                      :minor-unit="expenseMinorUnit"
                      :currency-code="expenseCurrency"
                    />
                  </AppFormField>
                </div>
                <AppFormField :label="$t('views.transactions.fields.payFromAssetOrLiability')">
                  <AccountSelector
                    v-model="expensePayFrom"
                    :allowed-types="['ASSET', 'LIABILITY']"
                    :placeholder="$t('views.transactions.placeholders.selectPayFromAccount')"
                  />
                </AppFormField>
                <AppFormField :label="$t('views.transactions.fields.expenseAccount')">
                  <AccountSelector
                    v-model="expenseAccount"
                    :allowed-types="['EXPENSE']"
                    :placeholder="$t('views.transactions.placeholders.selectExpenseAccount')"
                  />
                </AppFormField>
                <AppFormField :label="$t('common.fields.category')">
                  <CategoryTagSelector v-model="expenseCategory" />
                </AppFormField>
                <AppFormField :label="$t('common.fields.description')" control-id="expense-desc">
                  <Input
                    id="expense-desc"
                    v-model="expenseDescription"
                    :placeholder="$t('views.transactions.placeholders.whatWasThisFor')"
                  />
                </AppFormField>
              </TabsContent>

              <!-- Income Tab -->
              <TabsContent value="income" class="space-y-4 pt-4">
                <div class="grid grid-cols-2 gap-4">
                  <AppFormField :label="$t('common.fields.date')" control-id="income-date">
                    <Input id="income-date" v-model="incomeDate" type="date" />
                  </AppFormField>
                  <AppFormField :label="$t('common.fields.amount')">
                    <CurrencyAmountInput
                      v-model="incomeAmount"
                      :minor-unit="incomeMinorUnit"
                      :currency-code="incomeCurrency"
                    />
                  </AppFormField>
                </div>
                <AppFormField :label="$t('views.transactions.fields.depositTo')">
                  <AccountSelector
                    v-model="incomeDeposit"
                    :allowed-types="['ASSET']"
                    :placeholder="$t('views.transactions.placeholders.selectDepositAccount')"
                  />
                </AppFormField>
                <AppFormField :label="$t('views.transactions.fields.incomeAccount')">
                  <AccountSelector
                    v-model="incomeAccount"
                    :allowed-types="['INCOME']"
                    :placeholder="$t('views.transactions.placeholders.selectIncomeAccount')"
                  />
                </AppFormField>
                <AppFormField :label="$t('common.fields.description')" control-id="income-desc">
                  <Input
                    id="income-desc"
                    v-model="incomeDescription"
                    :placeholder="$t('views.transactions.placeholders.incomeSource')"
                  />
                </AppFormField>
              </TabsContent>

              <!-- Transfer Tab -->
              <TabsContent value="transfer" class="space-y-4 pt-4">
                <AppNotice v-if="modeStore.isHousehold">
                  {{ $t('views.transactions.notices.householdTransfers') }}
                </AppNotice>
                <div class="grid grid-cols-2 gap-4">
                  <AppFormField :label="$t('common.fields.date')" control-id="transfer-date">
                    <Input id="transfer-date" v-model="transferDate" type="date" />
                  </AppFormField>
                  <AppFormField :label="$t('common.fields.amount')">
                    <CurrencyAmountInput
                      v-model="transferAmount"
                      :minor-unit="transferMinorUnit"
                      :currency-code="transferCurrency"
                    />
                  </AppFormField>
                </div>
                <AppFormField :label="$t('views.transactions.fields.fromAccount')">
                  <AccountSelector
                    v-model="transferFrom"
                    :allowed-types="['ASSET']"
                    :placeholder="$t('views.transactions.placeholders.selectSourceAccount')"
                  />
                </AppFormField>
                <AppFormField :label="$t('views.transactions.fields.toAccount')">
                  <AccountSelector
                    v-model="transferTo"
                    :allowed-types="['ASSET']"
                    :currency="transferCurrency || undefined"
                    :placeholder="$t('views.transactions.placeholders.selectDestinationAccount')"
                  />
                </AppFormField>
                <AppFormField :label="$t('common.fields.description')" control-id="transfer-desc">
                  <Input
                    id="transfer-desc"
                    v-model="transferDescription"
                    :placeholder="$t('views.transactions.placeholders.transferReason')"
                  />
                </AppFormField>
                <AppNotice v-if="isCrossUserTransfer" variant="warning">
                  {{ $t('views.transactions.notices.crossUserTransfer') }}
                </AppNotice>
              </TabsContent>

              <!-- Debt Payment Tab -->
              <TabsContent value="debt-payment" class="space-y-4 pt-4">
                <div class="grid grid-cols-2 gap-4">
                  <AppFormField :label="$t('common.fields.date')" control-id="debt-payment-date">
                    <Input id="debt-payment-date" v-model="debtPaymentDate" type="date" />
                  </AppFormField>
                  <AppFormField :label="$t('common.fields.amount')">
                    <CurrencyAmountInput
                      v-model="debtPaymentAmount"
                      :minor-unit="debtPaymentMinorUnit"
                      :currency-code="debtPaymentCurrency"
                    />
                  </AppFormField>
                </div>
                <AppFormField :label="$t('views.transactions.fields.payFromAsset')">
                  <AccountSelector
                    v-model="debtPaymentPayFrom"
                    :allowed-types="['ASSET']"
                    :placeholder="$t('views.transactions.placeholders.selectAssetAccount')"
                  />
                </AppFormField>
                <AppFormField :label="$t('views.transactions.fields.debtPaymentLiabilityAccount')">
                  <AccountSelector
                    v-model="debtPaymentLiabilityAccount"
                    :allowed-types="['LIABILITY']"
                    :currency="debtPaymentCurrency || undefined"
                    :placeholder="$t('views.transactions.placeholders.selectLiabilityAccount')"
                  />
                </AppFormField>
                <AppFormField
                  :label="$t('common.fields.description')"
                  control-id="debt-payment-desc"
                >
                  <Input
                    id="debt-payment-desc"
                    v-model="debtPaymentDescription"
                    :placeholder="$t('views.transactions.placeholders.debtPaymentNote')"
                  />
                </AppFormField>
              </TabsContent>
            </Tabs>

            <AppStatusText v-if="submitError">{{ submitError }}</AppStatusText>

            <template #footer>
              <Button :disabled="isSubmitting || !isFormValid" @click="submitTransaction">
                {{
                  isSubmitting
                    ? $t('common.feedback.creating')
                    : $t('views.transactions.create.title')
                }}
              </Button>
            </template>
          </AppDialogContent>
        </Dialog>
      </AppCardHeader>

      <CardContent class="flex flex-col pb-6">
        <!-- Filters -->
        <AppFilterBar>
          <AppFormField :label="$t('common.fields.dateRange')" class="gap-1" label-class="text-xs">
            <DateRangePicker
              :from="filterDateFrom"
              :to="filterDateTo"
              @update:from="filterDateFrom = $event"
              @update:to="filterDateTo = $event"
            />
          </AppFormField>
          <AppFormField :label="$t('common.fields.account')" class="gap-1" label-class="text-xs">
            <div class="w-48">
              <AccountSelector
                v-model="filterAccountId"
                :placeholder="$t('views.transactions.placeholders.allAccounts')"
              />
            </div>
          </AppFormField>
          <AppFormField :label="$t('common.fields.category')" class="gap-1" label-class="text-xs">
            <div class="w-48">
              <CategoryTagSelector v-model="filterCategoryId" />
            </div>
          </AppFormField>
          <AppFormField :label="$t('common.fields.search')" class="gap-1" label-class="text-xs">
            <Input
              v-model="filterSearch"
              type="text"
              :placeholder="$t('views.transactions.placeholders.searchDescriptions')"
              class="h-8 w-48 text-xs"
            />
          </AppFormField>
        </AppFilterBar>

        <!-- Loading state -->
        <AppStateMessage v-if="ledgerStore.isLoading" center>
          {{ $t('views.transactions.loading') }}
        </AppStateMessage>

        <!-- Empty state -->
        <AppStateMessage v-else-if="filteredTransactions.length === 0" center>
          {{ $t('views.transactions.empty') }}
        </AppStateMessage>

        <!-- Error state (only reached when transactions exist but a reload failed) -->
        <AppStateMessage v-else-if="ledgerStore.error" variant="error" center>
          {{ ledgerStore.error }}
        </AppStateMessage>

        <!-- Transaction table -->
        <DataTable
          v-else
          :table="table"
          sticky-header
          clickable
          :empty-text="$t('views.transactions.empty')"
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
                  <Badge variant="outline" class="text-[10px]">{{
                    $t(`display.splitSides.${split.side}`)
                  }}</Badge>
                  <span>{{ splitLabel(split) }}</span>
                </div>
                <span class="font-mono">{{ splitAmount(split, row.original.currency) }}</span>
              </div>
            </div>
          </template>
        </DataTable>

        <AppStatusText v-if="deleteError" class="mt-3">{{ deleteError }}</AppStatusText>
      </CardContent>
    </Card>
  </section>
</template>
