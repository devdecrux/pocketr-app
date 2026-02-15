<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import {
  type ColumnDef,
  FlexRender,
  getCoreRowModel,
  getFilteredRowModel,
  useVueTable,
} from '@tanstack/vue-table'
import { Pencil, Plus } from 'lucide-vue-next'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
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
import { useAccountStore } from '@/stores/account'
import { useCurrencyStore } from '@/stores/currency'
import { useModeStore } from '@/stores/mode'
import { useHouseholdStore } from '@/stores/household'
import { createAccount, updateAccount } from '@/api/accounts'
import { getAccountBalance } from '@/api/ledger'
import { formatMinor } from '@/utils/money'
import type { Account, AccountType, CreateAccountRequest } from '@/types/ledger'
import CurrencyAmountInput from '@/components/CurrencyAmountInput.vue'

const accountStore = useAccountStore()
const currencyStore = useCurrencyStore()
const modeStore = useModeStore()
const householdStore = useHouseholdStore()

const balances = ref<Map<string, number>>(new Map())
const balancesLoading = ref(false)
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
  getFilteredRowModel: getFilteredRowModel(),
})

// N+1 pattern: one API call per account. A batch balance endpoint should be preferred when available.
async function loadBalances(): Promise<void> {
  balancesLoading.value = true
  const promises = accountStore.activeAccounts.map(async (account) => {
    try {
      const result = await getAccountBalance(account.id)
      balances.value.set(account.id, result.balanceMinor)
    } catch {
      // skip failed balance loads
    }
  })
  await Promise.all(promises)
  balancesLoading.value = false
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
    if (newAccount.value.type === 'ASSET' && openingBalanceMinor.value !== 0) {
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
  <section class="grid gap-4">
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
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Create Account</DialogTitle>
              <DialogDescription> Add a new account to your ledger. </DialogDescription>
            </DialogHeader>
            <div class="grid gap-4 py-4">
              <div class="grid gap-2">
                <Label for="account-name">Name</Label>
                <Input
                  id="account-name"
                  v-model="newAccount.name"
                  placeholder="e.g. Checking, Savings"
                />
              </div>
              <div class="grid gap-2">
                <Label>Type</Label>
                <Select v-model="newAccount.type">
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem v-for="t in accountTypes" :key="t" :value="t">
                      {{ t }}
                    </SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div class="grid gap-2">
                <Label>Currency</Label>
                <Select v-model="newAccount.currency">
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem v-for="c in currencyStore.currencies" :key="c.code" :value="c.code">
                      {{ c.code }} - {{ c.name }}
                    </SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div v-if="newAccount.type === 'ASSET'" class="grid gap-2">
                <Label for="opening-balance">Initial balance</Label>
                <CurrencyAmountInput
                  id="opening-balance"
                  v-model="openingBalanceMinor"
                  :minor-unit="openingBalanceMinorUnit"
                  :currency-code="newAccount.currency"
                  :allow-negative="true"
                  placeholder="0.00"
                />
                <p class="text-xs text-muted-foreground">
                  Posted as an opening balance journal entry against Opening Equity.
                </p>
              </div>
              <div
                v-if="newAccount.type === 'ASSET' && openingBalanceMinor !== 0"
                class="grid gap-2"
              >
                <Label for="opening-balance-date">Opening balance date</Label>
                <Input id="opening-balance-date" v-model="openingBalanceDate" type="date" />
              </div>
              <p v-if="createError" class="text-sm text-red-600">{{ createError }}</p>
            </div>
            <DialogFooter>
              <Button :disabled="isCreating || !newAccount.name.trim()" @click="submitCreate">
                {{ isCreating ? 'Creating...' : 'Create' }}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </CardHeader>
      <CardContent>
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

        <div v-if="accountStore.isLoading" class="space-y-3">
          <Skeleton class="h-10 w-full" />
          <Skeleton class="h-10 w-full" />
          <Skeleton class="h-10 w-full" />
        </div>

        <div v-else-if="accountStore.error" class="text-sm text-red-600">
          {{ accountStore.error }}
        </div>

        <div v-else class="overflow-auto rounded-md border">
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
                  class="px-4 py-2 text-left font-medium text-muted-foreground"
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
              <tr
                v-for="row in table.getRowModel().rows"
                :key="row.id"
                class="border-b last:border-0"
              >
                <td v-for="cell in row.getVisibleCells()" :key="cell.id" class="px-4 py-2">
                  <FlexRender :render="cell.column.columnDef.cell" :props="cell.getContext()" />
                </td>
              </tr>
              <tr v-if="table.getRowModel().rows.length === 0">
                <td :colspan="columns.length" class="px-4 py-8 text-center text-muted-foreground">
                  No accounts found.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
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
