import { beforeEach, describe, expect, it, vi } from 'vitest'
import { type VueWrapper } from '@vue/test-utils'
import { archiveAccount, createAccount, listAccounts, updateAccount } from '@/api/accounts'
import { listHouseholds, listSharedAccounts } from '@/api/households'
import { getAccountBalances } from '@/api/ledger'
import { useModeStore } from '@/stores/mode'
import AccountsPage from '@/views/accounts/AccountsPage.vue'
import {
  ACCOUNTS,
  balanceRows,
  buildAccount,
  buildHouseholdSummary,
  buildShare,
  httpError,
  signIn,
} from '@/__tests__/support/fixtures'
import {
  alertText,
  buttonIn,
  chooseFilter,
  click,
  columnHeaders,
  dialogByTitle,
  dialogs,
  openFilters,
  optionLabels,
  rowNames,
  rowWith,
  typeInto,
} from '@/__tests__/support/dom'
import { mountRoute } from '@/__tests__/support/mount'

const ACTIVE_NAMES = [
  'Daily account',
  'Savings',
  'Credit card',
  'Salary',
  'Living expenses',
  'USD cash',
  'Jamie wallet',
]
const ACTIVE_IDS = ['daily', 'savings', 'card', 'salary', 'living', 'usd', 'jamie']

async function mountPage() {
  const { wrapper } = await mountRoute(AccountsPage, { path: '/accounts' })
  return wrapper
}

const editButton = (wrapper: VueWrapper, name: string) =>
  wrapper.get(`[aria-label="Edit ${name}"]`).element
const archiveButton = (wrapper: VueWrapper, name: string) =>
  wrapper.get(`[aria-label="Archive account ${name}"]`).element

describe('AccountsPage', () => {
  beforeEach(() => {
    signIn()
    vi.mocked(listAccounts).mockResolvedValue(ACCOUNTS)
    vi.mocked(getAccountBalances).mockImplementation(async (ids) => balanceRows(ids))
    vi.mocked(createAccount).mockResolvedValue(buildAccount())
    vi.mocked(updateAccount).mockResolvedValue(buildAccount())
  })

  describe('list', () => {
    it('lists the active accounts with their balances, loaded in one batch', async () => {
      const wrapper = await mountPage()

      expect(rowNames(wrapper)).toEqual(ACTIVE_NAMES)
      expect(getAccountBalances).toHaveBeenCalledExactlyOnceWith(ACTIVE_IDS, undefined, undefined)
      expect(rowWith(wrapper, 'Daily account').text()).toContain('€1,845.00')
      expect(rowWith(wrapper, 'USD cash').text()).toContain('$50.00')
    })

    it('shows an error when the accounts fail to load', async () => {
      vi.mocked(listAccounts).mockRejectedValue(new Error('boom'))

      const wrapper = await mountPage()

      expect(wrapper.text()).toContain('Failed to load accounts.')
      expect(wrapper.text()).not.toContain('Daily account')
    })
  })

  describe('summary cards', () => {
    const card = (wrapper: VueWrapper, title: string) =>
      wrapper
        .findAll('main [data-slot="root"]')
        .find((candidate) => candidate.text().includes(title))!

    it('totals asset balances as money available and liability balances as debt', async () => {
      const wrapper = await mountPage()

      // Own and housemate EUR assets (1,845 + 1,000 + 200) with the USD account as a secondary amount.
      expect(card(wrapper, 'Money available').text()).toContain('€3,045.00')
      expect(card(wrapper, 'Money available').text()).toContain('$50.00')
      expect(card(wrapper, 'Debt outstanding').text()).toContain('€320.00')
    })

    it('follows the currency filter without extra requests', async () => {
      const wrapper = await mountPage()

      await chooseFilter(wrapper, 'Filter currency', 'USD')

      expect(card(wrapper, 'Money available').text()).toContain('$50.00')
      expect(card(wrapper, 'Money available').text()).toContain('USD accounts')
      expect(getAccountBalances).toHaveBeenCalledOnce()
      expect(listAccounts).toHaveBeenCalledOnce()
    })
  })

  describe('filters', () => {
    it('filters by type', async () => {
      const wrapper = await mountPage()

      await chooseFilter(wrapper, 'Filter type', 'Liability')
      expect(rowNames(wrapper)).toEqual(['Credit card'])

      await chooseFilter(wrapper, 'Filter type', 'All types')
      expect(rowNames(wrapper)).toEqual(ACTIVE_NAMES)
    })

    it('offers the currencies of the active accounts, and filters by currency', async () => {
      const wrapper = await mountPage()
      await openFilters(wrapper)

      expect(optionLabels(wrapper, 'Filter currency')).toEqual(['All currencies', 'EUR', 'USD'])
      await chooseFilter(wrapper, 'Filter currency', 'USD')

      expect(rowNames(wrapper)).toEqual(['USD cash'])
    })
  })

  it('offers rename and archive only for accounts the current user owns', async () => {
    const wrapper = await mountPage()

    expect(wrapper.find('[aria-label="Edit Daily account"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="Archive account Daily account"]').exists()).toBe(true)
    const shared = rowWith(wrapper, 'Jamie wallet')
    expect(shared.find('button').exists()).toBe(false)
  })

  describe('rename', () => {
    async function openRename(wrapper: VueWrapper) {
      await click(editButton(wrapper, 'Daily account'))
      return dialogByTitle('Rename account')
    }

    it('renames through an overlay prefilled with the name and reloads the accounts', async () => {
      const wrapper = await mountPage()
      const dialog = await openRename(wrapper)

      const input = dialog.querySelector<HTMLInputElement>('input')!
      expect(input.value).toBe('Daily account')
      await typeInto(input, '  Everyday  ')
      await click(buttonIn(dialog, 'Save'))

      expect(updateAccount).toHaveBeenCalledWith('daily', { name: 'Everyday' })
      expect(listAccounts).toHaveBeenCalledTimes(2)
      expect(dialogs()).toHaveLength(0)
    })
  })

  describe('archive', () => {
    async function requestArchive(wrapper: VueWrapper) {
      await click(archiveButton(wrapper, 'Daily account'))
      return dialogByTitle('Archive account')
    }

    it('archives only after confirmation and reloads the accounts and balances', async () => {
      const wrapper = await mountPage()
      const dialog = await requestArchive(wrapper)
      expect(archiveAccount).not.toHaveBeenCalled()

      await click(buttonIn(dialog, 'Archive account'))

      expect(archiveAccount).toHaveBeenCalledExactlyOnceWith('daily')
      expect(listAccounts).toHaveBeenCalledTimes(2)
      expect(getAccountBalances).toHaveBeenCalledTimes(2)
      expect(dialogs()).toHaveLength(0)
    })

    it('does not archive when the confirmation is cancelled', async () => {
      const wrapper = await mountPage()
      const dialog = await requestArchive(wrapper)

      await click(buttonIn(dialog, 'Cancel'))

      expect(archiveAccount).not.toHaveBeenCalled()
      expect(dialogs()).toHaveLength(0)
    })

    it('shows the server message on the page when archiving fails', async () => {
      vi.mocked(archiveAccount).mockRejectedValue(httpError('Account still has a balance'))
      const wrapper = await mountPage()
      const dialog = await requestArchive(wrapper)

      await click(buttonIn(dialog, 'Archive account'))

      expect(alertText(wrapper.element)).toContain('Account still has a balance')
      expect(dialogs()).toHaveLength(0)
      expect(listAccounts).toHaveBeenCalledOnce()
      expect(rowNames(wrapper)).toContain('Daily account')
    })
  })

  describe('create', () => {
    async function openCreate(wrapper: VueWrapper) {
      await click(buttonIn(wrapper.element as HTMLElement, 'New account'))
      return dialogByTitle('New account')
    }

    it('creates an account with the opening balance and reloads the page data', async () => {
      const wrapper = await mountPage()
      const dialog = await openCreate(wrapper)

      expect(buttonIn(dialog, 'Create').disabled).toBe(true)
      await typeInto(
        dialog.querySelector<HTMLInputElement>('input[id$="-name"]')!,
        'Holiday savings',
      )
      await typeInto(
        dialog.querySelector<HTMLInputElement>('input[id$="-initial-balance"]')!,
        '500',
      )
      await click(buttonIn(dialog, 'Create'))

      expect(createAccount).toHaveBeenCalledExactlyOnceWith(
        expect.objectContaining({
          name: 'Holiday savings',
          type: 'ASSET',
          currency: 'EUR',
          openingBalanceMinor: 50000,
        }),
      )
      expect(listAccounts).toHaveBeenCalledTimes(2)
      expect(getAccountBalances).toHaveBeenCalledTimes(2)
      expect(dialogs()).toHaveLength(0)
    })

    it('shows "..." silently for balances that fail to load and keeps the overlay open with an error when creating fails', async () => {
      vi.mocked(getAccountBalances).mockRejectedValue(new Error('boom'))
      vi.mocked(createAccount).mockRejectedValue(new Error('boom'))
      const wrapper = await mountPage()

      expect(rowWith(wrapper, 'Daily account').text()).toContain('...')
      const available = wrapper
        .findAll('main [data-slot="root"]')
        .find((card) => card.text().includes('Money available'))!
      expect(available.text()).toContain('...')
      expect(alertText(wrapper.element)).toBeNull()

      const dialog = await openCreate(wrapper)

      await typeInto(dialog.querySelector<HTMLInputElement>('input[id$="-name"]')!, 'Holiday')
      await click(buttonIn(dialog, 'Create'))

      expect(dialogByTitle('New account').textContent).toContain('Failed to create account.')
      expect(listAccounts).toHaveBeenCalledOnce()
    })
  })

  describe('household mode', () => {
    beforeEach(() => {
      // The household store drops a selected household that is not in the member's list.
      vi.mocked(listHouseholds).mockResolvedValue([buildHouseholdSummary({ id: 'h1' })])
      vi.mocked(listSharedAccounts).mockResolvedValue([buildShare('daily'), buildShare('jamie')])
    })

    it('marks the shared accounts and counts only them in the summary', async () => {
      useModeStore().switchToHousehold('h1')

      const wrapper = await mountPage()

      expect(listSharedAccounts).toHaveBeenCalledWith('h1')
      expect(getAccountBalances).toHaveBeenCalledExactlyOnceWith(ACTIVE_IDS, undefined, 'h1')
      expect(columnHeaders(wrapper)).toContain('Shared')
      expect(rowWith(wrapper, 'Daily account').text()).toContain('Shared')
      expect(rowWith(wrapper, 'Savings').text()).not.toContain('Shared')
      const available = wrapper
        .findAll('main [data-slot="root"]')
        .find((card) => card.text().includes('Money available'))!
      expect(available.text()).toContain('€2,045.00')
    })
  })
})
