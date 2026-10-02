import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { HTTPError } from 'ky'
import { h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import UDashboardGroup from '@nuxt/ui/components/DashboardGroup.vue'
import AccountSelect from '@/components/forms/AccountSelect.vue'
import AppDateRangePicker from '@/components/forms/AppDateRangePicker.vue'
import CategorySelect from '@/components/forms/CategorySelect.vue'
import MoneyInput from '@/components/forms/MoneyInput.vue'
import { i18n } from '@/i18n'
import { useAuthStore } from '@/stores/auth'
import { useLedgerStore } from '@/stores/ledger'
import { useModeStore } from '@/stores/mode'
import type { Account, LedgerSplit, LedgerTxn } from '@/types/ledger'
import TransactionsPage from '@/views/TransactionsPage.vue'

const mocks = vi.hoisted(() => ({
  desktop: { value: true },
  listTxns: vi.fn(),
  createTxn: vi.fn(),
  deleteTxn: vi.fn(),
  listAccounts: vi.fn(),
  listCategories: vi.fn(),
  listCurrencies: vi.fn(),
  listHouseholds: vi.fn(),
}))

vi.mock('@vueuse/core', async () => {
  const actual = await vi.importActual<typeof import('@vueuse/core')>('@vueuse/core')
  const { ref } = await vi.importActual<typeof import('vue')>('vue')
  return {
    ...actual,
    useColorMode: () => ref('light'),
    useMediaQuery: () => ref(mocks.desktop.value),
  }
})

vi.mock('@/api/csrf', () => ({ primeCsrfToken: vi.fn() }))
vi.mock('@/api/http', () => ({ api: { post: vi.fn(), get: vi.fn() } }))
vi.mock('@/api/ledger', () => ({
  listTxns: mocks.listTxns,
  createTxn: mocks.createTxn,
  deleteTxn: mocks.deleteTxn,
}))
vi.mock('@/api/accounts', () => ({
  listAccounts: mocks.listAccounts,
  archiveAccount: vi.fn(),
}))
vi.mock('@/api/categories', () => ({ listCategories: mocks.listCategories }))
vi.mock('@/api/currencies', () => ({ listCurrencies: mocks.listCurrencies }))
vi.mock('@/api/households', () => ({ listHouseholds: mocks.listHouseholds }))

function account(override: Partial<Account>): Account {
  return {
    id: 'acc-1',
    ownerUserId: 1,
    name: 'Checking',
    type: 'ASSET',
    currency: 'EUR',
    createdAt: '2026-01-01T00:00:00Z',
    status: 'ACTIVE',
    archivedAt: null,
    ...override,
  }
}

const ACCOUNTS = [
  account({ id: 'daily', name: 'Daily account', type: 'ASSET' }),
  account({ id: 'savings', name: 'Savings', type: 'ASSET' }),
  account({ id: 'card', name: 'Credit card', type: 'LIABILITY' }),
  account({ id: 'salary', name: 'Salary', type: 'INCOME' }),
  account({ id: 'living', name: 'Living expenses', type: 'EXPENSE' }),
  account({ id: 'old', name: 'Old checking', type: 'ASSET', status: 'ARCHIVED' }),
  account({ id: 'jamie', name: 'Jamie wallet', type: 'ASSET', ownerUserId: 2 }),
  account({ id: 'usd', name: 'USD cash', type: 'ASSET', currency: 'USD' }),
  account({ id: 'eur-spend', name: 'EUR spending', type: 'EXPENSE' }),
]

function split(
  accountId: string,
  accountName: string,
  side: LedgerSplit['side'],
  amountMinor: number,
  extra: Partial<LedgerSplit> = {},
): LedgerSplit {
  return {
    id: `${accountId}-${side}`,
    accountId,
    accountName,
    accountCurrency: 'EUR',
    side,
    amountMinor,
    exchangeRate: '1',
    ...extra,
  }
}

function txn(override: Partial<LedgerTxn>): LedgerTxn {
  return {
    id: 't1',
    txnDate: '2026-09-20',
    description: 'Grocery market',
    currency: 'EUR',
    txnKind: 'EXPENSE',
    createdAt: '2026-09-20T08:00:00Z',
    updatedAt: '2026-09-20T08:00:00Z',
    splits: [
      split('daily', 'Daily account', 'CREDIT', 4680),
      split('living', 'Living expenses', 'DEBIT', 4680, { categoryTagId: 'c1' }),
    ],
    ...override,
  }
}

const TXNS: LedgerTxn[] = [
  txn({}),
  txn({
    id: 't2',
    txnDate: '2026-09-19',
    description: 'Savings transfer',
    txnKind: 'TRANSFER',
    splits: [
      split('daily', 'Daily account', 'CREDIT', 25000),
      split('savings', 'Savings', 'DEBIT', 25000),
    ],
  }),
  txn({
    id: 't3',
    txnDate: '2026-09-01',
    description: 'September salary',
    txnKind: 'INCOME',
    splits: [
      split('salary', 'Salary', 'CREDIT', 350000),
      split('daily', 'Daily account', 'DEBIT', 350000),
    ],
  }),
]

function page(content: LedgerTxn[], overrides: Record<string, number> = {}) {
  return {
    content,
    page: 0,
    size: 15,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    ...overrides,
  }
}

function httpError(message: string, status = 400): HTTPError {
  const request = new Request('http://localhost/api')
  const response = new Response(JSON.stringify({ message }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
  return new HTTPError(response, request, {} as never)
}

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/transactions', name: 'transactions', component: TransactionsPage },
      { path: '/dashboard', name: 'dashboard', component: { render: () => null } },
    ],
  })
  await router.push('/transactions')
  await router.isReady()

  const wrapper = mount(
    {
      render: () =>
        h(UApp, null, {
          default: () =>
            h(UDashboardGroup, { unit: 'px', persistent: false }, { default: () => h(RouterView) }),
        }),
    },
    { attachTo: document.body, global: { plugins: [router, i18n, ui] } },
  )
  await flushPromises()
  return wrapper
}

// A closed overlay stays mounted while its exit animation would run, so only open ones count.
function dialogs(): HTMLElement[] {
  return [...document.body.querySelectorAll<HTMLElement>('[role="dialog"]')].filter(
    (dialog) => dialog.getAttribute('data-state') !== 'closed',
  )
}

function dialogByTitle(title: string): HTMLElement {
  const match = dialogs().find((dialog) => dialog.textContent?.includes(title))
  if (!match) throw new Error(`No dialog "${title}"`)
  return match
}

function buttonIn(root: ParentNode, text: string): HTMLButtonElement {
  const button = [...root.querySelectorAll<HTMLButtonElement>('button')].find(
    (candidate) =>
      candidate.textContent?.trim() === text || candidate.getAttribute('aria-label') === text,
  )
  if (!button) throw new Error(`No button "${text}"`)
  return button
}

async function click(button: Element) {
  ;(button as HTMLElement).click()
  await flushPromises()
}

async function openFilters(wrapper: VueWrapper) {
  await click(buttonIn(wrapper.element as HTMLElement, 'Filters'))
}

function rowTexts(wrapper: VueWrapper): string[] {
  return wrapper.findAll('main tbody tr[data-selectable="true"]').map((row) => row.text())
}

function last<T>(items: readonly T[]): T | undefined {
  return items[items.length - 1]
}

function lastListCall(): Record<string, unknown> {
  return last(mocks.listTxns.mock.calls)![0] as Record<string, unknown>
}

describe('TransactionsPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-09-20T10:00:00'))
    setActivePinia(createPinia())
    vi.stubGlobal('localStorage', {
      getItem: vi.fn(() => null),
      setItem: vi.fn(),
      removeItem: vi.fn(),
    })
    mocks.desktop.value = true
    for (const mock of Object.values(mocks)) {
      if (typeof mock === 'function') mock.mockReset()
    }
    mocks.listTxns.mockResolvedValue(page(TXNS))
    mocks.listAccounts.mockResolvedValue(ACCOUNTS)
    mocks.listCategories.mockResolvedValue([
      { id: 'c1', name: 'Groceries', color: '#62a864', createdAt: '2026-09-12T09:00:00Z' },
      { id: 'c2', name: 'Transport', color: '#8391a5', createdAt: '2026-09-12T09:00:00Z' },
    ])
    mocks.listCurrencies.mockResolvedValue([
      { code: 'EUR', minorUnit: 2, name: 'Euro' },
      { code: 'JPY', minorUnit: 0, name: 'Yen' },
    ])
    mocks.listHouseholds.mockResolvedValue([])
    useAuthStore().user = {
      id: 1,
      email: 'alex@example.com',
      firstName: 'Alex',
      lastName: 'Morgan',
      language: 'en',
      rolloverDay: 1,
      avatar: null,
    }
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  describe('list', () => {
    it('loads once and shows the heading, filters and one row per transaction', async () => {
      const wrapper = await mountPage()

      expect(mocks.listTxns).toHaveBeenCalledTimes(1)
      expect(lastListCall()).toEqual({ mode: 'INDIVIDUAL', page: 0, size: 15 })
      expect(wrapper.findAll('h1')).toHaveLength(2)
      expect(wrapper.text()).toContain('Every movement, clearly recorded.')

      // The filters stay hidden until the Filters button opens them.
      const toggle = buttonIn(wrapper.element as HTMLElement, 'Filters')
      expect(toggle.getAttribute('aria-expanded')).toBe('false')
      expect(wrapper.find('input[type="search"]').exists()).toBe(false)
      await openFilters(wrapper)
      expect(toggle.getAttribute('aria-expanded')).toBe('true')
      expect(wrapper.find('input[type="search"]').attributes('placeholder')).toBe(
        'Search descriptions',
      )
      expect(wrapper.text()).not.toContain('All dates')
      expect(wrapper.text()).toContain('All accounts')
      expect(wrapper.text()).toContain('All categories')
      expect(wrapper.findAll('main th').map((th) => th.text())).toEqual([
        'Date',
        'Description',
        'Type',
        'Category',
        'Amount',
        'Actions',
        'Details',
      ])

      const rows = rowTexts(wrapper)
      expect(rows).toHaveLength(3)
      expect(rows[0]).toContain('Grocery market')
      expect(rows[0]).toContain('Expense')
      expect(rows[0]).toContain('Groceries')
      expect(rows[0]).toContain('−€46.80')
      expect(rows[1]).toContain('Transfer')
      expect(rows[1]).toContain('€250.00')
      expect(rows[1]).not.toMatch(/[−+↔]/)
      expect(rows[2]).toContain('Income')
      expect(rows[2]).toContain('+€3,500.00')
      expect(wrapper.text()).toContain('1–3 of 3')

      wrapper.unmount()
    })

    it('shows the loading, empty and error states', async () => {
      mocks.listTxns.mockResolvedValue(page([]))
      const empty = await mountPage()
      expect(empty.text()).toContain('No transactions found.')
      empty.unmount()

      mocks.listTxns.mockRejectedValue(new Error('offline'))
      const failed = await mountPage()
      expect(failed.get('[role="alert"]').text()).toContain('Failed to load transactions.')
      expect(failed.find('main table').exists()).toBe(false)
      failed.unmount()
    })

    it('filters descriptions on the client without a request', async () => {
      const wrapper = await mountPage()
      await openFilters(wrapper)

      await wrapper.get('input[type="search"]').setValue('  SALARY ')
      await flushPromises()

      expect(rowTexts(wrapper)).toHaveLength(1)
      expect(rowTexts(wrapper)[0]).toContain('September salary')
      expect(mocks.listTxns).toHaveBeenCalledTimes(1)

      wrapper.unmount()
    })

    it('reloads from page 0 for a date range, an account (archived included) and a category', async () => {
      const wrapper = await mountPage()
      const ledgerStore = useLedgerStore()
      ledgerStore.currentPage = 2
      await openFilters(wrapper)

      wrapper.findComponent(AppDateRangePicker).vm.$emit('update:from', '2026-09-01')
      wrapper.findComponent(AppDateRangePicker).vm.$emit('update:to', '2026-09-30')
      await flushPromises()
      expect(lastListCall()).toMatchObject({
        dateFrom: '2026-09-01',
        dateTo: '2026-09-30',
        page: 0,
      })

      const accountSelect = wrapper.findComponent(AccountSelect)
      expect(accountSelect.props('includeArchived')).toBe(true)
      accountSelect.vm.$emit('update:modelValue', 'old')
      await flushPromises()
      expect(lastListCall()).toMatchObject({ accountId: 'old', page: 0 })

      wrapper.findComponent(CategorySelect).vm.$emit('update:modelValue', 'c2')
      await flushPromises()
      expect(lastListCall()).toMatchObject({ accountId: 'old', categoryId: 'c2', page: 0 })

      wrapper.unmount()
    })

    it('pages through the server-side result and resets to page 0 when the size changes', async () => {
      mocks.listTxns.mockResolvedValue(page(TXNS, { totalElements: 40, totalPages: 3, size: 15 }))
      const wrapper = await mountPage()

      await click(wrapper.get('button[aria-label="Next page"]').element)
      expect(lastListCall()).toMatchObject({ page: 1, size: 15 })

      const pageSize = last(wrapper.findAllComponents({ name: 'Select' }))!
      pageSize.vm.$emit('update:modelValue', 25)
      await flushPromises()
      expect(lastListCall()).toMatchObject({ page: 0, size: 25 })

      wrapper.unmount()
    })

    it('reloads from page 0 when the view mode changes', async () => {
      const wrapper = await mountPage()

      useModeStore().switchToHousehold('h1')
      await flushPromises()

      expect(lastListCall()).toMatchObject({ mode: 'HOUSEHOLD', householdId: 'h1', page: 0 })
      expect(mocks.listTxns).toHaveBeenCalledTimes(2)

      wrapper.unmount()
    })

    it('adds the member column with creator info in household mode', async () => {
      useModeStore().switchToHousehold('h1')
      mocks.listHouseholds.mockResolvedValue([
        {
          id: 'h1',
          name: 'Morgan household',
          role: 'OWNER',
          status: 'ACTIVE',
          rolloverDay: 1,
          createdAt: '',
        },
      ])
      mocks.listTxns.mockResolvedValue(
        page([
          txn({
            createdBy: {
              firstName: 'Jamie',
              lastName: 'Morgan',
              email: 'jamie@example.com',
              avatar: null,
            },
          }),
        ]),
      )
      const wrapper = await mountPage()

      expect(wrapper.findAll('main th').map((th) => th.text())).toContain('Member')
      expect(rowTexts(wrapper)[0]).toContain('Jamie Morgan')
      expect(rowTexts(wrapper)[0]).toContain('jamie@example.com')

      wrapper.unmount()
    })
  })

  describe('expanded rows', () => {
    it('shows where the money moved and the category, toggled from the row and the chevron', async () => {
      const wrapper = await mountPage()

      expect(wrapper.text()).not.toContain('Transaction details')
      await wrapper.findAll('main tbody tr[data-selectable="true"]')[0]!.trigger('click')
      expect(wrapper.text()).toContain('Transaction details')
      expect(wrapper.text()).toContain('Daily account')
      expect(wrapper.text()).toContain('Living expenses')
      expect(wrapper.text()).toContain('Category: Groceries')
      // A plain one-currency, two-account transaction needs no per-split amounts.
      expect(wrapper.text()).not.toContain('+€46.80')

      const toggle = wrapper.get('button[aria-label="Hide details of Grocery market"]')
      expect(toggle.attributes('aria-expanded')).toBe('true')
      await toggle.trigger('click')
      expect(wrapper.text()).not.toContain('Transaction details')

      wrapper.unmount()
    })

    it('expands and collapses every shown row from the header toggle', async () => {
      const wrapper = await mountPage()
      const rowCount = wrapper.findAll('main tbody tr[data-selectable="true"]').length
      const countDetails = () => wrapper.text().split('Transaction details').length - 1

      const expandAll = wrapper.get('button[aria-label="Expand all rows"]')
      expect(expandAll.attributes('aria-expanded')).toBe('false')
      await expandAll.trigger('click')
      expect(rowCount).toBeGreaterThan(0)
      expect(countDetails()).toBe(rowCount)

      const collapseAll = wrapper.get('button[aria-label="Collapse all rows"]')
      expect(collapseAll.attributes('aria-expanded')).toBe('true')
      await collapseAll.trigger('click')
      expect(countDetails()).toBe(0)

      wrapper.unmount()
    })

    it('lists every split with its own currency for mixed-currency transactions', async () => {
      mocks.listTxns.mockResolvedValue(
        page([
          txn({
            currency: 'USD',
            splits: [
              split('usd', 'USD cash', 'CREDIT', 1000, { accountCurrency: 'USD' }),
              split('eur-spend', 'EUR spending', 'DEBIT', 920, { exchangeRate: '0.92' }),
            ],
          }),
        ]),
      )
      const wrapper = await mountPage()

      await wrapper.get('button[aria-label="Show details of Grocery market"]').trigger('click')
      expect(wrapper.text()).toContain('-$10.00')
      expect(wrapper.text()).toContain('+€9.20')

      wrapper.unmount()
    })

    it('falls back to the ledger snapshot name for accounts that are no longer listed', async () => {
      mocks.listTxns.mockResolvedValue(
        page([txn({ splits: [split('gone', 'Old Checking', 'CREDIT', 1000)] })]),
      )
      const wrapper = await mountPage()

      await wrapper.get('button[aria-label="Show details of Grocery market"]').trigger('click')
      expect(wrapper.text()).toContain('Old Checking')
      expect(wrapper.text()).not.toContain('gone')

      wrapper.unmount()
    })
  })

  describe('delete', () => {
    it('asks for confirmation, then deletes and reloads', async () => {
      mocks.deleteTxn.mockResolvedValue(undefined)
      const wrapper = await mountPage()

      await click(wrapper.get('button[aria-label="Delete Grocery market"]').element)
      const dialog = dialogByTitle('Delete transaction "Grocery market"?')
      expect(mocks.deleteTxn).not.toHaveBeenCalled()

      await click(buttonIn(dialog, 'Cancel'))
      expect(dialogs()).toHaveLength(0)
      expect(mocks.deleteTxn).not.toHaveBeenCalled()

      await click(wrapper.get('button[aria-label="Delete Grocery market"]').element)
      await click(buttonIn(dialogByTitle('Delete transaction'), 'Delete transaction'))

      expect(mocks.deleteTxn).toHaveBeenCalledWith('t1')
      expect(mocks.listTxns).toHaveBeenCalledTimes(2)
      expect(dialogs()).toHaveLength(0)

      wrapper.unmount()
    })

    it('shows the server message when deleting fails', async () => {
      mocks.deleteTxn.mockRejectedValue(httpError('Cannot delete a reconciled transaction', 409))
      const wrapper = await mountPage()

      await click(wrapper.get('button[aria-label="Delete Grocery market"]').element)
      await click(buttonIn(dialogByTitle('Delete transaction'), 'Delete transaction'))

      expect(wrapper.get('[role="alert"]').text()).toContain(
        'Cannot delete a reconciled transaction',
      )
      expect(mocks.listTxns).toHaveBeenCalledTimes(1)

      wrapper.unmount()
    })

    it('falls back to the generic message for other failures', async () => {
      mocks.deleteTxn.mockRejectedValue(new Error('offline'))
      const wrapper = await mountPage()

      await click(wrapper.get('button[aria-label="Delete Grocery market"]').element)
      await click(buttonIn(dialogByTitle('Delete transaction'), 'Delete transaction'))

      expect(wrapper.get('[role="alert"]').text()).toContain('Failed to delete transaction.')

      wrapper.unmount()
    })
  })

  describe('create', () => {
    async function openCreate(wrapper: VueWrapper) {
      await click(buttonIn(wrapper.element as HTMLElement, 'Add transaction'))
      return dialogByTitle('Create Transaction')
    }

    async function fillExpense(wrapper: VueWrapper, amountMinor = 4680) {
      const selects = wrapper.findAllComponents(AccountSelect)
      const payFrom = selects.find((select) => select.props('allowedTypes')?.includes('LIABILITY'))!
      const expense = selects.find((select) => select.props('allowedTypes')?.[0] === 'EXPENSE')!
      payFrom.vm.$emit('update:modelValue', 'daily')
      expense.vm.$emit('update:modelValue', 'living')
      wrapper.findComponent(MoneyInput).vm.$emit('update:modelValue', amountMinor)
      await flushPromises()
    }

    it('opens a modal with the four types and a disabled submit until the draft is valid', async () => {
      const wrapper = await mountPage()

      const dialog = await openCreate(wrapper)
      expect(dialog.textContent).toContain('Record an expense, income, transfer, or debt payment.')
      expect(
        [...dialog.querySelectorAll('[role="tab"]')].map((tab) => tab.textContent?.trim()),
      ).toEqual(['Expense', 'Income', 'Transfer', 'Debt Payment'])
      expect(dialog.querySelector('[role="group"]')?.getAttribute('aria-label')).toBe(
        'Transaction type',
      )
      expect(buttonIn(dialog, 'Create Transaction').disabled).toBe(true)

      await fillExpense(wrapper)
      expect(buttonIn(dialog, 'Create Transaction').disabled).toBe(false)

      wrapper.unmount()
    })

    it('creates an expense with the currency of the pay-from account, closes and reloads', async () => {
      mocks.createTxn.mockResolvedValue(txn({ id: 'new' }))
      const wrapper = await mountPage()
      const dialog = await openCreate(wrapper)
      await fillExpense(wrapper)

      await click(buttonIn(dialog, 'Create Transaction'))

      expect(mocks.createTxn).toHaveBeenCalledWith({
        mode: 'INDIVIDUAL',
        householdId: null,
        txnDate: '2026-09-20',
        currency: 'EUR',
        description: '',
        splits: [
          { accountId: 'daily', side: 'CREDIT', amountMinor: 4680 },
          { accountId: 'living', side: 'DEBIT', amountMinor: 4680, categoryTagId: null },
        ],
      })
      expect(dialogs()).toHaveLength(0)
      expect(mocks.listTxns).toHaveBeenCalledTimes(2)

      wrapper.unmount()
    })

    it('shows the server message at the top of the form and stays open', async () => {
      mocks.createTxn.mockRejectedValue(httpError('Account is archived'))
      const wrapper = await mountPage()
      const dialog = await openCreate(wrapper)
      await fillExpense(wrapper)

      await click(buttonIn(dialog, 'Create Transaction'))

      expect(dialog.querySelector('form > [role="alert"]')?.textContent).toContain(
        'Account is archived',
      )
      expect(dialogs()).toHaveLength(1)
      expect(mocks.listTxns).toHaveBeenCalledTimes(1)

      mocks.createTxn.mockRejectedValue(new Error('offline'))
      await click(buttonIn(dialog, 'Create Transaction'))
      expect(dialog.querySelector('form > [role="alert"]')?.textContent).toContain(
        'Failed to create transaction.',
      )

      wrapper.unmount()
    })

    it('starts every new transaction from an empty draft', async () => {
      const wrapper = await mountPage()
      let dialog = await openCreate(wrapper)
      await fillExpense(wrapper)
      expect(buttonIn(dialog, 'Create Transaction').disabled).toBe(false)

      await click(buttonIn(dialog, 'Cancel'))
      dialog = await openCreate(wrapper)

      expect(buttonIn(dialog, 'Create Transaction').disabled).toBe(true)

      wrapper.unmount()
    })

    it('does not close on a backdrop click but closes on Escape', async () => {
      const wrapper = await mountPage()
      await openCreate(wrapper)

      document.body.querySelector<HTMLElement>('[data-slot="overlay"]')?.click()
      await flushPromises()
      expect(dialogs()).toHaveLength(1)

      document.activeElement?.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }),
      )
      await flushPromises()
      expect(dialogs()).toHaveLength(0)

      wrapper.unmount()
    })
  })

  describe('mobile', () => {
    beforeEach(() => {
      mocks.desktop.value = false
    })

    it('groups transactions by day in a list instead of a table', async () => {
      const wrapper = await mountPage()

      expect(wrapper.find('main table').exists()).toBe(false)
      expect(wrapper.findAll('main section h2').map((heading) => heading.text())).toEqual([
        'Today',
        'Yesterday',
        'Earlier this month',
      ])
      expect(wrapper.findAll('main section li').map((row) => row.find('button').text())).toEqual([
        expect.stringContaining('Grocery market'),
        expect.stringContaining('Savings transfer'),
        expect.stringContaining('September salary'),
      ])
      expect(wrapper.text()).toContain('1–3 of 3')
      expect(wrapper.find('button[aria-label="Filters"]').exists()).toBe(true)

      wrapper.unmount()
    })

    it('expands a row in place and deletes from its detail', async () => {
      mocks.deleteTxn.mockResolvedValue(undefined)
      const wrapper = await mountPage()

      const row = wrapper.findAll('main section li button')[0]!
      expect(row.attributes('aria-expanded')).toBe('false')
      await row.trigger('click')
      expect(row.attributes('aria-expanded')).toBe('true')
      expect(wrapper.text()).toContain('Category: Groceries')
      // Today's group heading already names the day, so the detail does not repeat the date.
      expect(wrapper.text()).not.toContain('20/09/2026')

      await click(wrapper.get('button[aria-label="Delete Grocery market"]').element)
      await click(buttonIn(dialogByTitle('Delete transaction'), 'Delete transaction'))
      expect(mocks.deleteTxn).toHaveBeenCalledWith('t1')

      wrapper.unmount()
    })

    it('shows who added the transaction next to its details in household mode', async () => {
      useModeStore().switchToHousehold('h1')
      mocks.listHouseholds.mockResolvedValue([
        {
          id: 'h1',
          name: 'Morgan household',
          role: 'OWNER',
          status: 'ACTIVE',
          rolloverDay: 1,
          createdAt: '',
        },
      ])
      mocks.listTxns.mockResolvedValue(
        page([
          txn({
            createdBy: {
              firstName: 'Jamie',
              lastName: 'Morgan',
              email: 'jamie@example.com',
              avatar: null,
            },
          }),
        ]),
      )
      const wrapper = await mountPage()

      expect(wrapper.text()).not.toContain('Jamie Morgan')
      await wrapper.findAll('main section li button')[0]!.trigger('click')
      expect(wrapper.get('main section li').text()).toContain('Jamie Morgan')

      wrapper.unmount()
    })

    it('opens the filters in place, applies them at once, counts them and clears them', async () => {
      const wrapper = await mountPage()
      const toggle = buttonIn(wrapper.element as HTMLElement, 'Filters')

      expect(wrapper.findComponent(AccountSelect).exists()).toBe(false)
      await openFilters(wrapper)
      expect(dialogs()).toHaveLength(0)

      wrapper.findComponent(AccountSelect).vm.$emit('update:modelValue', 'old')
      await flushPromises()
      expect(lastListCall()).toMatchObject({ accountId: 'old', page: 0 })
      await wrapper.get('input[type="search"]').setValue('salary')
      expect(toggle.textContent).toContain('2')

      await click(buttonIn(wrapper.element as HTMLElement, 'Clear filters'))
      expect(lastListCall()).not.toHaveProperty('accountId')
      expect((wrapper.get('input[type="search"]').element as HTMLInputElement).value).toBe('')
      expect(toggle.textContent).not.toContain('2')

      // Closing the panel keeps the filters; the button still counts them.
      wrapper.findComponent(CategorySelect).vm.$emit('update:modelValue', 'c2')
      await flushPromises()
      await click(toggle)
      expect(wrapper.findComponent(CategorySelect).exists()).toBe(false)
      expect(lastListCall()).toMatchObject({ categoryId: 'c2' })
      expect(toggle.textContent).toContain('1')

      wrapper.unmount()
    })
  })
})
