import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { h } from 'vue'
import AppSelect from '@/components/fields/AppSelect.vue'
import { useCurrencyStore } from '@/stores/currency'
import type { MonthlyReportEntry } from '@/types/ledger'
import DashboardPeriodSelect from '@/views/dashboard/DashboardPeriodSelect.vue'
import DashboardSpendingChartCard from '@/views/dashboard/DashboardSpendingChartCard.vue'
import DashboardSpentCard from '@/views/dashboard/DashboardSpentCard.vue'
import DashboardStatCard from '@/views/dashboard/DashboardStatCard.vue'
import DashboardTopCategories from '@/views/dashboard/DashboardTopCategories.vue'
import { buildReportEntry } from '@/__tests__/helpers/factories'
import { mountInApp } from '@/__tests__/helpers/mount'
import { stubLocalStorage, stubResizeObserver } from '@/__tests__/helpers/stubs'
import { viewport } from '@/__tests__/helpers/vueuse'

vi.mock('@vueuse/core', async () => (await import('@/__tests__/helpers/vueuse')).mockVueuse())

vi.mock('@/views/dashboard/SpendingTrendChart.vue', () => ({
  __esModule: true,
  default: { render: () => null },
}))

describe('dashboard sections', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    viewport.desktop = false
    stubLocalStorage()
    stubResizeObserver()
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
  })

  describe('DashboardStatCard', () => {
    const base = {
      label: 'Available balance',
      icon: 'i-lucide-wallet',
      tone: 'success' as const,
      loading: false,
      emptyText: null,
      amounts: [
        { currency: 'USD', amountMinor: 5000 },
        { currency: 'EUR', amountMinor: 123456 },
      ],
      caption: 'Across 2 accounts',
    }

    it('shows the primary amount large and the other currencies as badges', () => {
      const wrapper = mountInApp(() => h(DashboardStatCard, base))

      const text = wrapper.text()
      expect(text).toContain('Available balance')
      expect(text).toContain('1,234.56')
      expect(text).toContain('Across 2 accounts')
      expect(wrapper.findAll('.text-\\[28px\\]')).toHaveLength(1)
      expect(wrapper.find('div.flex-wrap').text()).toContain('50.00')
    })

    it('shows a skeleton while loading and the empty text instead of an amount', () => {
      const loading = mountInApp(() => h(DashboardStatCard, { ...base, loading: true }))
      expect(loading.findAll('.animate-pulse')).toHaveLength(2)
      expect(loading.text()).not.toContain('1,234.56')

      const empty = mountInApp(() =>
        h(DashboardStatCard, { ...base, emptyText: 'No accounts yet.' }),
      )
      expect(empty.text()).toContain('No accounts yet.')
      expect(empty.text()).not.toContain('1,234.56')
    })

    it('uses the tone for the icon tile', () => {
      const wrapper = mountInApp(() => h(DashboardStatCard, { ...base, tone: 'error' }))

      expect(wrapper.find('span.rounded-full').classes()).toContain('bg-error/10')
    })
  })

  describe('DashboardPeriodSelect', () => {
    const items = [
      { value: '2026-09', label: 'September 2026', description: '01/09 - 30/09' },
      { value: '2026-08', label: 'August 2026', description: '01/08 - 31/08' },
    ]

    it('emits only string periods', async () => {
      const wrapper = mountInApp(() => h(DashboardPeriodSelect, { modelValue: '2026-09', items }))
      const select = wrapper.findComponent(AppSelect)

      expect(select.props('ariaLabel')).toBe('Select period')
      select.vm.$emit('update:modelValue', 7)
      select.vm.$emit('update:modelValue', '2026-08')
      await flushPromises()

      const emitted = wrapper.findComponent(DashboardPeriodSelect).emitted('update:modelValue')
      expect(emitted).toEqual([['2026-08']])
    })

    it.each([
      ['desktop', true, 'September 2026'],
      ['phone', false, 'Sep 2026'],
    ])('renders only the %s period label', (_label, desktop, text) => {
      viewport.desktop = desktop
      const wrapper = mountInApp(() => h(DashboardPeriodSelect, { modelValue: '2026-09', items }))

      expect(wrapper.text()).toContain(text)
      expect(wrapper.text()).not.toContain(desktop ? 'Sep 2026' : 'September 2026')
    })
  })

  describe('DashboardSpentCard', () => {
    const props = {
      entries: [] as MonthlyReportEntry[],
      hasReports: true,
      loading: false,
      period: '2026-09',
      currentPeriod: '2026-09',
    }

    it('names the card after this month or the chosen period', () => {
      const current = mountInApp(() => h(DashboardSpentCard, props))
      expect(current.text()).toContain('Spent this month')

      const past = mountInApp(() => h(DashboardSpentCard, { ...props, period: '2026-08' }))
      expect(past.text()).toContain('Spent in August 2026')
    })

    it('tells "no data" from "nothing spent"', () => {
      const none = mountInApp(() => h(DashboardSpentCard, { ...props, hasReports: false }))
      expect(none.text()).toContain('No data available.')

      const zero = mountInApp(() => h(DashboardSpentCard, props))
      expect(zero.text()).toContain('No spendings yet.')
    })

    it('sums the entries per currency', () => {
      const wrapper = mountInApp(() =>
        h(DashboardSpentCard, {
          ...props,
          entries: [
            buildReportEntry({ netMinor: 1000 }),
            buildReportEntry({ netMinor: 550 }),
            buildReportEntry({ currency: 'USD', netMinor: 300 }),
          ],
        }),
      )

      expect(wrapper.text()).toContain('15.50')
      expect(wrapper.text()).toContain('3.00')
    })
  })

  describe('DashboardSpendingChartCard', () => {
    const reports = [
      {
        period: '2026-08',
        report: { periodStart: '', periodEnd: '', entries: [buildReportEntry({ netMinor: 500 })] },
      },
      { period: '2026-09', report: { periodStart: '', periodEnd: '', entries: [] } },
    ]

    it('emits a period count only for the offered options', async () => {
      const wrapper = mountInApp(() =>
        h(DashboardSpendingChartCard, { reports, loading: false, periodCount: 6 }),
      )
      const select = wrapper.findComponent(AppSelect)

      expect(select.props('ariaLabel')).toBe('Spending chart period')
      select.vm.$emit('update:modelValue', 9)
      select.vm.$emit('update:modelValue', 12)
      await flushPromises()

      const card = wrapper.findComponent(DashboardSpendingChartCard)
      expect(card.emitted('update:periodCount')).toEqual([[12]])
    })

    it('shows the empty message when no period spent anything in the primary currency', () => {
      const wrapper = mountInApp(() =>
        h(DashboardSpendingChartCard, {
          reports: [
            {
              period: '2026-09',
              report: {
                periodStart: '',
                periodEnd: '',
                entries: [buildReportEntry({ currency: 'USD', netMinor: 900 })],
              },
            },
          ],
          loading: false,
          periodCount: 6,
        }),
      )

      expect(wrapper.text()).toContain('No spending data for this month.')
    })
  })

  describe('DashboardTopCategories', () => {
    beforeEach(() => {
      useCurrencyStore().currencies = [{ code: 'EUR', minorUnit: 2, name: 'Euro' }] as never
    })

    const props = {
      periodEntries: [] as MonthlyReportEntry[],
      periodLoading: false,
      lifetimeEntries: [] as MonthlyReportEntry[],
      lifetimeLoading: false,
    }

    it('merges entries of one category, ranks them and keeps five', () => {
      const wrapper = mountInApp(() =>
        h(DashboardTopCategories, {
          ...props,
          periodEntries: [
            buildReportEntry({ categoryTagId: 'a', categoryTagName: 'A', netMinor: 1000 }),
            buildReportEntry({ categoryTagId: 'a', categoryTagName: 'A', netMinor: 500 }),
            buildReportEntry({ categoryTagId: 'b', categoryTagName: 'B', netMinor: 3000 }),
            buildReportEntry({ categoryTagId: null, categoryTagName: null, netMinor: 100 }),
            ...['c', 'd', 'e', 'f'].map((id, index) =>
              buildReportEntry({
                categoryTagId: id,
                categoryTagName: id.toUpperCase(),
                netMinor: 200 + index,
              }),
            ),
          ],
        }),
      )

      const names = wrapper.findAll('ul li > span.truncate').map((name) => name.text())
      expect(names).toEqual(['B', 'A', 'F', 'E', 'D'])
      expect(wrapper.text()).toContain('€30')
      expect(wrapper.text()).toContain('€15')
    })

    it('labels entries without a category as Uncategorized', () => {
      const wrapper = mountInApp(() =>
        h(DashboardTopCategories, {
          ...props,
          periodEntries: [
            buildReportEntry({ categoryTagId: null, categoryTagName: null, netMinor: 250 }),
          ],
        }),
      )

      expect(wrapper.text()).toContain('Uncategorized')
      expect(wrapper.text()).toContain('€2.50')
    })

    it('ignores entries that are not in the chart currency', () => {
      const wrapper = mountInApp(() =>
        h(DashboardTopCategories, {
          ...props,
          periodEntries: [
            buildReportEntry({ netMinor: 1000 }),
            buildReportEntry({
              categoryTagId: 'u',
              categoryTagName: 'Travel',
              currency: 'USD',
              netMinor: 9000,
            }),
          ],
        }),
      )

      expect(wrapper.text()).not.toContain('Travel')
    })

    it('shows skeleton rows while the active view loads and the empty message otherwise', () => {
      const loading = mountInApp(() => h(DashboardTopCategories, { ...props, periodLoading: true }))
      expect(loading.findAll('.animate-pulse')).toHaveLength(4)

      const empty = mountInApp(() => h(DashboardTopCategories, props))
      expect(empty.text()).toContain('No spending data for this month.')
    })

    it('draws the bar in the category colour, falling back to the primary token', () => {
      const wrapper = mountInApp(() =>
        h(DashboardTopCategories, {
          ...props,
          periodEntries: [
            buildReportEntry({
              categoryTagId: 'a',
              categoryTagName: 'A',
              categoryTagColor: '#112233',
              netMinor: 1000,
            }),
            buildReportEntry({
              categoryTagId: 'b',
              categoryTagName: 'B',
              categoryTagColor: 'red',
              netMinor: 500,
            }),
          ],
        }),
      )

      const bars = wrapper.findAll('ul li span.h-full')
      expect(bars[0]?.attributes('style')).toContain('width: 100%')
      expect(bars[0]?.attributes('style')).toContain('rgb(17, 34, 51)')
      expect(bars[1]?.attributes('style')).toContain('width: 50%')
      expect(bars[1]?.attributes('style')).toContain('var(--ui-primary)')
    })
  })
})
