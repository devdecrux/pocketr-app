import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { defineComponent, h, type VNodeChild } from 'vue'
import { i18n } from '@/i18n'
import type { Account } from '@/types/ledger'

const archiveAccount = vi.fn()
const getAccountBalances = vi.fn()

const ownedAccount: Account = {
  id: 'owned-account',
  ownerUserId: 1,
  name: 'Checking',
  type: 'ASSET',
  currency: 'EUR',
  createdAt: '2026-07-12T00:00:00Z',
  status: 'ACTIVE',
  archivedAt: null,
}

const sharedAccount: Account = {
  ...ownedAccount,
  id: 'shared-account',
  ownerUserId: 2,
  name: 'Housemate Savings',
}

const accountStore = {
  accounts: [ownedAccount, sharedAccount],
  activeAccounts: [ownedAccount, sharedAccount],
  isLoading: false,
  error: null as string | null,
  load: vi.fn(async () => {}),
  archiveAccount,
}

const currencyStore = {
  currencies: [{ code: 'EUR', minorUnit: 2, name: 'Euro' }],
  load: vi.fn(async () => {}),
  getMinorUnit: vi.fn(() => 2),
}

const modeStore = {
  isHousehold: true,
  householdId: 'household-1',
  viewMode: { kind: 'HOUSEHOLD', householdId: 'household-1' },
}

const householdStore = {
  sharedAccounts: [{ accountId: ownedAccount.id }, { accountId: sharedAccount.id }],
  loadSharedAccounts: vi.fn(async () => {}),
}

vi.mock('@tanstack/vue-table', () => ({
  getCoreRowModel: vi.fn(() => vi.fn()),
  useVueTable: vi.fn((options) => options),
}))

vi.mock('@/api/accounts', () => ({
  createAccount: vi.fn(),
  updateAccount: vi.fn(),
}))

vi.mock('@/api/ledger', () => ({
  getAccountBalances,
}))

vi.mock('@/stores/account', () => ({
  useAccountStore: () => accountStore,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ user: { id: 1 } }),
}))

vi.mock('@/stores/currency', () => ({
  useCurrencyStore: () => currencyStore,
}))

vi.mock('@/stores/household', () => ({
  useHouseholdStore: () => householdStore,
}))

vi.mock('@/stores/mode', () => ({
  useModeStore: () => modeStore,
}))

const { default: AccountsPage } = await import('@/views/AccountsPage.vue')

const SlotStub = defineComponent({
  template: '<div><slot /><slot name="footer" /></div>',
})

const DataTableStub = defineComponent({
  props: ['table'],
  setup(props) {
    return () => {
      const table = props.table as {
        data: Account[]
        columns: Array<{
          id?: string
          cell?: (context: { row: { original: Account } }) => VNodeChild
        }>
      }
      const actions = table.columns.find((column) => column.id === 'actions')
      return h(
        'div',
        table.data.map((account) =>
          actions?.cell ? actions.cell({ row: { original: account } }) : null,
        ),
      )
    }
  },
})

function mountPage() {
  return shallowMount(AccountsPage, {
    global: {
      plugins: [i18n],
      stubs: {
        AppCardHeader: SlotStub,
        AppDialogContent: SlotStub,
        AppStatusText: SlotStub,
        Card: SlotStub,
        CardContent: SlotStub,
        DataTable: DataTableStub,
        Dialog: SlotStub,
        DialogTrigger: SlotStub,
      },
    },
  })
}

describe('AccountsPage account archiving', () => {
  beforeEach(() => {
    archiveAccount.mockReset()
    archiveAccount.mockResolvedValue(undefined)
    getAccountBalances.mockReset()
    getAccountBalances.mockResolvedValue([])
    accountStore.load.mockClear()
    currencyStore.load.mockClear()
    householdStore.loadSharedAccounts.mockClear()
  })

  it('shows the trash action only for an account owned by the current household member', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const archiveActions = wrapper.findAll('[data-table-action="delete"]')
    expect(archiveActions).toHaveLength(1)
    expect(archiveActions[0]?.attributes('aria-label')).toBe('Archive account Checking')
  })

  it('archives only after confirmation and reloads dependent account data', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-table-action="delete"]').trigger('click')
    expect(archiveAccount).not.toHaveBeenCalled()

    await wrapper.find('[data-testid="confirm-archive-account"]').trigger('click')
    await flushPromises()

    expect(archiveAccount).toHaveBeenCalledTimes(1)
    expect(archiveAccount).toHaveBeenCalledWith('owned-account')
    expect(accountStore.load).toHaveBeenCalledTimes(2)
    expect(householdStore.loadSharedAccounts).toHaveBeenCalledTimes(2)
  })

  it('keeps the confirmation open and displays an error when archiving fails', async () => {
    archiveAccount.mockRejectedValue(new Error('request failed'))
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-table-action="delete"]').trigger('click')
    await wrapper.find('[data-testid="confirm-archive-account"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Failed to archive account.')
    expect(accountStore.load).toHaveBeenCalledTimes(1)
  })
})
