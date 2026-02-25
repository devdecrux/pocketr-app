import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import type { Account } from '@/types/ledger'

const getAccountBalances = vi.fn()
const getMonthlyReport = vi.fn()

const accountStore = {
  activeAccounts: [] as Account[],
  accounts: [] as Account[],
  accountMap: new Map<string, Account>(),
  isLoading: false,
  load: vi.fn(async () => {}),
}

const currencyStore = {
  currencies: [{ code: 'EUR', minorUnit: 2, name: 'Euro' }],
  load: vi.fn(async () => {}),
  getMinorUnit: vi.fn(() => 2),
}

const modeStore: {
  isHousehold: boolean
  householdId: string | null
  modeParam: 'INDIVIDUAL' | 'HOUSEHOLD'
  viewMode: { kind: 'INDIVIDUAL' } | { kind: 'HOUSEHOLD'; householdId: string }
} = {
  isHousehold: false,
  householdId: null as string | null,
  modeParam: 'INDIVIDUAL' as 'INDIVIDUAL' | 'HOUSEHOLD',
  viewMode: { kind: 'INDIVIDUAL' as const },
}

const householdStore = {
  sharedAccounts: [] as Array<{
    accountId: string
    accountName: string
    ownerEmail: string
    ownerFirstName: string | null
    ownerLastName: string | null
    sharedAt: string
  }>,
  loadSharedAccounts: vi.fn(async () => {}),
}

const ledgerStore = {
  transactions: [] as Array<{
    id: string
    txnDate: string
    description: string
    currency: string
    splits: Array<{ side: 'DEBIT' | 'CREDIT'; amountMinor: number }>
  }>,
  isLoading: false,
  load: vi.fn(async () => {}),
}

vi.mock('@tanstack/vue-table', () => ({
  getCoreRowModel: vi.fn(() => vi.fn()),
  useVueTable: vi.fn(() => ({})),
}))

vi.mock('@/api/ledger', () => ({
  getAccountBalances,
}))

vi.mock('@/api/reports', () => ({
  getMonthlyReport,
}))

vi.mock('@/api/accounts', () => ({
  createAccount: vi.fn(async () => {}),
  updateAccount: vi.fn(async () => {}),
}))

vi.mock('@/stores/account', () => ({
  useAccountStore: () => accountStore,
}))

vi.mock('@/stores/currency', () => ({
  useCurrencyStore: () => currencyStore,
}))

vi.mock('@/stores/mode', () => ({
  useModeStore: () => modeStore,
}))

vi.mock('@/stores/household', () => ({
  useHouseholdStore: () => householdStore,
}))

vi.mock('@/stores/ledger', () => ({
  useLedgerStore: () => ledgerStore,
}))

const { default: AccountsPage } = await import('@/views/AccountsPage.vue')
const { default: DashboardPage } = await import('@/views/DashboardPage.vue')

describe('batch balances page wiring', () => {
  beforeEach(() => {
    getAccountBalances.mockReset()
    getMonthlyReport.mockReset()
    accountStore.load.mockClear()
    currencyStore.load.mockClear()
    ledgerStore.load.mockClear()
    householdStore.loadSharedAccounts.mockClear()
    currencyStore.getMinorUnit.mockReset()

    modeStore.isHousehold = false
    modeStore.householdId = null
    modeStore.modeParam = 'INDIVIDUAL'
    modeStore.viewMode = { kind: 'INDIVIDUAL' }

    accountStore.activeAccounts = []
    accountStore.accounts = []
    accountStore.accountMap = new Map()
    householdStore.sharedAccounts = []
    ledgerStore.transactions = []

    currencyStore.getMinorUnit.mockReturnValue(2)
    getAccountBalances.mockResolvedValue([])
    getMonthlyReport.mockResolvedValue([])
  })

  it('AccountsPage calls batch balances once per load with all active account ids', async () => {
    accountStore.activeAccounts = [
      {
        id: 'acc-1',
        ownerUserId: 1,
        name: 'Checking',
        type: 'ASSET',
        currency: 'EUR',
        createdAt: '2026-02-01T00:00:00Z',
      },
      {
        id: 'acc-2',
        ownerUserId: 1,
        name: 'Savings',
        type: 'ASSET',
        currency: 'EUR',
        createdAt: '2026-02-01T00:00:00Z',
      },
    ]
    accountStore.accounts = [...accountStore.activeAccounts]

    const wrapper = shallowMount(AccountsPage)
    await flushPromises()

    expect(getAccountBalances).toHaveBeenCalledTimes(1)
    expect(getAccountBalances).toHaveBeenCalledWith(['acc-1', 'acc-2'], undefined, undefined)
    wrapper.unmount()
  })

  it('DashboardPage calls batch balances once and filters to shared ids in household mode', async () => {
    modeStore.isHousehold = true
    modeStore.householdId = 'hh-1'
    modeStore.modeParam = 'HOUSEHOLD'
    modeStore.viewMode = { kind: 'HOUSEHOLD', householdId: 'hh-1' }

    accountStore.activeAccounts = [
      {
        id: 'acc-shared',
        ownerUserId: 1,
        name: 'Shared Checking',
        type: 'ASSET',
        currency: 'EUR',
        createdAt: '2026-02-01T00:00:00Z',
      },
      {
        id: 'acc-private',
        ownerUserId: 1,
        name: 'Private Savings',
        type: 'ASSET',
        currency: 'EUR',
        createdAt: '2026-02-01T00:00:00Z',
      },
      {
        id: 'acc-expense',
        ownerUserId: 1,
        name: 'Groceries',
        type: 'EXPENSE',
        currency: 'EUR',
        createdAt: '2026-02-01T00:00:00Z',
      },
    ]
    accountStore.accounts = [...accountStore.activeAccounts]
    accountStore.accountMap = new Map(
      accountStore.activeAccounts.map((account) => [account.id, account]),
    )

    householdStore.sharedAccounts = [
      {
        accountId: 'acc-shared',
        accountName: 'Shared Checking',
        ownerEmail: 'user@test.com',
        ownerFirstName: null,
        ownerLastName: null,
        sharedAt: '2026-02-01T00:00:00Z',
      },
    ]

    getAccountBalances.mockResolvedValue([
      {
        accountId: 'acc-shared',
        accountName: 'Shared Checking',
        accountType: 'ASSET',
        currency: 'EUR',
        balanceMinor: 12345,
        asOf: '2026-02-25',
      },
    ])

    const wrapper = shallowMount(DashboardPage)
    await flushPromises()

    expect(householdStore.loadSharedAccounts).toHaveBeenCalledTimes(1)
    expect(householdStore.loadSharedAccounts).toHaveBeenCalledWith('hh-1')
    expect(getAccountBalances).toHaveBeenCalledTimes(1)
    expect(getAccountBalances).toHaveBeenCalledWith(['acc-shared'], undefined, 'hh-1')
    wrapper.unmount()
  })
})
