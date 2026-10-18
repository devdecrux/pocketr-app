import { beforeEach, describe, expect, it } from 'vitest'
import { defineComponent, h } from 'vue'
import USelect from '@nuxt/ui/components/Select.vue'
import AppSelect from '@/components/fields/AppSelect.vue'
import { type AccountOptionsFilter, useAccountOptions } from '@/components/fields/useAccountOptions'
import { useAccountStore } from '@/stores/account'
import { useModeStore } from '@/stores/mode'
import { buildAccount } from '@/__tests__/support/fixtures'
import { mountInApp } from '@/__tests__/support/mount'

/** An `AppSelect` configured the way the pages do, through the account item builder. */
const AccountSelect = defineComponent({
  inheritAttrs: false,
  setup(_, { attrs }) {
    const { accountSelect } = useAccountOptions()
    return () => h(AppSelect, { ...accountSelect(attrs as AccountOptionsFilter), ...attrs })
  },
})

function groupedLabels(attrs: Record<string, unknown>): string[][] {
  const wrapper = mountInApp(() => h(AccountSelect, attrs))
  const items = (wrapper.findComponent(USelect).props() as { items: Record<string, unknown>[][] })
    .items
  return items.map((group) => group.map((item) => item.label as string))
}

describe('account select options', () => {
  beforeEach(() => {
    useAccountStore().accounts = [
      buildAccount({ id: 'daily', name: 'Account daily' }),
      buildAccount({ id: 'card', name: 'Account card', type: 'LIABILITY' }),
      buildAccount({ id: 'living', name: 'Account living', type: 'EXPENSE' }),
      buildAccount({ id: 'equity', name: 'Account equity', type: 'EQUITY' }),
      buildAccount({ id: 'old', name: 'Account old', status: 'ARCHIVED' }),
      buildAccount({ id: 'jamie', name: 'Account jamie', ownerUserId: 2 }),
      buildAccount({ id: 'usd', name: 'Account usd', currency: 'USD' }),
    ]
  })

  it('lists active accounts of the allowed types grouped by type, never equity', () => {
    expect(groupedLabels({ allowedTypes: ['ASSET', 'LIABILITY'] })).toEqual([
      ['Asset', 'Account daily', 'Account jamie', 'Account usd'],
      ['Liability', 'Account card'],
    ])
  })

  it('includes archived accounts on request and marks them', () => {
    const labels = groupedLabels({ includeArchived: true }).flat()

    expect(labels).toContain('Account old (Archived)')
    expect(labels).not.toContain('Account equity')
  })

  it('groups by owner in household mode', () => {
    useModeStore().switchToHousehold('h1')

    expect(groupedLabels({ allowedTypes: ['ASSET'], currency: 'EUR' })).toEqual([
      ['Owner: 1', 'Account daily'],
      ['Owner: 2', 'Account jamie'],
    ])
  })

  it('filters by currency', () => {
    expect(groupedLabels({ currency: 'USD' }).flat()).toEqual(['Asset', 'Account usd'])
  })
})
