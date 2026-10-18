import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, type VueWrapper } from '@vue/test-utils'
import UInputNumber from '@nuxt/ui/components/InputNumber.vue'
import { listAccounts } from '@/api/accounts'
import {
  getHousehold,
  inviteMember,
  listHouseholds,
  listSharedAccounts,
  shareAccount,
  unshareAccount,
  updateHouseholdRolloverDay,
} from '@/api/households'
import HouseholdSettingsPage from '@/views/household/HouseholdSettingsPage.vue'
import { alertText, typeInto } from '@/__tests__/support/dom'
import {
  buildAccount,
  buildHousehold,
  buildHouseholdSummary,
  buildShare,
  httpError,
  signIn,
} from '@/__tests__/support/fixtures'
import { mountRoute } from '@/__tests__/support/mount'

const HOUSEHOLD_ID = 'household-1'
const ARCHIVED = { status: 'ARCHIVED', archivedAt: '2026-07-12T08:00:00Z' } as const

const HOUSEHOLD = buildHousehold({
  id: HOUSEHOLD_ID,
  members: [
    {
      userId: 1,
      email: 'alex@example.com',
      firstName: 'Alex',
      lastName: 'Morgan',
      role: 'OWNER',
      status: 'ACTIVE',
      joinedAt: '2026-07-01T00:00:00Z',
    },
    {
      userId: 2,
      email: 'sam@example.com',
      firstName: null,
      lastName: null,
      role: 'MEMBER',
      status: 'INVITED',
      joinedAt: null,
    },
  ],
})

const OLD_BANK_SHARE = buildShare('archived-shared', { accountName: 'Old Bank' })

async function mountPage() {
  const { wrapper } = await mountRoute(HouseholdSettingsPage, {
    path: `/household/${HOUSEHOLD_ID}/settings`,
    pattern: '/household/:householdId/settings',
  })
  return wrapper
}

const buttonsByText = (wrapper: VueWrapper, text: string) =>
  wrapper.findAll('button').filter((button) => button.text() === text)

const inviteForm = (wrapper: VueWrapper) => wrapper.get('form:has(input[type="email"])')
const emailInput = (wrapper: VueWrapper) => wrapper.get<HTMLInputElement>('input[type="email"]')

describe('HouseholdSettingsPage', () => {
  beforeEach(() => {
    signIn()
    vi.mocked(listHouseholds).mockResolvedValue([
      buildHouseholdSummary({ id: HOUSEHOLD_ID, role: 'OWNER' }),
    ])
    vi.mocked(getHousehold).mockResolvedValue(HOUSEHOLD)
    vi.mocked(listSharedAccounts).mockResolvedValue([OLD_BANK_SHARE])
    vi.mocked(listAccounts).mockResolvedValue([
      buildAccount({ id: 'active', name: 'Current Bank' }),
      buildAccount({ id: 'archived-shared', name: 'Old Bank', ...ARCHIVED }),
      buildAccount({ id: 'archived-private', name: 'Private Archive', ...ARCHIVED }),
      buildAccount({ id: 'other-owner', name: 'Other Bank', ownerUserId: 2 }),
      buildAccount({ id: 'equity', name: 'Opening Equity', type: 'EQUITY' }),
    ])
  })

  it('loads the household and its shared accounts and gives the owner the owner controls', async () => {
    const wrapper = await mountPage()

    expect(getHousehold).toHaveBeenCalledWith(HOUSEHOLD_ID)
    expect(listSharedAccounts).toHaveBeenCalledWith(HOUSEHOLD_ID)
    expect(buttonsByText(wrapper, 'Save')).toHaveLength(1)
  })

  it('hides owner-only controls from plain members but keeps account sharing', async () => {
    vi.mocked(listHouseholds).mockResolvedValue([
      buildHouseholdSummary({ id: HOUSEHOLD_ID, role: 'MEMBER' }),
    ])

    const wrapper = await mountPage()

    expect(wrapper.text()).not.toContain('Invite a member')
    expect(buttonsByText(wrapper, 'Save')).toHaveLength(0)
    expect(wrapper.get('[role="spinbutton"]').attributes('disabled')).toBeDefined()
    expect(buttonsByText(wrapper, 'Share')).toHaveLength(1)
    expect(buttonsByText(wrapper, 'Unshare')).toHaveLength(1)
  })

  describe('sharing accounts', () => {
    it('lets the owner unshare an archived account without offering archived or foreign accounts for sharing', async () => {
      const wrapper = await mountPage()

      expect(buttonsByText(wrapper, 'Share')).toHaveLength(1)
      for (const hidden of ['Private Archive', 'Opening Equity', 'Other Bank']) {
        expect(wrapper.text()).not.toContain(hidden)
      }

      vi.mocked(listSharedAccounts).mockResolvedValue([])
      await buttonsByText(wrapper, 'Unshare')[0]!.trigger('click')
      await flushPromises()

      expect(unshareAccount).toHaveBeenCalledExactlyOnceWith(HOUSEHOLD_ID, 'archived-shared')
      expect(wrapper.text()).not.toContain('Old Bank')
      expect(shareAccount).not.toHaveBeenCalled()
    })

    it('shares an account', async () => {
      const wrapper = await mountPage()

      await wrapper.get('[aria-label="Share Current Bank"]').trigger('click')
      await flushPromises()

      expect(shareAccount).toHaveBeenCalledExactlyOnceWith(HOUSEHOLD_ID, { accountId: 'active' })
    })
  })

  describe('inviting members', () => {
    it('invites a member by email', async () => {
      const wrapper = await mountPage()

      await typeInto(emailInput(wrapper).element, '  jamie@example.com ')
      await inviteForm(wrapper).trigger('submit')
      await flushPromises()

      expect(inviteMember).toHaveBeenCalledExactlyOnceWith(HOUSEHOLD_ID, {
        email: 'jamie@example.com',
      })
      expect(getHousehold).toHaveBeenCalledTimes(2)
      expect(emailInput(wrapper).element.value).toBe('')
    })

    it('shows the server message when an invitation fails and keeps the email', async () => {
      vi.mocked(inviteMember).mockRejectedValue(
        httpError('User not found with email: x@example.com'),
      )
      const wrapper = await mountPage()

      await typeInto(emailInput(wrapper).element, 'x@example.com')
      await inviteForm(wrapper).trigger('submit')
      await flushPromises()

      expect(alertText(wrapper.element)).toContain('User not found with email')
      expect(emailInput(wrapper).element.value).toBe('x@example.com')
    })
  })

  describe('rollover day', () => {
    it('saves the household rollover day', async () => {
      vi.mocked(updateHouseholdRolloverDay).mockResolvedValue(buildHousehold({ rolloverDay: 15 }))
      const wrapper = await mountPage()

      wrapper.findComponent(UInputNumber).vm.$emit('update:modelValue', 15)
      await flushPromises()
      await buttonsByText(wrapper, 'Save')[0]!.trigger('click')
      await flushPromises()

      expect(updateHouseholdRolloverDay).toHaveBeenCalledExactlyOnceWith(HOUSEHOLD_ID, {
        rolloverDay: 15,
      })
    })

    it('shows the load error instead of the household sections and reports a failed rollover day save', async () => {
      vi.mocked(getHousehold).mockRejectedValueOnce(
        httpError('Not a member of this household', 403),
      )
      const failed = await mountPage()
      expect(alertText(failed.element)).toContain('Not a member of this household')
      expect(failed.text()).not.toContain('Invite a member')
      expect(failed.text()).not.toContain('Your accounts')
      failed.unmount()

      vi.mocked(updateHouseholdRolloverDay).mockRejectedValue(httpError('Rollover rejected'))
      const wrapper = await mountPage()

      wrapper.findComponent(UInputNumber).vm.$emit('update:modelValue', 20)
      await flushPromises()
      await buttonsByText(wrapper, 'Save')[0]!.trigger('click')
      await flushPromises()
      expect(alertText(wrapper.element)).toContain('Rollover rejected')
    })
  })
})
