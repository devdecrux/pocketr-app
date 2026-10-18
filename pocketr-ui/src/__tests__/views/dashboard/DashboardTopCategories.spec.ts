import { describe, expect, it } from 'vitest'
import { h } from 'vue'
import { useCurrencyStore } from '@/stores/currency'
import type { MonthlyReportEntry } from '@/types/ledger'
import DashboardTopCategories from '@/views/dashboard/DashboardTopCategories.vue'
import { buildReportEntry, CURRENCIES } from '@/__tests__/support/fixtures'
import { mountInApp } from '@/__tests__/support/mount'

const props = {
  periodEntries: [] as MonthlyReportEntry[],
  periodLoading: false,
  lifetimeEntries: [] as MonthlyReportEntry[],
  lifetimeLoading: false,
}

function mountWith(periodEntries: MonthlyReportEntry[]) {
  useCurrencyStore().currencies = CURRENCIES
  return mountInApp(() => h(DashboardTopCategories, { ...props, periodEntries }))
}

describe('DashboardTopCategories', () => {
  it('merges entries of one category, ranks them and keeps five', () => {
    const wrapper = mountWith([
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
    ])

    // Each row starts with its category name, so the first character identifies the category.
    const names = wrapper.findAll('li').map((row) => row.text().charAt(0))
    expect(names).toEqual(['B', 'A', 'F', 'E', 'D'])
    expect(wrapper.text()).toContain('€30')
    expect(wrapper.text()).toContain('€15')
  })

  it('ignores entries that are not in the chart currency', () => {
    const wrapper = mountWith([
      buildReportEntry({ netMinor: 1000 }),
      buildReportEntry({
        categoryTagId: 'u',
        categoryTagName: 'Travel',
        currency: 'USD',
        netMinor: 9000,
      }),
    ])

    expect(wrapper.text()).not.toContain('Travel')
  })
})
