<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import { type ColumnDef, getCoreRowModel, useVueTable } from '@tanstack/vue-table'
import { CreditCard, Pencil, Plus, ShoppingCart, TrendingUp, Wallet } from 'lucide-vue-next'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useAccountStore } from '@/stores/account'
import { useCurrencyStore } from '@/stores/currency'
import { useModeStore } from '@/stores/mode'
import { useHouseholdStore } from '@/stores/household'
import { createAccount, updateAccount } from '@/api/accounts'
import { getAccountBalances } from '@/api/ledger'
import { formatMinor } from '@/utils/money'
import type { Account, AccountType, CreateAccountRequest } from '@/types/ledger'
import CurrencyAmountInput from '@/components/CurrencyAmountInput.vue'
import DataTable from '@/components/DataTable.vue'

const accountStore = useAccountStore()
const currencyStore = useCurrencyStore()
const modeStore = useModeStore()
const householdStore = useHouseholdStore()

const balances = ref<Map<string, number>>(new Map())
const typeFilter = ref<string>('ALL')
const currencyFilter = ref<string>('ALL')
const createDialogOpen = ref(false)
const isCreating = ref(false)
const createError = ref('')
const renameError = ref('')

const newAccount = ref({
  name: '',
  type: 'ASSET' as AccountType,
  currency: 'EUR',
})
const openingBalanceMinor = ref(0)
const openingBalanceDate = ref(todayString())

const accountTypes: AccountType[] = ['ASSET', 'LIABILITY', 'INCOME', 'EXPENSE']

const filteredAccounts = computed(() => {
  let list = accountStore.activeAccounts
  if (typeFilter.value !== 'ALL') {
    list = list.filter((a) => a.type === typeFilter.value)
  }
  if (currencyFilter.value !== 'ALL') {
    list = list.filter((a) => a.currency === currencyFilter.value)
  }
  return list
})

const availableCurrencies = computed(() => {
  const codes = new Set(accountStore.accounts.map((a) => a.currency))
  return [...codes].sort()
})

const openingBalanceMinorUnit = computed(() =>
  currencyStore.getMinorUnit(newAccount.value.currency),
)
const supportsOpeningAmount = computed(
  () => newAccount.value.type === 'ASSET' || newAccount.value.type === 'LIABILITY',
)

const sharedAccountIds = computed(() => {
  const ids = new Set<string>()
  for (const share of householdStore.sharedAccounts) {
    ids.add(share.accountId)
  }
  return ids
})

const columns = computed<ColumnDef<Account>[]>(() => {
  const cols: ColumnDef<Account>[] = [
    {
      accessorKey: 'name',
      header: 'Name',
      cell: ({ row }) => row.original.name,
    },
    {
      accessorKey: 'type',
      header: 'Type',
      cell: ({ row }) => {
        const badges: Record<AccountType, string> = {
          ASSET: 'default',
          LIABILITY: 'destructive',
          INCOME: 'secondary',
          EXPENSE: 'outline',
          EQUITY: 'secondary',
        }
        return h(
          Badge,
          { variant: badges[row.original.type] as 'default' },
          () => row.original.type,
        )
      },
    },
    {
      accessorKey: 'currency',
      header: 'Currency',
    },
    {
      id: 'balance',
      header: 'Balance',
      cell: ({ row }) => {
        const bal = balances.value.get(row.original.id)
        if (bal === undefined) return '...'
        const minorUnit = currencyStore.getMinorUnit(row.original.currency)
        return formatMinor(bal, row.original.currency, minorUnit)
      },
    },
  ]

  if (modeStore.isHousehold) {
    cols.push({
      id: 'shared',
      header: 'Shared',
      cell: ({ row }) => {
        if (sharedAccountIds.value.has(row.original.id)) {
          return h(Badge, { variant: 'secondary' }, () => 'Shared')
        }
        return ''
      },
    })
  }

  cols.push({
    id: 'actions',
    header: '',
    meta: { tdClass: 'py-0' },
    cell: ({ row }) => {
      return h(
        Button,
        {
          variant: 'ghost',
          size: 'icon',
          class: 'size-8',
          onClick: () => startRename(row.original),
        },
        () => h(Pencil, { class: 'size-4' }),
      )
    },
  })

  return cols
})

const table = useVueTable({
  get data() {
    return filteredAccounts.value
  },
  get columns() {
    return columns.value
  },
  getCoreRowModel: getCoreRowModel(),
})

async function loadBalances(): Promise<void> {
  balances.value = new Map()
  const accountIds = modeStore.isHousehold
    ? accountStore.activeAccounts
        .map((account) => account.id)
        .filter((accountId) => sharedAccountIds.value.has(accountId))
    : accountStore.activeAccounts.map((account) => account.id)

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
    // skip failed balance loads
  }
}

async function loadAll(): Promise<void> {
  await Promise.all([accountStore.load(), currencyStore.load()])
  if (modeStore.isHousehold && modeStore.householdId) {
    await householdStore.loadSharedAccounts(modeStore.householdId)
  }
  await loadBalances()
}

onMounted(loadAll)

watch(() => modeStore.viewMode, loadAll, { deep: true })

watch(
  () => newAccount.value.type,
  (type) => {
    if (type === 'LIABILITY' && openingBalanceMinor.value < 0) {
      openingBalanceMinor.value = Math.abs(openingBalanceMinor.value)
    }
  },
)

const renameDialog = ref(false)
const renameTarget = ref<Account | null>(null)
const renameName = ref('')

function startRename(account: Account): void {
  renameTarget.value = account
  renameName.value = account.name
  renameError.value = ''
  renameDialog.value = true
}

async function submitRename(): Promise<void> {
  if (!renameTarget.value) return
  renameError.value = ''
  try {
    await updateAccount(renameTarget.value.id, { name: renameName.value })
    renameDialog.value = false
    await accountStore.load()
  } catch {
    renameError.value = 'Failed to rename account.'
  }
}

async function submitCreate(): Promise<void> {
  isCreating.value = true
  createError.value = ''
  try {
    const payload: CreateAccountRequest = {
      name: newAccount.value.name,
      type: newAccount.value.type,
      currency: newAccount.value.currency,
    }
    if (supportsOpeningAmount.value && openingBalanceMinor.value !== 0) {
      payload.openingBalanceMinor = openingBalanceMinor.value
      payload.openingBalanceDate = openingBalanceDate.value || todayString()
    }

    await createAccount(payload)
    createDialogOpen.value = false
    newAccount.value = { name: '', type: 'ASSET', currency: 'EUR' }
    openingBalanceMinor.value = 0
    openingBalanceDate.value = todayString()
    await loadAll()
  } catch {
    createError.value = 'Failed to create account.'
  } finally {
    isCreating.value = false
  }
}

function todayString(): string {
  return new Date().toISOString().slice(0, 10)
}
</script>

<template>
  <section class="flex flex-col gap-4">
    <Card>
      <CardHeader class="flex flex-row items-center justify-between">
        <CardTitle class="text-2xl">Accounts</CardTitle>
        <Dialog v-model:open="createDialogOpen">
          <DialogTrigger as-child>
            <Button size="sm">
              <Plus class="size-4" />
              New Account
            </Button>
          </DialogTrigger>
          <DialogContent class="max-w-lg">
            <DialogHeader>
              <DialogTitle>Create Account</DialogTitle>
              <DialogDescription>Add a new account to your ledger.</DialogDescription>
            </DialogHeader>

            <Tabs v-model="newAccount.type" class="w-full">
              <TabsList
                class="grid h-auto w-full grid-cols-4 gap-1.5 rounded-xl bg-primary/20 p-1.5 dark:bg-primary/10"
              >
                <TabsTrigger
                  value="ASSET"
                  class="h-auto flex flex-col items-center justify-center gap-1 rounded-lg px-2 py-2.5 text-[11px] font-medium text-foreground/70 transition-colors hover:bg-primary/40 hover:text-foreground data-[state=active]:bg-primary data-[state=active]:text-primary-foreground data-[state=active]:shadow-sm dark:hover:bg-primary/20 dark:data-[state=active]:bg-primary dark:data-[state=active]:text-primary-foreground"
                >
                  <Wallet class="size-4 shrink-0" />
                  <span class="text-center leading-tight">Asset</span>
                </TabsTrigger>
                <TabsTrigger
                  value="EXPENSE"
                  class="h-auto flex flex-col items-center justify-center gap-1 rounded-lg px-2 py-2.5 text-[11px] font-medium text-foreground/70 transition-colors hover:bg-primary/40 hover:text-foreground data-[state=active]:bg-primary data-[state=active]:text-primary-foreground data-[state=active]:shadow-sm dark:hover:bg-primary/20 dark:data-[state=active]:bg-primary dark:data-[state=active]:text-primary-foreground"
                >
                  <ShoppingCart class="size-4 shrink-0" />
                  <span class="text-center leading-tight">Expense</span>
                </TabsTrigger>
                <TabsTrigger
                  value="INCOME"
                  class="h-auto flex flex-col items-center justify-center gap-1 rounded-lg px-2 py-2.5 text-[11px] font-medium text-foreground/70 transition-colors hover:bg-primary/40 hover:text-foreground data-[state=active]:bg-primary data-[state=active]:text-primary-foreground data-[state=active]:shadow-sm dark:hover:bg-primary/20 dark:data-[state=active]:bg-primary dark:data-[state=active]:text-primary-foreground"
                >
                  <TrendingUp class="size-4 shrink-0" />
                  <span class="text-center leading-tight">Income</span>
                </TabsTrigger>
                <TabsTrigger
                  value="LIABILITY"
                  class="h-auto flex flex-col items-center justify-center gap-1 rounded-lg px-2 py-2.5 text-[11px] font-medium text-foreground/70 transition-colors hover:bg-primary/40 hover:text-foreground data-[state=active]:bg-primary data-[state=active]:text-primary-foreground data-[state=active]:shadow-sm dark:hover:bg-primary/20 dark:data-[state=active]:bg-primary dark:data-[state=active]:text-primary-foreground"
                >
                  <CreditCard class="size-4 shrink-0" />
                  <span class="text-center leading-tight">Liability</span>
                </TabsTrigger>
              </TabsList>

              <!-- Asset tab -->
              <TabsContent value="ASSET" class="space-y-4 pt-4">
                <div class="grid gap-2">
                  <Label for="asset-name">Name</Label>
                  <Input
                    id="asset-name"
                    v-model="newAccount.name"
                    placeholder="e.g. Checking, Savings"
                  />
                </div>
                <div class="grid gap-2">
                  <Label>Currency</Label>
                  <Select v-model="newAccount.currency">
                    <SelectTrigger class="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem
                        v-for="c in currencyStore.currencies"
                        :key="c.code"
                        :value="c.code"
                      >
                        {{ c.code }} – {{ c.name }}
                      </SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div class="grid grid-cols-2 gap-4">
                  <div class="grid gap-2">
                    <Label for="asset-opening-date">Opening date</Label>
                    <Input
                      id="asset-opening-date"
                      v-model="openingBalanceDate"
                      type="date"
                      :disabled="openingBalanceMinor === 0"
                    />
                  </div>
                  <div class="grid gap-2">
                    <Label>Initial balance</Label>
                    <CurrencyAmountInput
                      v-model="openingBalanceMinor"
                      :minor-unit="openingBalanceMinorUnit"
                      :currency-code="newAccount.currency"
                      :allow-negative="true"
                      placeholder="0.00"
                    />
                  </div>
                </div>
                <p class="text-xs text-muted-foreground">
                  Posted as an opening balance journal entry against Opening Equity. Opening balance
                  date is used only when initial balance is non-zero.
                </p>
              </TabsContent>

              <!-- Expense tab -->
              <TabsContent value="EXPENSE" class="space-y-4 pt-4">
                <div class="grid gap-2">
                  <Label for="expense-name">Name</Label>
                  <Input
                    id="expense-name"
                    v-model="newAccount.name"
                    placeholder="e.g. Groceries, Utilities"
                  />
                </div>
                <div class="grid gap-2">
                  <Label>Currency</Label>
                  <Select v-model="newAccount.currency">
                    <SelectTrigger class="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem
                        v-for="c in currencyStore.currencies"
                        :key="c.code"
                        :value="c.code"
                      >
                        {{ c.code }} – {{ c.name }}
                      </SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </TabsContent>

              <!-- Income tab -->
              <TabsContent value="INCOME" class="space-y-4 pt-4">
                <div class="grid gap-2">
                  <Label for="income-name">Name</Label>
                  <Input
                    id="income-name"
                    v-model="newAccount.name"
                    placeholder="e.g. Salary, Freelance"
                  />
                </div>
                <div class="grid gap-2">
                  <Label>Currency</Label>
                  <Select v-model="newAccount.currency">
                    <SelectTrigger class="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem
                        v-for="c in currencyStore.currencies"
                        :key="c.code"
                        :value="c.code"
                      >
                        {{ c.code }} – {{ c.name }}
                      </SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </TabsContent>

              <!-- Liability tab -->
              <TabsContent value="LIABILITY" class="space-y-4 pt-4">
                <div class="grid gap-2">
                  <Label for="liability-name">Name</Label>
                  <Input
                    id="liability-name"
                    v-model="newAccount.name"
                    placeholder="e.g. Mortgage, Car Loan"
                  />
                </div>
                <div class="grid gap-2">
                  <Label>Currency</Label>
                  <Select v-model="newAccount.currency">
                    <SelectTrigger class="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem
                        v-for="c in currencyStore.currencies"
                        :key="c.code"
                        :value="c.code"
                      >
                        {{ c.code }} – {{ c.name }}
                      </SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div class="grid grid-cols-2 gap-4">
                  <div class="grid gap-2">
                    <Label for="liability-opening-date">Opening date</Label>
                    <Input
                      id="liability-opening-date"
                      v-model="openingBalanceDate"
                      type="date"
                      :disabled="openingBalanceMinor === 0"
                    />
                  </div>
                  <div class="grid gap-2">
                    <Label>Initial balance</Label>
                    <CurrencyAmountInput
                      v-model="openingBalanceMinor"
                      :minor-unit="openingBalanceMinorUnit"
                      :currency-code="newAccount.currency"
                      :allow-negative="false"
                      placeholder="0.00"
                    />
                  </div>
                </div>
                <p class="text-xs text-muted-foreground">
                  Posted as opening debt against Opening Equity. Liability opening amounts must be
                  positive.
                </p>
              </TabsContent>
            </Tabs>

            <p v-if="createError" class="text-sm text-red-600">{{ createError }}</p>

            <DialogFooter>
              <Button :disabled="isCreating || !newAccount.name.trim()" @click="submitCreate">
                {{ isCreating ? 'Creating...' : 'Create' }}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </CardHeader>
      <CardContent class="flex flex-col pb-6">
        <div class="mb-4 flex flex-wrap items-center gap-3">
          <Select v-model="typeFilter">
            <SelectTrigger class="w-36">
              <SelectValue placeholder="Filter type" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All types</SelectItem>
              <SelectItem v-for="t in accountTypes" :key="t" :value="t">
                {{ t }}
              </SelectItem>
            </SelectContent>
          </Select>
          <Select v-model="currencyFilter">
            <SelectTrigger class="w-36">
              <SelectValue placeholder="Filter currency" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All currencies</SelectItem>
              <SelectItem v-for="code in availableCurrencies" :key="code" :value="code">
                {{ code }}
              </SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div
          v-if="accountStore.isLoading"
          class="flex flex-1 items-center justify-center text-sm text-muted-foreground"
        >
          Loading accounts...
        </div>

        <div
          v-else-if="accountStore.error"
          class="flex flex-1 items-center justify-center text-sm text-red-600"
        >
          {{ accountStore.error }}
        </div>

        <div
          v-else-if="filteredAccounts.length === 0"
          class="flex flex-1 items-center justify-center text-sm text-muted-foreground"
        >
          No accounts found.
        </div>

        <DataTable v-else :table="table" sticky-header empty-text="No accounts found." />
      </CardContent>
    </Card>

    <Dialog v-model:open="renameDialog">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Rename Account</DialogTitle>
          <DialogDescription> Enter a new name for "{{ renameTarget?.name }}". </DialogDescription>
        </DialogHeader>
        <div class="grid gap-2 py-4">
          <Input v-model="renameName" placeholder="Account name" />
          <p v-if="renameError" class="text-sm text-red-600">{{ renameError }}</p>
        </div>
        <DialogFooter>
          <Button :disabled="!renameName.trim()" @click="submitRename"> Save </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </section>
</template>
