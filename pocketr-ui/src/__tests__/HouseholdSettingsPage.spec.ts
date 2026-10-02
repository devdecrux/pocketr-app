import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { HTTPError } from 'ky'
import { h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import UDashboardGroup from '@nuxt/ui/components/DashboardGroup.vue'
import UInputNumber from '@nuxt/ui/components/InputNumber.vue'
import { i18n } from '@/i18n'
import { useAuthStore } from '@/stores/auth'
import type {
  Household,
  HouseholdAccountShare,
  HouseholdRole,
  HouseholdSummary,
} from '@/types/household'
import type { Account } from '@/types/ledger'
import HouseholdSettingsPage from '@/views/HouseholdSettingsPage.vue'

const mocks = vi.hoisted(() => ({
  desktop: { value: true },
  listHouseholds: vi.fn(),
  getHousehold: vi.fn(),
  listSharedAccounts: vi.fn(),
  inviteMember: vi.fn(),
  shareAccount: vi.fn(),
  unshareAccount: vi.fn(),
  updateHouseholdRolloverDay: vi.fn(),
  listAccounts: vi.fn(),
}))

vi.mock('@vueuse/core', async () => {
  const actual = await vi.importActual<typeof import('@vueuse/core')>('@vueuse/core')
  const { ref } = await vi.importActual<typeof import('vue')>('vue')
  return {
    ...actual,
    useColorMode: () => ref('light'),
    useMediaQuery: () => ref(mocks.desktop.value),
  }
})

vi.mock('@/api/csrf', () => ({ primeCsrfToken: vi.fn() }))
vi.mock('@/api/http', () => ({ api: { post: vi.fn(), get: vi.fn() } }))
vi.mock('@/api/households', () => ({
  listHouseholds: mocks.listHouseholds,
  getHousehold: mocks.getHousehold,
  listSharedAccounts: mocks.listSharedAccounts,
  inviteMember: mocks.inviteMember,
  shareAccount: mocks.shareAccount,
  unshareAccount: mocks.unshareAccount,
  updateHouseholdRolloverDay: mocks.updateHouseholdRolloverDay,
}))
vi.mock('@/api/accounts', () => ({ listAccounts: mocks.listAccounts }))

const HOUSEHOLD_ID = 'household-1'

function household(overrides: Partial<Household> = {}): Household {
  return {
    id: HOUSEHOLD_ID,
    name: 'Morgan household',
    rolloverDay: 1,
    createdAt: '2026-07-01T00:00:00Z',
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
    ...overrides,
  }
}

function summary(role: HouseholdRole): HouseholdSummary {
  return {
    id: HOUSEHOLD_ID,
    name: 'Morgan household',
    role,
    status: 'ACTIVE',
    rolloverDay: 1,
    createdAt: '2026-07-01T00:00:00Z',
  }
}

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

function share(accountId: string, accountName: string): HouseholdAccountShare {
  return {
    accountId,
    accountName,
    ownerEmail: 'alex@example.com',
    ownerFirstName: 'Alex',
    ownerLastName: 'Morgan',
    sharedAt: '2026-07-20T00:00:00Z',
  }
}

function httpError(message: string, status = 400): HTTPError {
  const request = new Request('http://localhost/api')
  const response = new Response(JSON.stringify({ message }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
  return new HTTPError(response, request, {} as never)
}

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/household/:householdId/settings',
        name: 'household-settings',
        component: HouseholdSettingsPage,
      },
      { path: '/dashboard', name: 'dashboard', component: { render: () => null } },
    ],
  })
  await router.push(`/household/${HOUSEHOLD_ID}/settings`)
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

function buttonByText(wrapper: VueWrapper, text: string) {
  return wrapper.findAll('button').filter((button) => button.text() === text)
}

async function typeInto(wrapper: VueWrapper, selector: string, value: string) {
  const input = wrapper.get<HTMLInputElement>(selector)
  await input.setValue(value)
  await flushPromises()
}

describe('HouseholdSettingsPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    Element.prototype.scrollIntoView = vi.fn()
    mocks.desktop.value = true
    for (const mock of Object.values(mocks)) {
      if (typeof mock === 'function') mock.mockReset()
    }
    mocks.listHouseholds.mockResolvedValue([summary('OWNER')])
    mocks.getHousehold.mockResolvedValue(household())
    mocks.listSharedAccounts.mockResolvedValue([share('archived-shared', 'Old Bank')])
    mocks.listAccounts.mockResolvedValue([
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
      account({ id: 'other-owner', ownerUserId: 2, name: 'Other Bank' }),
      account({ id: 'equity', name: 'Opening Equity', type: 'EQUITY' }),
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
  })

  it('shows the household, its members and shared accounts to the owner', async () => {
    const wrapper = await mountPage()

    expect(mocks.getHousehold).toHaveBeenCalledWith(HOUSEHOLD_ID)
    expect(mocks.listSharedAccounts).toHaveBeenCalledWith(HOUSEHOLD_ID)
    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.get('main h2').text()).toBe('Morgan household')
    expect(wrapper.text()).toContain('Created')

    const members = wrapper.findAll('main ul')[0]!.findAll('li')
    expect(members).toHaveLength(2)
    expect(members[0]!.text()).toContain('Alex Morgan')
    expect(members[0]!.text()).toContain('alex@example.com')
    expect(members[0]!.text()).toContain('Owner')
    expect(members[0]!.text()).toContain('Active')
    expect(members[0]!.text()).toContain('Joined')
    expect(members[1]!.text()).toContain('sam@example.com')
    expect(members[1]!.text()).toContain('Invited')

    expect(wrapper.text()).toContain('Invite a member')
    expect(wrapper.text()).toContain('Shared by Alex Morgan')
    expect(buttonByText(wrapper, 'Save')).toHaveLength(1)
    expect(wrapper.get('[role="spinbutton"]').attributes('disabled')).toBeUndefined()

    wrapper.unmount()
  })

  it('lays out rollover beside invite, members full width, then the two account cards', async () => {
    const wrapper = await mountPage()
    const cards = wrapper
      .findAll('main [data-slot="root"]')
      .filter((card) => card.find('h3, label').exists())
    const placement = (text: string) =>
      cards
        .find((card) => card.text().includes(text))!
        .classes()
        .filter((name) => name.startsWith('lg:'))

    expect(placement('Rollover period')).toEqual(['lg:col-start-1', 'lg:row-start-1'])
    expect(placement('Invite a member')).toEqual(['lg:col-start-2', 'lg:row-start-1'])
    expect(placement('Members')).toEqual(['lg:col-span-2', 'lg:row-start-2'])
    expect(placement('Shared accounts')).toEqual(['lg:col-start-1', 'lg:row-start-3'])
    expect(placement('Your accounts')).toEqual(['lg:col-start-2', 'lg:row-start-3'])

    wrapper.unmount()
  })

  it('scrolls long lists inside keyboard-reachable regions', async () => {
    const wrapper = await mountPage()

    const regions = wrapper.findAll('[role="region"]')
    expect(regions.map((region) => region.attributes('aria-label'))).toEqual([
      'Members',
      'Shared accounts',
    ])
    for (const region of regions) {
      expect(region.attributes('tabindex')).toBe('0')
      expect(region.classes()).toContain('overflow-y-auto')
    }
    expect(regions[0]!.classes()).toContain('max-h-96')
    expect(regions[1]!.classes()).toContain('max-h-72')
    expect(wrapper.get('h4').element.parentElement!.parentElement!.className).toContain('max-h-72')

    wrapper.unmount()
  })

  it('hides owner-only controls from plain members but keeps account sharing', async () => {
    mocks.listHouseholds.mockResolvedValue([summary('MEMBER')])
    const wrapper = await mountPage()

    expect(wrapper.text()).not.toContain('Invite a member')
    expect(wrapper.find('input[type="email"]').exists()).toBe(false)
    expect(buttonByText(wrapper, 'Save')).toHaveLength(0)
    expect(wrapper.get('[role="spinbutton"]').attributes('disabled')).toBeDefined()
    expect(buttonByText(wrapper, 'Share')).toHaveLength(1)
    expect(buttonByText(wrapper, 'Unshare')).toHaveLength(1)

    wrapper.unmount()
  })

  it('lets the owner unshare an archived account without offering archived accounts for sharing', async () => {
    mocks.unshareAccount.mockResolvedValue(undefined)
    const wrapper = await mountPage()

    const unshareButtons = buttonByText(wrapper, 'Unshare')
    expect(unshareButtons).toHaveLength(1)
    expect(buttonByText(wrapper, 'Share')).toHaveLength(1)
    expect(wrapper.text()).toContain('Archived')
    expect(wrapper.text()).not.toContain('Private Archive')
    expect(wrapper.text()).not.toContain('Opening Equity')
    expect(wrapper.text()).not.toContain('Other Bank')

    mocks.listSharedAccounts.mockResolvedValue([])
    await unshareButtons[0]!.trigger('click')
    await flushPromises()

    expect(mocks.unshareAccount).toHaveBeenCalledWith(HOUSEHOLD_ID, 'archived-shared')
    expect(wrapper.text()).not.toContain('Old Bank')
    expect(buttonByText(wrapper, 'Unshare')).toHaveLength(0)
    expect(mocks.shareAccount).not.toHaveBeenCalled()
    // The unshared row is gone, so focus lands on its card heading.
    expect(document.activeElement?.textContent?.trim()).toBe('Your accounts')

    wrapper.unmount()
  })

  it('shares an account and keeps focus on its button', async () => {
    mocks.shareAccount.mockResolvedValue(share('active', 'Current Bank'))
    const wrapper = await mountPage()

    mocks.listSharedAccounts.mockResolvedValue([
      share('archived-shared', 'Old Bank'),
      share('active', 'Current Bank'),
    ])
    await wrapper.get('[aria-label="Share Current Bank"]').trigger('click')
    await flushPromises()

    expect(mocks.shareAccount).toHaveBeenCalledWith(HOUSEHOLD_ID, { accountId: 'active' })
    const unshare = wrapper.get('[aria-label="Unshare Current Bank"]')
    expect(document.activeElement).toBe(unshare.element)
    expect(Element.prototype.scrollIntoView).toHaveBeenCalledWith({ block: 'nearest' })

    wrapper.unmount()
  })

  it('reports a failed share', async () => {
    mocks.shareAccount.mockRejectedValue(httpError('Account cannot be shared'))
    const wrapper = await mountPage()

    await wrapper.get('[aria-label="Share Current Bank"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Account cannot be shared')

    wrapper.unmount()
  })

  it('invites a member by email and reports success', async () => {
    mocks.inviteMember.mockResolvedValue({})
    const wrapper = await mountPage()

    await typeInto(wrapper, 'input[type="email"]', '  jamie@example.com ')
    await wrapper.get('form:has(input[type="email"])').trigger('submit')
    await flushPromises()

    expect(mocks.inviteMember).toHaveBeenCalledWith(HOUSEHOLD_ID, { email: 'jamie@example.com' })
    expect(mocks.getHousehold).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[role="status"]').text()).toContain('Invitation sent to jamie@example.com.')
    const input = wrapper.get<HTMLInputElement>('input[type="email"]')
    expect(input.element.value).toBe('')
    expect(document.activeElement).toBe(input.element)

    wrapper.unmount()
  })

  it('shows the server message when an invitation fails', async () => {
    mocks.inviteMember.mockRejectedValue(httpError('User not found with email: x@example.com'))
    const wrapper = await mountPage()

    await typeInto(wrapper, 'input[type="email"]', 'x@example.com')
    await wrapper.get('form:has(input[type="email"])').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('User not found with email')
    expect(wrapper.get<HTMLInputElement>('input[type="email"]').element.value).toBe('x@example.com')

    wrapper.unmount()
  })

  it('requires an email before inviting', async () => {
    const wrapper = await mountPage()

    await wrapper.get('form:has(input[type="email"])').trigger('submit')
    await flushPromises()

    expect(mocks.inviteMember).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Email is required.')
    expect(wrapper.get('input[type="email"]').attributes('aria-invalid')).toBe('true')

    wrapper.unmount()
  })

  it('saves the household rollover day', async () => {
    mocks.updateHouseholdRolloverDay.mockResolvedValue(household({ rolloverDay: 15 }))
    const wrapper = await mountPage()

    wrapper.findComponent(UInputNumber).vm.$emit('update:modelValue', 15)
    await flushPromises()
    await buttonByText(wrapper, 'Save')[0]!.trigger('click')
    await flushPromises()

    expect(mocks.updateHouseholdRolloverDay).toHaveBeenCalledWith(HOUSEHOLD_ID, {
      rolloverDay: 15,
    })
    expect(wrapper.get('[role="status"]').text()).toContain(
      'Household expense rollover day updated.',
    )

    wrapper.unmount()
  })

  it('rejects an empty rollover day and reports failed saves', async () => {
    mocks.updateHouseholdRolloverDay.mockRejectedValue(httpError('Rollover rejected'))
    const wrapper = await mountPage()
    const rollover = wrapper.findComponent(UInputNumber)

    rollover.vm.$emit('update:modelValue', null)
    await flushPromises()
    await buttonByText(wrapper, 'Save')[0]!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Rollover day must be between 1 and 31.')
    expect(mocks.updateHouseholdRolloverDay).not.toHaveBeenCalled()

    rollover.vm.$emit('update:modelValue', 20)
    await flushPromises()
    await buttonByText(wrapper, 'Save')[0]!.trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('Rollover rejected')

    wrapper.unmount()
  })

  it('shows the load error instead of the household sections', async () => {
    mocks.getHousehold.mockRejectedValue(httpError('Not a member of this household', 403))
    const wrapper = await mountPage()

    expect(wrapper.get('[role="alert"]').text()).toContain('Not a member of this household')
    expect(wrapper.text()).not.toContain('Invite a member')
    expect(wrapper.text()).not.toContain('Your accounts')

    wrapper.unmount()
  })

  it('shows centred empty states', async () => {
    mocks.listSharedAccounts.mockResolvedValue([])
    mocks.listAccounts.mockResolvedValue([])
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('No accounts are shared yet.')
    expect(wrapper.text()).toContain('You have no accounts to share. Create accounts first.')

    wrapper.unmount()
  })

  it('splits members and accounts into tabs on mobile', async () => {
    mocks.desktop.value = false
    const wrapper = await mountPage()

    expect(wrapper.get('main h1').text()).toBe('Morgan household')
    const panels = wrapper.findAll('[role="tabpanel"]')
    expect(panels).toHaveLength(2)
    expect(panels[0]!.isVisible()).toBe(true)
    expect(panels[1]!.isVisible()).toBe(false)

    const accountsTab = wrapper.findAll('[role="tab"]').find((tab) => tab.text() === 'Accounts')
    await accountsTab!.trigger('mousedown', { button: 0 })
    await flushPromises()

    expect(panels[0]!.isVisible()).toBe(false)
    expect(panels[1]!.isVisible()).toBe(true)

    wrapper.unmount()
  })
})
