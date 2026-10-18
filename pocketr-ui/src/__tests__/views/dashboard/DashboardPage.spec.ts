import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, type VueWrapper } from '@vue/test-utils'
import USwitch from '@nuxt/ui/components/Switch.vue'
import { listAccounts } from '@/api/accounts'
import { listHouseholds, listSharedAccounts } from '@/api/households'
import { getAccountBalances, listTxns } from '@/api/ledger'
import { getLifetimeExpenseReport, getMonthlyReport } from '@/api/reports'
import AppSelect from '@/components/fields/AppSelect.vue'
import { useHouseholdStore } from '@/stores/household'
import { useModeStore } from '@/stores/mode'
import DashboardPage from '@/views/dashboard/DashboardPage.vue'
import {
  ACCOUNTS,
  balanceRows,
  buildHouseholdSummary,
  buildReportEntry,
  buildShare,
  buildSplit,
  buildTxn,
  freezeToday,
  signIn,
  txnPage,
} from '@/__tests__/support/fixtures'
import { chooseOption } from '@/__tests__/support/dom'
import { mountRoute } from '@/__tests__/support/mount'

// ECharts cannot draw in jsdom; the stub exposes the props the page hands to the real chart.
vi.mock('@/views/dashboard/SpendingTrendChart.vue', async () => {
  const { defineComponent, h } = await vi.importActual<typeof import('vue')>('vue')
  return {
    __esModule: true,
    default: defineComponent({
      props: ['labels', 'values', 'formatValue', 'formatAxis'],
      setup:
        (props: {
          labels: string[]
          values: number[]
          formatValue: (value: number) => string
          formatAxis: (value: number) => string
        }) =>
        () =>
          h('div', {
            'data-test': 'trend-chart',
            'data-labels': props.labels.join(','),
            'data-values': props.values.join(','),
            'data-formatted': props.formatValue(props.values[props.values.length - 1] ?? 0),
            'data-axis': props.formatAxis(1500),
          }),
    }),
  }
})

const DASHBOARD_ACCOUNTS = ACCOUNTS.filter((account) =>
  ['daily', 'savings', 'usd', 'living'].includes(account.id),
)

function expense(id: string, description: string, txnDate: string, amountMinor: number) {
  return buildTxn({
    id,
    description,
    txnDate,
    txnKind: 'STANDARD',
    splits: [
      buildSplit({
        accountId: 'exp-1',
        accountName: 'Groceries',
        accountType: 'EXPENSE',
        side: 'DEBIT',
        amountMinor,
        categoryTagName: 'Food',
      }),
    ],
  })
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
  const { wrapper } = await mountRoute(DashboardPage, {
    path: '/dashboard',
    routes: [{ path: '/transactions', component: { render: () => null } }],
  })
  return wrapper
}

const selectByLabel = (wrapper: VueWrapper, label: string) =>
  wrapper.findAllComponents(AppSelect).find((select) => select.props('ariaLabel') === label)!

const mainText = (wrapper: VueWrapper) => wrapper.find('main').text()
const chart = (wrapper: VueWrapper) => wrapper.find('[data-test="trend-chart"]')
const reportedPeriods = () =>
  vi
    .mocked(getMonthlyReport)
    .mock.calls.map(([params]) => params.period)
    .sort()

describe('DashboardPage', () => {
  // The chart is an async component: import it up front. When the first lazy load starts inside a
  // test that ends first, later tests never get the chart.
  beforeAll(async () => {
    await import('@/views/dashboard/SpendingTrendChart.vue')
  })

  beforeEach(() => {
    freezeToday('2026-09-20T12:00:00')
    signIn()
    vi.mocked(listAccounts).mockResolvedValue(DASHBOARD_ACCOUNTS)
    vi.mocked(getAccountBalances).mockImplementation(async (ids) => balanceRows(ids))
    vi.mocked(listTxns).mockResolvedValue(
      txnPage([
        expense('t1', 'Weekly shop', '2026-09-20', 4250),
        expense('t2', 'Metro card', '2026-09-19', 1500),
        expense('t3', 'Cinema', '2026-09-10', 1200),
      ]),
    )
    vi.mocked(getMonthlyReport).mockImplementation(async ({ period }) => reportFor(period))
    vi.mocked(getLifetimeExpenseReport).mockResolvedValue([
      buildReportEntry({ categoryTagId: 'cat-9', categoryTagName: 'Rent', netMinor: 900000 }),
      buildReportEntry({ netMinor: 12345 }),
    ])
  })

  describe('personal mode', () => {
    it('sums the active asset accounts per currency, primary currency first', async () => {
      const wrapper = await mountPage()

      const text = mainText(wrapper)
      expect(text).toContain('2,845.00')
      expect(text).toContain('50.00')
      expect(getAccountBalances).toHaveBeenCalledExactlyOnceWith(
        ['daily', 'savings', 'usd'],
        undefined,
        undefined,
      )
    })

    it('shows the current period spending with its secondary currency', async () => {
      const wrapper = await mountPage()

      const text = mainText(wrapper)
      expect(text).toContain('935.50')
      expect(text).toContain('40.00')
      expect(reportedPeriods()).toEqual([
        '2026-04',
        '2026-05',
        '2026-06',
        '2026-07',
        '2026-08',
        '2026-09',
      ])
    })

    it('feeds the chart with one point per period of the primary currency', async () => {
      const wrapper = await mountPage()

      const rendered = chart(wrapper)
      expect(rendered.attributes('data-values')).toBe('0,0,0,0,120,935.5')
      expect(rendered.attributes('data-formatted')).toContain('935.50')
    })

    it('requests the recent expenses and shows the amount as negative', async () => {
      const wrapper = await mountPage()

      const text = mainText(wrapper)
      expect(text).toContain('−€42.50')
      expect(listTxns).toHaveBeenCalledExactlyOnceWith({
        mode: 'INDIVIDUAL',
        householdId: undefined,
        spendingOnly: true,
        page: 0,
        size: 10,
      })
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

    it('reloads the reports for another period', async () => {
      const wrapper = await mountPage()
      vi.mocked(getMonthlyReport).mockClear()

      await chooseOption(wrapper, 'Select period', 'August 2026')

      expect(reportedPeriods()).toEqual([
        '2026-03',
        '2026-04',
        '2026-05',
        '2026-06',
        '2026-07',
        '2026-08',
      ])
      expect(mainText(wrapper)).toContain('120.00')
    })

    it('reloads the chart for another period count and remembers it', async () => {
      const wrapper = await mountPage()
      vi.mocked(getMonthlyReport).mockClear()

      await chooseOption(wrapper, 'Spending chart period', 'Last 12 months')

      expect(getMonthlyReport).toHaveBeenCalledTimes(12)
      expect(localStorage.getItem('pocketr-dashboard-period-count')).toBe('12')
    })
  })

  describe('states', () => {
    it('shows a zero balance and no chart when the reports, balances and expenses fail to load', async () => {
      vi.mocked(getMonthlyReport).mockRejectedValue(new Error('boom'))
      vi.mocked(getAccountBalances).mockRejectedValue(new Error('boom'))
      vi.mocked(listTxns).mockRejectedValue(new Error('boom'))

      const wrapper = await mountPage()

      const text = mainText(wrapper)
      expect(chart(wrapper).exists()).toBe(false)
      expect(text).toContain('0.00')
    })
  })

  describe('household mode', () => {
    beforeEach(() => {
      useModeStore().switchToHousehold('hh-1')
      const household = buildHouseholdSummary({ rolloverDay: 15 })
      vi.mocked(listHouseholds).mockResolvedValue([household])
      useHouseholdStore().households = [household]
      vi.mocked(listSharedAccounts).mockResolvedValue([buildShare('savings')])
    })

    it('counts only the shared asset accounts and queries everything in household mode', async () => {
      const wrapper = await mountPage()

      const text = mainText(wrapper)
      expect(text).toContain('1,000.00')
      expect(listSharedAccounts).toHaveBeenCalledExactlyOnceWith('hh-1')
      expect(getAccountBalances).toHaveBeenCalledExactlyOnceWith(['savings'], undefined, 'hh-1')
      expect(getMonthlyReport).toHaveBeenCalledWith({
        mode: 'HOUSEHOLD',
        householdId: 'hh-1',
        period: '2026-09',
      })
      expect(listTxns).toHaveBeenCalledExactlyOnceWith({
        mode: 'HOUSEHOLD',
        householdId: 'hh-1',
        spendingOnly: true,
        page: 0,
        size: 10,
      })
      expect(getLifetimeExpenseReport).toHaveBeenCalledExactlyOnceWith({
        mode: 'HOUSEHOLD',
        householdId: 'hh-1',
      })
    })

    it('uses the household rollover day for the current period', async () => {
      // 2026-09-10 is before the 15th: the household period still started in August.
      freezeToday('2026-09-10T12:00:00')

      const wrapper = await mountPage()

      expect(selectByLabel(wrapper, 'Select period').props('modelValue')).toBe('2026-08')
    })
  })
})
