import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import UDashboardGroup from '@nuxt/ui/components/DashboardGroup.vue'
import USwitch from '@nuxt/ui/components/Switch.vue'
import { i18n } from '@/i18n'
import AppSelect from '@/components/fields/AppSelect.vue'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { useModeStore } from '@/stores/mode'
import type { LedgerTxn } from '@/types/ledger'
import DashboardPage from '@/views/dashboard/DashboardPage.vue'
import { buildAccount, buildReportEntry } from '@/__tests__/helpers/factories'
import { stubLocalStorage, stubResizeObserver } from '@/__tests__/helpers/stubs'
import { viewport } from '@/__tests__/helpers/vueuse'

const mocks = vi.hoisted(() => ({
  listAccounts: vi.fn(),
  listCurrencies: vi.fn(),
  listHouseholds: vi.fn(),
  listSharedAccounts: vi.fn(),
  getAccountBalances: vi.fn(),
  listTxns: vi.fn(),
  getMonthlyReport: vi.fn(),
  getLifetimeExpenseReport: vi.fn(),
}))

vi.mock('@vueuse/core', async () => (await import('@/__tests__/helpers/vueuse')).mockVueuse())

// ECharts cannot draw in jsdom; the stub exposes the props the page hands to the real chart.
vi.mock('@/views/dashboard/SpendingTrendChart.vue', async () => {
  const { defineComponent: define, h: render } = await vi.importActual<typeof import('vue')>('vue')
  return {
    __esModule: true,
    default: define({
      props: ['labels', 'values', 'formatValue', 'formatAxis'],
      setup:
        (props: {
          labels: string[]
          values: number[]
          formatValue: (value: number) => string
          formatAxis: (value: number) => string
        }) =>
        () =>
          render('div', {
            'data-test': 'trend-chart',
            'data-labels': props.labels.join(','),
            'data-values': props.values.join(','),
            'data-formatted': props.formatValue(props.values[props.values.length - 1] ?? 0),
            'data-axis': props.formatAxis(1500),
          }),
    }),
  }
})

vi.mock('@/api/csrf', () => ({ primeCsrfToken: vi.fn() }))
vi.mock('@/api/http', () => ({ api: { post: vi.fn(), get: vi.fn() } }))
vi.mock('@/api/accounts', () => ({ listAccounts: mocks.listAccounts }))
vi.mock('@/api/currencies', () => ({ listCurrencies: mocks.listCurrencies }))
vi.mock('@/api/households', () => ({
  listHouseholds: mocks.listHouseholds,
  listSharedAccounts: mocks.listSharedAccounts,
}))
vi.mock('@/api/ledger', () => ({
  getAccountBalances: mocks.getAccountBalances,
  listTxns: mocks.listTxns,
}))
vi.mock('@/api/reports', () => ({
  getMonthlyReport: mocks.getMonthlyReport,
  getLifetimeExpenseReport: mocks.getLifetimeExpenseReport,
}))

function expense(id: string, description: string, txnDate: string, amountMinor: number): LedgerTxn {
  return {
    id,
    txnDate,
    description,
    currency: 'EUR',
    txnKind: 'STANDARD',
    splits: [
      {
        accountId: 'exp-1',
        accountName: 'Groceries',
        accountType: 'EXPENSE',
        accountCurrency: 'EUR',
        side: 'DEBIT',
        amountMinor,
        exchangeRate: '1',
        categoryTagName: 'Food',
      },
    ],
    createdAt: `${txnDate}T10:00:00Z`,
    updatedAt: `${txnDate}T10:00:00Z`,
  }
}

const ACCOUNTS = [
  buildAccount({ id: 'daily', name: 'Daily account' }),
  buildAccount({ id: 'savings', name: 'Savings' }),
  buildAccount({ id: 'usd', name: 'USD cash', currency: 'USD' }),
  buildAccount({ id: 'living', name: 'Living expenses', type: 'EXPENSE' }),
]

const BALANCES: Record<string, { balanceMinor: number; currency: string }> = {
  daily: { balanceMinor: 184500, currency: 'EUR' },
  savings: { balanceMinor: 100000, currency: 'EUR' },
  usd: { balanceMinor: 5000, currency: 'USD' },
}

// The current period (fake "today" is 2026-09-20, rollover day 1) spends 650 + 285.50 EUR + 40 USD.
function reportFor(period: string) {
  const entries =
    period === '2026-09'
      ? [
          buildReportEntry({ netMinor: 65000 }),
          buildReportEntry({
            categoryTagId: 'cat-2',
            categoryTagName: 'Transport',
            categoryTagColor: '#8391a5',
            netMinor: 28550,
          }),
          buildReportEntry({
            categoryTagId: 'cat-3',
            categoryTagName: 'Travel',
            currency: 'USD',
            netMinor: 4000,
          }),
        ]
      : period === '2026-08'
        ? [buildReportEntry({ netMinor: 12000 })]
        : []
  return { periodStart: `${period}-01`, periodEnd: `${period}-28`, entries }
}

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/dashboard', name: 'dashboard', component: DashboardPage },
      { path: '/transactions', name: 'transactions', component: { render: () => null } },
    ],
  })
  await router.push('/dashboard')
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

function selectByLabel(wrapper: VueWrapper, label: string) {
  const match = wrapper
    .findAllComponents(AppSelect)
    .find((select) => select.props('ariaLabel') === label)
  if (!match) throw new Error(`No select "${label}"`)
  return match
}

async function choose(wrapper: VueWrapper, label: string, value: string | number) {
  selectByLabel(wrapper, label).vm.$emit('update:modelValue', value)
  await flushPromises()
}

function mainText(wrapper: VueWrapper): string {
  return wrapper.find('main').text()
}

function chart(wrapper: VueWrapper) {
  return wrapper.find('[data-test="trend-chart"]')
}

function reportedPeriods(): string[] {
  return mocks.getMonthlyReport.mock.calls.map((call) => (call[0] as { period: string }).period)
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(2026, 8, 20, 12) })
    setActivePinia(createPinia())
    stubLocalStorage()
    stubResizeObserver()
    viewport.desktop = true
    for (const mock of Object.values(mocks)) {
      if (typeof mock === 'function') mock.mockReset()
    }
    mocks.listAccounts.mockResolvedValue(ACCOUNTS)
    mocks.listCurrencies.mockResolvedValue([
      { code: 'EUR', minorUnit: 2, name: 'Euro' },
      { code: 'USD', minorUnit: 2, name: 'US Dollar' },
    ])
    mocks.listHouseholds.mockResolvedValue([])
    mocks.listSharedAccounts.mockResolvedValue([])
    mocks.getAccountBalances.mockImplementation(async (ids: string[]) =>
      ids.map((id) => ({
        accountId: id,
        accountName: id,
        accountType: 'ASSET',
        currency: BALANCES[id]?.currency ?? 'EUR',
        balanceMinor: BALANCES[id]?.balanceMinor ?? 0,
        asOf: '2026-09-20',
      })),
    )
    mocks.listTxns.mockResolvedValue({
      content: [
        expense('t1', 'Weekly shop', '2026-09-20', 4250),
        expense('t2', 'Metro card', '2026-09-19', 1500),
        expense('t3', 'Cinema', '2026-09-10', 1200),
      ],
    })
    mocks.getMonthlyReport.mockImplementation(async ({ period }: { period: string }) =>
      reportFor(period),
    )
    mocks.getLifetimeExpenseReport.mockResolvedValue([
      buildReportEntry({ categoryTagId: 'cat-9', categoryTagName: 'Rent', netMinor: 900000 }),
      buildReportEntry({ netMinor: 12345 }),
    ])
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
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  describe('personal mode', () => {
    it('shows the heading, the new-transaction links and the period select', async () => {
      const wrapper = await mountPage()

      expect(wrapper.findAll('h1').length).toBeGreaterThan(0)
      expect(mainText(wrapper)).toContain('Your money, at a glance')
      const links = wrapper.findAll('a[href="/transactions"]')
      expect(links.map((link) => link.text())).toContain('Transaction')
      expect(links.map((link) => link.attributes('aria-label'))).toContain('New transaction')
      expect(selectByLabel(wrapper, 'Select period').props('modelValue')).toBe('2026-09')
      const items = selectByLabel(wrapper, 'Select period').props('items') as { value: string }[]
      expect(items).toHaveLength(24)
      expect(items[0]?.value).toBe('2026-09')
    })

    it('sums the active asset accounts per currency, primary currency first', async () => {
      const wrapper = await mountPage()

      const text = mainText(wrapper)
      expect(text).toContain('Available balance')
      expect(text).toContain('Across 3 accounts')
      expect(text).toContain('2,845.00')
      expect(text).toContain('50.00')
      expect(mocks.getAccountBalances).toHaveBeenCalledWith(
        ['daily', 'savings', 'usd'],
        undefined,
        undefined,
      )
    })

    it('shows the current period spending with its secondary currency', async () => {
      const wrapper = await mountPage()

      const text = mainText(wrapper)
      expect(text).toContain('Spent this month')
      expect(text).toContain('935.50')
      expect(text).toContain('40.00')
      expect(reportedPeriods().sort()).toEqual([
        '2026-04',
        '2026-05',
        '2026-06',
        '2026-07',
        '2026-08',
        '2026-09',
      ])
    })

    it('feeds the lazy chart with one point per period of the primary currency', async () => {
      const wrapper = await mountPage()

      expect(wrapper.text()).toContain('Spending over time')
      const rendered = chart(wrapper)
      expect(rendered.exists()).toBe(true)
      expect(rendered.attributes('data-values')).toBe('0,0,0,0,120,935.5')
      expect(rendered.attributes('data-labels')?.split(',')).toHaveLength(6)
      expect(rendered.attributes('data-formatted')).toContain('935.50')
      expect(rendered.attributes('data-axis')).toContain('1,500')
    })

    it('lists the recent expenses with category, relative date and a negative amount', async () => {
      const wrapper = await mountPage()

      const text = mainText(wrapper)
      expect(text).toContain('Recent expenses')
      expect(text).toContain('Weekly shop')
      expect(text).toContain('Groceries · Today')
      expect(text).toContain('Groceries · Yesterday')
      expect(text).toContain('−€42.50')
      expect(text).toContain('View all')
      expect(mocks.listTxns).toHaveBeenCalledWith({
        mode: 'INDIVIDUAL',
        householdId: undefined,
        spendingOnly: true,
        page: 0,
        size: 10,
      })
    })

    it('ranks the top categories of the current period and trims trailing zero cents', async () => {
      const wrapper = await mountPage()

      const text = mainText(wrapper)
      expect(text).toContain('Top categories')
      expect(text.indexOf('Groceries')).toBeLessThan(text.indexOf('Transport'))
      expect(text).toContain('€650')
      expect(text).toContain('€285.50')
      expect(text).not.toContain('Travel')
    })

    it('switches the top categories to the lifetime report', async () => {
      const wrapper = await mountPage()

      wrapper.findComponent(USwitch).vm.$emit('update:modelValue', 'lifetime')
      await flushPromises()

      const text = mainText(wrapper)
      expect(text).toContain('Rent')
      expect(text).toContain('€9,000')
      expect(text).toContain('€123.45')
      expect(text).not.toContain('Transport')
    })

    it('reloads the reports for another period and renames the spent card', async () => {
      const wrapper = await mountPage()
      mocks.getMonthlyReport.mockClear()

      await choose(wrapper, 'Select period', '2026-08')

      expect(reportedPeriods().sort()).toEqual([
        '2026-03',
        '2026-04',
        '2026-05',
        '2026-06',
        '2026-07',
        '2026-08',
      ])
      const text = mainText(wrapper)
      expect(text).toContain('Spent in August 2026')
      expect(text).toContain('120.00')
      expect(selectByLabel(wrapper, 'Select period').props('modelValue')).toBe('2026-08')
    })

    it('reloads the chart for another period count and remembers it', async () => {
      const wrapper = await mountPage()
      mocks.getMonthlyReport.mockClear()

      await choose(wrapper, 'Spending chart period', 12)

      expect(mocks.getMonthlyReport).toHaveBeenCalledTimes(12)
      expect(chart(wrapper).attributes('data-labels')?.split(',')).toHaveLength(12)
      expect(selectByLabel(wrapper, 'Spending chart period').props('modelValue')).toBe(12)
      expect(localStorage.getItem('pocketr-dashboard-period-count')).toBe('12')
    })

    it('ignores a period count that is not offered', async () => {
      const wrapper = await mountPage()
      mocks.getMonthlyReport.mockClear()

      await choose(wrapper, 'Spending chart period', 7)

      expect(mocks.getMonthlyReport).not.toHaveBeenCalled()
    })
  })

  describe('states', () => {
    it('shows skeletons while the data loads', async () => {
      mocks.getMonthlyReport.mockReturnValue(new Promise(() => {}))
      mocks.listTxns.mockReturnValue(new Promise(() => {}))
      const wrapper = await mountPage()

      expect(wrapper.findAll('main .animate-pulse').length).toBeGreaterThan(0)
      expect(chart(wrapper).exists()).toBe(false)
      expect(mainText(wrapper)).not.toContain('No transactions yet.')
    })

    it('shows the empty messages when there is no data', async () => {
      mocks.listAccounts.mockResolvedValue([])
      mocks.listTxns.mockResolvedValue({ content: [] })
      mocks.getMonthlyReport.mockResolvedValue(reportFor('none'))
      mocks.getLifetimeExpenseReport.mockResolvedValue([])
      const wrapper = await mountPage()

      const text = mainText(wrapper)
      expect(text).toContain('No accounts yet.')
      expect(text).toContain('No spendings yet.')
      expect(text).toContain('No transactions yet.')
      expect(text.match(/No spending data for this month\./g)).toHaveLength(2)
      expect(chart(wrapper).exists()).toBe(false)
    })

    it('shows the no-data messages when the report request fails', async () => {
      mocks.getMonthlyReport.mockRejectedValue(new Error('boom'))
      const wrapper = await mountPage()

      const text = mainText(wrapper)
      expect(text).toContain('No data available.')
      expect(text.match(/No spending data for this month\./g)).toHaveLength(2)
      expect(chart(wrapper).exists()).toBe(false)
      expect(text).toContain('Weekly shop')
    })

    it('shows the empty balance and recent-expense messages when those requests fail', async () => {
      mocks.getAccountBalances.mockRejectedValue(new Error('boom'))
      mocks.listTxns.mockRejectedValue(new Error('boom'))
      const wrapper = await mountPage()

      const text = mainText(wrapper)
      expect(text).toContain('Available balance')
      expect(text).toContain('0.00')
      expect(text).toContain('No transactions yet.')
    })
  })

  describe('household mode', () => {
    beforeEach(() => {
      useModeStore().switchToHousehold('hh-1')
      const household = { id: 'hh-1', name: 'Home', status: 'ACTIVE', rolloverDay: 15 }
      mocks.listHouseholds.mockResolvedValue([household])
      useHouseholdStore().households = [household as never]
      mocks.listSharedAccounts.mockResolvedValue([{ accountId: 'savings' }])
    })

    it('counts only the shared asset accounts and queries in household mode', async () => {
      const wrapper = await mountPage()

      const text = mainText(wrapper)
      expect(text).toContain('Household overview')
      expect(text).toContain('Across 1 account')
      expect(text).toContain('1,000.00')
      expect(mocks.getAccountBalances).toHaveBeenCalledWith(['savings'], undefined, 'hh-1')
      expect(mocks.getMonthlyReport).toHaveBeenCalledWith(
        expect.objectContaining({ mode: 'HOUSEHOLD', householdId: 'hh-1' }),
      )
      expect(mocks.listTxns).toHaveBeenCalledWith(
        expect.objectContaining({ mode: 'HOUSEHOLD', householdId: 'hh-1' }),
      )
      expect(mocks.getLifetimeExpenseReport).toHaveBeenCalledWith({
        mode: 'HOUSEHOLD',
        householdId: 'hh-1',
      })
    })

    it('uses the household rollover day for the current period', async () => {
      // 2026-09-10 is before the 15th: the household period still started in August.
      vi.setSystemTime(new Date(2026, 8, 10, 12))
      const wrapper = await mountPage()

      expect(selectByLabel(wrapper, 'Select period').props('modelValue')).toBe('2026-08')
    })
  })
})
