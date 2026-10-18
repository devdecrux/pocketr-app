import { describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { h } from 'vue'
import { listAccounts } from '@/api/accounts'
import { listHouseholds } from '@/api/households'
import { listTxns } from '@/api/ledger'
import AppModeSelect from '@/layout/AppModeSelect.vue'
import { useModeStore } from '@/stores/mode'
import { buildHouseholdSummary } from '@/__tests__/support/fixtures'
import { chooseOption } from '@/__tests__/support/dom'
import { mountInApp } from '@/__tests__/support/mount'

const MODE_SELECT = 'Select view mode'

describe('AppModeSelect', () => {
  it('refreshes the households once on mount', async () => {
    vi.mocked(listHouseholds).mockResolvedValue([buildHouseholdSummary({ role: 'MEMBER' })])

    mountInApp(() => h(AppModeSelect))
    await flushPromises()

    expect(listHouseholds).toHaveBeenCalledOnce()
  })

  it('switches between personal and household mode and reloads accounts and transactions', async () => {
    vi.mocked(listHouseholds).mockResolvedValue([buildHouseholdSummary({ role: 'MEMBER' })])
    const wrapper = mountInApp(() => h(AppModeSelect))
    await flushPromises()

    await chooseOption(wrapper, MODE_SELECT, 'Household')

    expect(useModeStore().viewMode).toEqual({ kind: 'HOUSEHOLD', householdId: 'hh-1' })
    expect(listAccounts).toHaveBeenLastCalledWith(
      expect.objectContaining({ mode: 'HOUSEHOLD', householdId: 'hh-1' }),
    )
    expect(listTxns).toHaveBeenLastCalledWith(
      expect.objectContaining({ mode: 'HOUSEHOLD', householdId: 'hh-1' }),
    )

    await chooseOption(wrapper, MODE_SELECT, 'Personal')

    expect(useModeStore().viewMode).toEqual({ kind: 'INDIVIDUAL' })
    expect(listAccounts).toHaveBeenLastCalledWith(expect.objectContaining({ mode: 'INDIVIDUAL' }))
  })
})
