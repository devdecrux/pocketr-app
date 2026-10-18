import { describe, expect, it, vi } from 'vitest'
import { flushPromises, type VueWrapper } from '@vue/test-utils'
import { api } from '@/api/http'
import AppSidebar from '@/layout/AppSidebar.vue'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { useModeStore } from '@/stores/mode'
import type { HouseholdSummary } from '@/types/household'
import { buildHouseholdSummary, signIn } from '@/__tests__/support/fixtures'
import { buttonIn, click } from '@/__tests__/support/dom'
import { mountRoute } from '@/__tests__/support/mount'

const Page = { render: () => null }

const PERSONAL_HREFS = ['/dashboard', '/transactions', '/accounts', '/categories']

async function mountSidebar() {
  return mountRoute(AppSidebar, {
    path: '/dashboard',
    routes: [
      '/transactions',
      '/accounts',
      '/categories',
      '/settings',
      '/household/:householdId/settings',
      '/login',
    ].map((route) => ({ path: route, component: Page })),
  })
}

function navHrefs(wrapper: VueWrapper) {
  return wrapper.findAll('nav a').map((link) => link.attributes('href'))
}

function joinHouseholds(memberships: HouseholdSummary[], activeId?: string) {
  useHouseholdStore().households = memberships
  if (activeId) useModeStore().switchToHousehold(activeId)
}

describe('AppSidebar', () => {
  describe('household entry', () => {
    it.each([
      ['OWNER', '/household/hh-1/settings'],
      ['ADMIN', '/household/hh-1/settings'],
      ['MEMBER', '/settings'],
    ] as const)('sends a %s in household mode to %s', async (role, target) => {
      signIn()
      joinHouseholds([buildHouseholdSummary({ role })], 'hh-1')

      const { wrapper } = await mountSidebar()

      expect(navHrefs(wrapper)).toEqual([...PERSONAL_HREFS, target])
    })

    it('offers no household entry in personal mode, even to an owner', async () => {
      signIn()
      joinHouseholds([buildHouseholdSummary({ role: 'OWNER' })])

      const { wrapper } = await mountSidebar()

      expect(navHrefs(wrapper)).toEqual(PERSONAL_HREFS)
    })

    it('offers no household entry before any membership has loaded', async () => {
      signIn()
      useModeStore().switchToHousehold('hh-1')

      const { wrapper } = await mountSidebar()

      expect(navHrefs(wrapper)).toEqual(PERSONAL_HREFS)
    })

    it('does not carry an admin role over from another household to the active one', async () => {
      signIn()
      joinHouseholds(
        [
          buildHouseholdSummary({ id: 'hh-1', role: 'OWNER' }),
          buildHouseholdSummary({ id: 'hh-2', role: 'MEMBER' }),
        ],
        'hh-2',
      )

      const { wrapper } = await mountSidebar()

      expect(navHrefs(wrapper)).toEqual([...PERSONAL_HREFS, '/settings'])
    })

    it('does not manage a household the user is not a member of', async () => {
      signIn()
      joinHouseholds([buildHouseholdSummary({ id: 'hh-1', role: 'OWNER' })], 'hh-unknown')

      const { wrapper } = await mountSidebar()

      expect(navHrefs(wrapper)).toEqual([...PERSONAL_HREFS, '/settings'])
    })
  })

  it('signs out from the profile menu and returns to the login page', async () => {
    signIn()
    vi.mocked(api.post).mockResolvedValue(new Response(null, { status: 204 }) as never)
    const { wrapper, router } = await mountSidebar()

    await wrapper.get('[data-slot="footer"] button').trigger('click')
    await flushPromises()
    await click(buttonIn(document.body, 'Sign out'))

    expect(api.post).toHaveBeenCalledWith('/api/v1/user/logout', expect.anything())
    expect(useAuthStore().user).toBeNull()
    expect(router.currentRoute.value.path).toBe('/login')
  })
})
