import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import UDashboardGroup from '@nuxt/ui/components/DashboardGroup.vue'
import AppModeSelect from '@/components/layout/AppModeSelect.vue'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import { useViewModeSelection } from '@/composables/useViewModeSelection'
import { i18n } from '@/i18n'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { useModeStore } from '@/stores/mode'
import { useAccountStore } from '@/stores/account'
import { useLedgerStore } from '@/stores/ledger'
import type { HouseholdSummary } from '@/types/household'

const { colorMode, listHouseholds, listAccounts, listTxns, apiPost } = vi.hoisted(() => ({
  colorMode: { value: 'light' as 'light' | 'dark' | 'auto' },
  listHouseholds: vi.fn<() => Promise<HouseholdSummary[]>>(),
  listAccounts: vi.fn(async () => []),
  listTxns: vi.fn(async () => ({
    content: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  })),
  apiPost: vi.fn(async () => new Response(null, { status: 204 })),
}))

vi.mock('@vueuse/core', async () => {
  const actual = await vi.importActual<typeof import('@vueuse/core')>('@vueuse/core')
  const { ref } = await vi.importActual<typeof import('vue')>('vue')
  // Like VueUse: `value` resolves "auto" to the system theme (light here); `store` keeps the choice.
  const store = ref(colorMode.value)
  return {
    ...actual,
    useColorMode: () => ({
      store,
      get value() {
        return store.value === 'auto' ? 'light' : store.value
      },
      set value(next: 'light' | 'dark' | 'auto') {
        colorMode.value = next
        store.value = next
      },
    }),
  }
})

vi.mock('@/api/csrf', () => ({ primeCsrfToken: vi.fn() }))
vi.mock('@/api/http', () => ({ api: { post: apiPost, get: vi.fn() } }))
vi.mock('@/api/households', () => ({ listHouseholds }))
vi.mock('@/api/accounts', () => ({ listAccounts }))
vi.mock('@/api/ledger', () => ({ listTxns }))

const Page = { template: '<div data-test="page">page</div>' }

function household(role: HouseholdSummary['role']): HouseholdSummary {
  return {
    id: 'hh-1',
    name: 'Morgan family',
    role,
    status: 'ACTIVE',
    rolloverDay: 1,
    createdAt: '2026-01-01T00:00:00Z',
  }
}

async function mountSidebar(path = '/dashboard') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/dashboard', component: Page },
      { path: '/transactions', component: Page },
      { path: '/accounts', component: Page },
      { path: '/categories', component: Page },
      { path: '/settings', component: Page },
      { path: '/household/:householdId/settings', component: Page },
      { path: '/login', component: Page },
    ],
  })
  await router.push(path)
  await router.isReady()

  const wrapper = mount(
    {
      render: () =>
        h(UApp, null, {
          default: () =>
            h(UDashboardGroup, { unit: 'px', persistent: false }, { default: () => h(AppSidebar) }),
        }),
    },
    { attachTo: document.body, global: { plugins: [router, i18n, ui] } },
  )
  await flushPromises()
  return { router, wrapper }
}

function navLinks(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAll('nav a').map((link) => ({
    text: link.text(),
    href: link.attributes('href'),
    current: link.attributes('aria-current'),
  }))
}

describe('AppSidebar', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    colorMode.value = 'light'
    listHouseholds.mockReset()
    listHouseholds.mockResolvedValue([])
    listAccounts.mockClear()
    listTxns.mockClear()
    apiPost.mockClear()
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        unobserve() {}
        disconnect() {}
      },
    )
    const storage = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => storage.set(key, value),
      removeItem: (key: string) => storage.delete(key),
    })
    useAuthStore().setUser({
      id: 1,
      email: 'alex@example.com',
      firstName: 'Alex',
      lastName: 'Morgan',
      language: 'en',
      rolloverDay: 1,
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.body.innerHTML = ''
  })

  it('renders every navigation target and marks the current route active', async () => {
    const { wrapper } = await mountSidebar('/dashboard')

    expect(navLinks(wrapper)).toEqual([
      { text: 'Dashboard', href: '/dashboard', current: 'page' },
      { text: 'Transactions', href: '/transactions', current: undefined },
      { text: 'Accounts', href: '/accounts', current: undefined },
      { text: 'Categories', href: '/categories', current: undefined },
      { text: 'Household', href: '/settings', current: undefined },
    ])
    expect(wrapper.text()).toContain('Alex Morgan')
    expect(wrapper.text()).toContain('alex@example.com')
    wrapper.unmount()
  })

  it('links Household to the active household settings for its owner or admin', async () => {
    useHouseholdStore().households = [household('ADMIN')]
    useModeStore().switchToHousehold('hh-1')

    const { wrapper } = await mountSidebar('/dashboard')

    expect(navLinks(wrapper)[4]).toMatchObject({ href: '/household/hh-1/settings' })
    wrapper.unmount()
  })

  it('keeps Household on the settings entry point for household members', async () => {
    useHouseholdStore().households = [household('MEMBER')]
    useModeStore().switchToHousehold('hh-1')

    const { wrapper } = await mountSidebar('/dashboard')

    expect(navLinks(wrapper)[4]).toMatchObject({ href: '/settings' })
    wrapper.unmount()
  })

  it('opens the mobile menu with Ctrl+B and closes it with Escape', async () => {
    const { wrapper } = await mountSidebar('/dashboard')
    expect(document.querySelector('[role="dialog"]')).toBeNull()

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'b', ctrlKey: true, bubbles: true }))
    await flushPromises()
    const dialog = document.querySelector('[role="dialog"]')
    expect(dialog).not.toBeNull()
    expect(dialog?.textContent).toContain('Transactions')

    document.activeElement?.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }),
    )
    await flushPromises()
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull())
    wrapper.unmount()
  })

  it('switches the theme through useAppTheme and signs out from the profile menu', async () => {
    const { router, wrapper } = await mountSidebar('/dashboard')

    await wrapper.find('[data-slot="footer"] button').trigger('click')
    await flushPromises()

    const darkOption = [...document.querySelectorAll<HTMLElement>('[role="radio"]')].find((radio) =>
      radio.closest('label')?.textContent?.includes('Dark'),
    )
    expect(darkOption).toBeDefined()
    darkOption?.click()
    await flushPromises()
    expect(colorMode.value).toBe('dark')

    const signOut = [...document.querySelectorAll<HTMLElement>('button')].find((button) =>
      button.textContent?.includes('Sign out'),
    )
    signOut?.click()
    await flushPromises()

    expect(apiPost).toHaveBeenCalledWith('/api/v1/user/logout', expect.anything())
    expect(useAuthStore().user).toBeNull()
    expect(router.currentRoute.value.path).toBe('/login')
    wrapper.unmount()
  })

  it('shows System as checked in Appearance when the stored mode is auto', async () => {
    const { wrapper } = await mountSidebar('/dashboard')

    await wrapper.find('[data-slot="footer"] button').trigger('click')
    await flushPromises()

    const radio = (label: string) =>
      [...document.querySelectorAll<HTMLElement>('[role="radio"]')].find((item) =>
        item.closest('label')?.textContent?.includes(label),
      )
    radio('System')?.click()
    await flushPromises()

    expect(colorMode.value).toBe('auto')
    expect(radio('System')?.getAttribute('aria-checked')).toBe('true')
    expect(radio('Light')?.getAttribute('aria-checked')).toBe('false')
    wrapper.unmount()
  })
})

describe('AppModeSelect', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        unobserve() {}
        disconnect() {}
      },
    )
    const storage = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => storage.set(key, value),
      removeItem: (key: string) => storage.delete(key),
    })
    listHouseholds.mockReset()
    listHouseholds.mockResolvedValue([household('MEMBER')])
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.body.innerHTML = ''
  })

  it('refreshes households once on mount and shows the current mode', async () => {
    const wrapper = mount(
      { render: () => h(UApp, null, { default: () => h(AppModeSelect) }) },
      { attachTo: document.body, global: { plugins: [i18n, ui] } },
    )
    await flushPromises()

    expect(listHouseholds).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('Personal')
    wrapper.unmount()
  })

  it('switches between individual and household mode and reloads accounts and transactions', async () => {
    const accountStore = useAccountStore()
    const ledgerStore = useLedgerStore()
    const accountLoad = vi.spyOn(accountStore, 'load').mockResolvedValue()
    const ledgerLoad = vi.spyOn(ledgerStore, 'load').mockResolvedValue()
    const { currentValue, selectMode } = useViewModeSelection()

    await selectMode('household:hh-1')
    expect(useModeStore().viewMode).toEqual({ kind: 'HOUSEHOLD', householdId: 'hh-1' })
    expect(currentValue.value).toBe('household:hh-1')

    await selectMode('individual')
    expect(useModeStore().viewMode).toEqual({ kind: 'INDIVIDUAL' })
    expect(accountLoad).toHaveBeenCalledTimes(2)
    expect(ledgerLoad).toHaveBeenCalledTimes(2)
  })
})
