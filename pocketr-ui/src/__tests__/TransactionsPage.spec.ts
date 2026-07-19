import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { i18n } from '@/i18n'
import type { Account, LedgerTxn } from '@/types/ledger'

const createTxn = vi.fn()
const deleteTxn = vi.fn()

const accountStore = {
  activeAccounts: [] as Account[],
  accounts: [] as Account[],
  accountMap: new Map<string, Account>(),
  load: vi.fn(async () => {}),
}

const categoryStore = {
  categories: [],
  load: vi.fn(async () => {}),
}

const currencyStore = {
  load: vi.fn(async () => {}),
  getMinorUnit: vi.fn((currency: string) => (currency === 'JPY' ? 0 : 2)),
}

const householdStore = {
  loadHouseholds: vi.fn(async () => {}),
}

const ledgerStore = {
  transactions: [] as LedgerTxn[],
  isLoading: false,
  error: null as string | null,
  currentPage: 0,
  pageSize: 10,
  totalPages: 1,
  totalElements: 0,
  load: vi.fn(async () => {}),
}

const modeStore = {
  isHousehold: false,
  householdId: null as string | null,
  modeParam: 'INDIVIDUAL' as const,
  viewMode: { kind: 'INDIVIDUAL' as const },
}

vi.mock('@/api/ledger', () => ({
  createTxn,
  deleteTxn,
}))

vi.mock('@/stores/account', () => ({
  useAccountStore: () => accountStore,
}))

vi.mock('@/stores/category', () => ({
  useCategoryStore: () => categoryStore,
}))

vi.mock('@/stores/currency', () => ({
  useCurrencyStore: () => currencyStore,
}))

vi.mock('@/stores/household', () => ({
  useHouseholdStore: () => householdStore,
}))

vi.mock('@/stores/ledger', () => ({
  useLedgerStore: () => ledgerStore,
}))

vi.mock('@/stores/mode', () => ({
  useModeStore: () => modeStore,
}))

const { default: TransactionsPage } = await import('@/views/TransactionsPage.vue')

function account(override: Partial<Account>): Account {
  return {
    id: 'acc-1',
    ownerUserId: 1,
    name: 'Checking',
    type: 'ASSET',
    currency: 'USD',
    createdAt: '2026-02-24T00:00:00Z',
    status: 'ACTIVE',
    archivedAt: null,
    ...override,
  }
}

function transaction(override: Partial<LedgerTxn>): LedgerTxn {
  return {
    id: 'txn-1',
    txnDate: '2026-02-24',
    description: 'Groceries',
    currency: 'USD',
    txnKind: 'EXPENSE',
    createdAt: '2026-02-24T00:00:00Z',
    updatedAt: '2026-02-24T00:00:00Z',
    splits: [],
    ...override,
  }
}

function mountPage() {
  const SlotStub = defineComponent({
    template: '<div><slot /><slot name="footer" /></div>',
  })

  return shallowMount(TransactionsPage, {
    global: {
      plugins: [i18n],
      stubs: {
        AppCardHeader: SlotStub,
        AppDialogContent: SlotStub,
        AppFilterBar: SlotStub,
        AppFormField: SlotStub,
        Card: SlotStub,
        CardContent: SlotStub,
        Dialog: SlotStub,
        DialogTrigger: SlotStub,
        Tabs: SlotStub,
        TabsContent: SlotStub,
        TabsList: SlotStub,
        TabsTrigger: SlotStub,
        DataTable: defineComponent({
          name: 'DataTable',
          props: ['table', 'stickyHeader', 'clickable', 'emptyText', 'pagination'],
          emits: ['row-click', 'update:page', 'update:page-size'],
          setup(_props, { slots }) {
            return () =>
              h(
                'div',
                ledgerStore.transactions.map((txn) =>
                  slots.expanded?.({
                    row: {
                      original: txn,
                    },
                  }),
                ),
              )
          },
        }),
      },
    },
  })
}

function resetStores(): void {
  accountStore.activeAccounts = [
    account({ id: 'usd-cash', name: 'USD Cash', type: 'ASSET', currency: 'USD' }),
    account({ id: 'eur-groceries', name: 'EUR Groceries', type: 'EXPENSE', currency: 'EUR' }),
    account({ id: 'bgn-savings', name: 'BGN Savings', type: 'ASSET', currency: 'BGN' }),
    account({ id: 'eur-income', name: 'EUR Income', type: 'INCOME', currency: 'EUR' }),
    account({ id: 'bgn-loan', name: 'BGN Loan', type: 'LIABILITY', currency: 'BGN' }),
  ]
  accountStore.accounts = [...accountStore.activeAccounts]
  accountStore.accountMap = new Map(accountStore.activeAccounts.map((item) => [item.id, item]))
  ledgerStore.transactions = []
  ledgerStore.totalElements = 0
  createTxn.mockReset()
  deleteTxn.mockReset()
  accountStore.load.mockClear()
  categoryStore.load.mockClear()
  currencyStore.load.mockClear()
  householdStore.loadHouseholds.mockClear()
  ledgerStore.load.mockClear()
  currencyStore.getMinorUnit.mockClear()
}

describe('TransactionsPage currency exchange behavior', () => {
  beforeEach(() => {
    resetStores()
  })

  it('does not currency-filter destination account selectors for mixed-currency transactions', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const selectors = wrapper.findAllComponents({ name: 'AccountSelector' })
    const destinationTypes = new Set(['EXPENSE', 'INCOME', 'ASSET', 'LIABILITY'])
    const destinationSelectors = selectors.filter((selector) => {
      const allowedTypes = selector.props('allowedTypes') as string[] | undefined
      return allowedTypes?.some((type) => destinationTypes.has(type)) ?? false
    })

    expect(destinationSelectors.length).toBeGreaterThanOrEqual(4)
    expect(destinationSelectors.every((selector) => selector.props('currency') === undefined)).toBe(
      true,
    )
  })

  it('displays expanded split amounts using accountCurrency from the response', async () => {
    ledgerStore.transactions = [
      transaction({
        splits: [
          {
            id: 'split-source',
            accountId: 'usd-cash',
            accountName: 'USD Cash',
            accountCurrency: 'USD',
            side: 'CREDIT',
            amountMinor: 1000,
            exchangeRate: '1',
          },
          {
            id: 'split-destination',
            accountId: 'eur-groceries',
            accountName: 'EUR Groceries',
            accountCurrency: 'EUR',
            side: 'DEBIT',
            amountMinor: 920,
            exchangeRate: '0.92',
          },
        ],
      }),
    ]

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('-$10.00')
    expect(wrapper.text()).toContain('+€9.20')
  })

  it('displays the ledger snapshot name when a split account is no longer active', async () => {
    ledgerStore.transactions = [
      transaction({
        splits: [
          {
            id: 'split-archived',
            accountId: 'archived-account',
            accountName: 'Old Checking',
            accountCurrency: 'USD',
            side: 'CREDIT',
            amountMinor: 1000,
            exchangeRate: '1',
          },
        ],
      }),
    ]

    const wrapper = mountPage()
    await flushPromises()

    expect(accountStore.accountMap.has('archived-account')).toBe(false)
    expect(wrapper.text()).toContain('Old Checking')
    expect(wrapper.text()).not.toContain('archived-account')
  })
})
