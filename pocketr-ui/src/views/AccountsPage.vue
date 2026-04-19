<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import { type ColumnDef, getCoreRowModel, useVueTable } from '@tanstack/vue-table'
import { CreditCard, Pencil, Plus, ShoppingCart, TrendingUp, Wallet } from 'lucide-vue-next'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Dialog, DialogTrigger } from '@/components/ui/dialog'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useAccountStore } from '@/stores/account'
import { useCurrencyStore } from '@/stores/currency'
import { useModeStore } from '@/stores/mode'
import { useHouseholdStore } from '@/stores/household'
import { createAccount, updateAccount } from '@/api/accounts'
import { getAccountBalances } from '@/api/ledger'
import { formatMinor } from '@/utils/money'
import type { Account, AccountType, CreateAccountRequest } from '@/types/ledger'
import {
  AppCardHeader,
  AppDialogBody,
  AppDialogContent,
  AppFilterBar,
  AppFormField,
  AppStateMessage,
  AppStatusText,
} from '@/components/app'
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
    cell: ({ row }) => {
      return h(
        Button,
        {
          variant: 'ghost',
          size: 'icon',
          'data-table-action': 'edit',
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
const renameDescription = computed(() => `Enter a new name for "${renameTarget.value?.name}".`)

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
      <AppCardHeader title="Accounts" title-class="text-2xl">
        <Dialog v-model:open="createDialogOpen">
          <DialogTrigger as-child>
            <Button size="sm">
              <Plus class="size-4" />
              New Account
            </Button>
          </DialogTrigger>
          <AppDialogContent
            title="Create Account"
            description="Add a new account to your ledger."
            class="max-w-lg"
          >
            <Tabs v-model="newAccount.type" class="w-full">
              <TabsList>
                <TabsTrigger value="ASSET">
                  <Wallet class="size-4 shrink-0" />
                  <span class="text-center leading-tight">Asset</span>
                </TabsTrigger>
                <TabsTrigger value="EXPENSE">
                  <ShoppingCart class="size-4 shrink-0" />
                  <span class="text-center leading-tight">Expense</span>
                </TabsTrigger>
                <TabsTrigger value="INCOME">
                  <TrendingUp class="size-4 shrink-0" />
                  <span class="text-center leading-tight">Income</span>
                </TabsTrigger>
                <TabsTrigger value="LIABILITY">
                  <CreditCard class="size-4 shrink-0" />
                  <span class="text-center leading-tight">Liability</span>
                </TabsTrigger>
              </TabsList>

              <!-- Asset tab -->
              <TabsContent value="ASSET" class="space-y-4 pt-4">
                <AppFormField label="Name" control-id="asset-name">
                  <Input
                    id="asset-name"
                    v-model="newAccount.name"
                    placeholder="e.g. Checking, Savings"
                  />
                </AppFormField>
                <AppFormField label="Currency">
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
                </AppFormField>
                <div class="grid grid-cols-2 gap-4">
                  <AppFormField label="Opening date" control-id="asset-opening-date">
                    <Input
                      id="asset-opening-date"
                      v-model="openingBalanceDate"
                      type="date"
                      :disabled="openingBalanceMinor === 0"
                    />
                  </AppFormField>
                  <AppFormField label="Initial balance">
                    <CurrencyAmountInput
                      v-model="openingBalanceMinor"
                      :minor-unit="openingBalanceMinorUnit"
                      :currency-code="newAccount.currency"
                      :allow-negative="true"
                      placeholder="0.00"
                    />
                  </AppFormField>
                </div>
                <p class="text-xs text-muted-foreground">
                  Posted as an opening balance journal entry against Opening Equity. Opening balance
                  date is used only when initial balance is non-zero.
                </p>
              </TabsContent>

              <!-- Expense tab -->
              <TabsContent value="EXPENSE" class="space-y-4 pt-4">
                <AppFormField label="Name" control-id="expense-name">
                  <Input
                    id="expense-name"
                    v-model="newAccount.name"
                    placeholder="e.g. Groceries, Utilities"
                  />
                </AppFormField>
                <AppFormField label="Currency">
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
                </AppFormField>
              </TabsContent>

              <!-- Income tab -->
              <TabsContent value="INCOME" class="space-y-4 pt-4">
                <AppFormField label="Name" control-id="income-name">
                  <Input
                    id="income-name"
                    v-model="newAccount.name"
                    placeholder="e.g. Salary, Freelance"
                  />
                </AppFormField>
                <AppFormField label="Currency">
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
                </AppFormField>
              </TabsContent>

              <!-- Liability tab -->
              <TabsContent value="LIABILITY" class="space-y-4 pt-4">
                <AppFormField label="Name" control-id="liability-name">
                  <Input
                    id="liability-name"
                    v-model="newAccount.name"
                    placeholder="e.g. Mortgage, Car Loan"
                  />
                </AppFormField>
                <AppFormField label="Currency">
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
                </AppFormField>
                <div class="grid grid-cols-2 gap-4">
                  <AppFormField label="Opening date" control-id="liability-opening-date">
                    <Input
                      id="liability-opening-date"
                      v-model="openingBalanceDate"
                      type="date"
                      :disabled="openingBalanceMinor === 0"
                    />
                  </AppFormField>
                  <AppFormField label="Initial balance">
                    <CurrencyAmountInput
                      v-model="openingBalanceMinor"
                      :minor-unit="openingBalanceMinorUnit"
                      :currency-code="newAccount.currency"
                      :allow-negative="false"
                      placeholder="0.00"
                    />
                  </AppFormField>
                </div>
                <p class="text-xs text-muted-foreground">
                  Posted as opening debt against Opening Equity. Liability opening amounts must be
                  positive.
                </p>
              </TabsContent>
            </Tabs>

            <AppStatusText v-if="createError">{{ createError }}</AppStatusText>

            <template #footer>
              <Button :disabled="isCreating || !newAccount.name.trim()" @click="submitCreate">
                {{ isCreating ? 'Creating...' : 'Create' }}
              </Button>
            </template>
          </AppDialogContent>
        </Dialog>
      </AppCardHeader>
      <CardContent class="flex flex-col pb-6">
        <AppFilterBar align="center">
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
        </AppFilterBar>

        <AppStateMessage v-if="accountStore.isLoading" center>
          Loading accounts...
        </AppStateMessage>

        <AppStateMessage v-else-if="accountStore.error" variant="error" center>
          {{ accountStore.error }}
        </AppStateMessage>

        <AppStateMessage v-else-if="filteredAccounts.length === 0" center>
          No accounts found.
        </AppStateMessage>

        <DataTable v-else :table="table" sticky-header empty-text="No accounts found." />
      </CardContent>
    </Card>

    <Dialog v-model:open="renameDialog">
      <AppDialogContent title="Rename Account" :description="renameDescription">
        <AppDialogBody gap="2">
          <Input v-model="renameName" placeholder="Account name" />
          <AppStatusText v-if="renameError">{{ renameError }}</AppStatusText>
        </AppDialogBody>
        <template #footer>
          <Button :disabled="!renameName.trim()" @click="submitRename"> Save </Button>
        </template>
      </AppDialogContent>
    </Dialog>
  </section>
</template>
