import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { defineComponent, reactive } from 'vue'
import { i18n } from '@/i18n'
import type { Account } from '@/types/ledger'

const accountStore = reactive({
  accounts: [] as Account[],
  load: vi.fn(async () => {}),
})
const householdStore = reactive({
  currentHousehold: null,
  isOwnerOrAdmin: false,
  sharedAccounts: [] as Array<{ accountId: string; accountName: string }>,
  loadHousehold: vi.fn(async () => {}),
  loadSharedAccounts: vi.fn(async () => {}),
  unshareAccount: vi.fn(async (_householdId: string, accountId: string) => {
    householdStore.sharedAccounts = householdStore.sharedAccounts.filter(
      (share) => share.accountId !== accountId,
    )
    return true
  }),
  shareAccount: vi.fn(),
  error: null,
})

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { householdId: 'household-1' } }),
}))
vi.mock('@/stores/account', () => ({ useAccountStore: () => accountStore }))
vi.mock('@/stores/household', () => ({ useHouseholdStore: () => householdStore }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ user: { id: 1 } }) }))

const { default: HouseholdSettingsPage } = await import('@/views/HouseholdSettingsPage.vue')

const SlotStub = defineComponent({
  template: '<div><slot /><slot name="actions" /></div>',
})
const ButtonStub = defineComponent({
  props: ['disabled'],
  template: '<button :disabled="disabled"><slot /></button>',
})

function account(overrides: Partial<Account>): Account {
  return {
    id: 'active',
    ownerUserId: 1,
    name: 'Current Bank',
    type: 'ASSET',
    currency: 'EUR',
    status: 'ACTIVE',
    archivedAt: null,
    createdAt: '2026-07-12T00:00:00Z',
    ...overrides,
  }
}

describe('HouseholdSettingsPage archived sharing', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    accountStore.accounts = [
      account({}),
      account({
        id: 'archived-shared',
        name: 'Old Bank',
        status: 'ARCHIVED',
        archivedAt: '2026-07-12T08:00:00Z',
      }),
      account({
        id: 'archived-private',
        name: 'Private Archive',
        status: 'ARCHIVED',
        archivedAt: '2026-07-12T08:00:00Z',
      }),
      account({
        id: 'other-owner',
        ownerUserId: 2,
        name: 'Other Bank',
        status: 'ARCHIVED',
        archivedAt: '2026-07-12T08:00:00Z',
      }),
      account({ id: 'equity', name: 'Opening Equity', type: 'EQUITY' }),
    ]
    householdStore.sharedAccounts = [
      { accountId: 'archived-shared', accountName: 'Old Bank' },
      { accountId: 'other-owner', accountName: 'Other Bank' },
    ]
  })

  it('lets the owner unshare an archived account without offering archived accounts for sharing', async () => {
    const wrapper = shallowMount(HouseholdSettingsPage, {
      global: {
        plugins: [i18n],
        stubs: {
          AppListItem: SlotStub,
          Badge: SlotStub,
          Button: ButtonStub,
          Card: SlotStub,
          CardContent: SlotStub,
          CardHeader: SlotStub,
        },
      },
    })
    await flushPromises()

    const unshareButtons = wrapper.findAll('button').filter((button) => button.text() === 'Unshare')
    const shareButtons = wrapper.findAll('button').filter((button) => button.text() === 'Share')
    expect(unshareButtons).toHaveLength(1)
    expect(shareButtons).toHaveLength(1)
    expect(wrapper.text()).toContain('Archived')
    expect(wrapper.text()).not.toContain('Private Archive')
    expect(wrapper.text()).not.toContain('Opening Equity')

    await unshareButtons[0]!.trigger('click')
    await flushPromises()

    expect(householdStore.unshareAccount).toHaveBeenCalledWith('household-1', 'archived-shared')
    expect(wrapper.text()).not.toContain('Old Bank')
    expect(wrapper.findAll('button').filter((button) => button.text() === 'Unshare')).toHaveLength(
      0,
    )
    expect(householdStore.shareAccount).not.toHaveBeenCalled()
  })
})
