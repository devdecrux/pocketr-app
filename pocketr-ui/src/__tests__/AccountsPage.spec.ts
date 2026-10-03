import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { HTTPError } from 'ky'
import { h, nextTick } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import UIcon from '@nuxt/ui/components/Icon.vue'
import USelect from '@nuxt/ui/components/Select.vue'
import UDashboardGroup from '@nuxt/ui/components/DashboardGroup.vue'
import { i18n } from '@/i18n'
import { useAuthStore } from '@/stores/auth'
import { useModeStore } from '@/stores/mode'
import type { Account, AccountBalance } from '@/types/ledger'
import AccountsPage from '@/views/AccountsPage.vue'

const mocks = vi.hoisted(() => ({
  desktop: { value: true },
  listAccounts: vi.fn(),
  createAccount: vi.fn(),
  updateAccount: vi.fn(),
  archiveAccount: vi.fn(),
  getAccountBalances: vi.fn(),
  listCurrencies: vi.fn(),
  listSharedAccounts: vi.fn(),
  listHouseholds: vi.fn(),
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
vi.mock('@/api/accounts', () => ({
  listAccounts: mocks.listAccounts,
  createAccount: mocks.createAccount,
  updateAccount: mocks.updateAccount,
  archiveAccount: mocks.archiveAccount,
}))
vi.mock('@/api/ledger', () => ({ getAccountBalances: mocks.getAccountBalances }))
vi.mock('@/api/currencies', () => ({ listCurrencies: mocks.listCurrencies }))
vi.mock('@/api/households', () => ({
  listHouseholds: mocks.listHouseholds,
  listSharedAccounts: mocks.listSharedAccounts,
}))

function account(override: Partial<Account>): Account {
  return {
    id: 'acc-1',
    ownerUserId: 1,
    name: 'Checking',
    type: 'ASSET',
    currency: 'EUR',
    createdAt: '2026-01-01T00:00:00Z',
    status: 'ACTIVE',
    archivedAt: null,
    ...override,
  }
}

const ACCOUNTS = [
  account({ id: 'daily', name: 'Daily account' }),
  account({ id: 'savings', name: 'Savings' }),
  account({ id: 'card', name: 'Credit card', type: 'LIABILITY' }),
  account({ id: 'salary', name: 'Salary', type: 'INCOME' }),
  account({ id: 'living', name: 'Living expenses', type: 'EXPENSE' }),
  account({ id: 'old', name: 'Old checking', status: 'ARCHIVED' }),
  account({ id: 'usd', name: 'USD cash', currency: 'USD' }),
  account({ id: 'jamie', name: 'Jamie wallet', ownerUserId: 2 }),
]

const BALANCES: Record<string, number> = {
  daily: 184500,
  savings: 100000,
  card: 32000,
  salary: 350000,
  living: 107500,
  usd: 5000,
  jamie: 20000,
}

function balanceRows(ids: string[]): AccountBalance[] {
  return ids.map((id) => ({
    accountId: id,
    accountName: id,
    accountType: 'ASSET',
    currency: 'EUR',
    balanceMinor: BALANCES[id] ?? 0,
    asOf: '2026-09-20',
  }))
}

function httpError(message: string, status = 409): HTTPError {
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
      { path: '/accounts', name: 'accounts', component: AccountsPage },
      { path: '/dashboard', name: 'dashboard', component: { render: () => null } },
    ],
  })
  await router.push('/accounts')
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

// A closed overlay stays mounted while its exit animation would run, so only open ones count.
function dialogs(): HTMLElement[] {
  return [...document.body.querySelectorAll<HTMLElement>('[role="dialog"]')].filter(
    (dialog) => dialog.getAttribute('data-state') !== 'closed',
  )
}

function dialogByTitle(title: string): HTMLElement {
  const match = dialogs().find((dialog) => dialog.textContent?.includes(title))
  if (!match) throw new Error(`No dialog "${title}"`)
  return match
}

function buttonIn(root: ParentNode, text: string): HTMLButtonElement {
  const button = [...root.querySelectorAll<HTMLButtonElement>('button')].find(
    (candidate) =>
      candidate.textContent?.trim() === text || candidate.getAttribute('aria-label') === text,
  )
  if (!button) throw new Error(`No button "${text}"`)
  return button
}

async function click(button: Element) {
  ;(button as HTMLElement).click()
  await flushPromises()
}

async function setInput(input: HTMLInputElement, value: string) {
  input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
  await flushPromises()
}

function rowNames(wrapper: VueWrapper): string[] {
  return wrapper.findAll('main tbody tr').map((row) => row.find('td').text())
}

function mobileRowNames(wrapper: VueWrapper): string[] {
  return wrapper.findAll('main ul li > button').map((row) => row.find('span.truncate').text())
}

function row(wrapper: VueWrapper, name: string) {
  const match = wrapper
    .findAll('main tbody tr')
    .find((candidate) => candidate.text().includes(name))
  if (!match) throw new Error(`No row "${name}"`)
  return match
}

// The filters are driven through the component, as the other page specs do: jsdom lacks the pointer
// capture API that Reka UI's select trigger calls on a real pointer interaction.
async function openFilters(wrapper: VueWrapper) {
  const toggle = buttonIn(wrapper.element as HTMLElement, 'Filters')
  if (toggle.getAttribute('aria-expanded') !== 'true') await click(toggle)
}

function filterSelect(wrapper: VueWrapper, label: string) {
  const match = wrapper
    .findAllComponents(USelect)
    .find((select) => select.vm.$attrs['aria-label'] === label)
  if (!match) throw new Error(`No filter "${label}"`)
  return match
}

function filterItems(wrapper: VueWrapper, label: string): { value: string; label: string }[] {
  const props = filterSelect(wrapper, label).props() as {
    items: { value: string; label: string }[]
  }
  return props.items
}

async function chooseFilter(wrapper: VueWrapper, label: string, option: string) {
  await openFilters(wrapper)
  const item = filterItems(wrapper, label).find((candidate) => candidate.label === option)
  if (!item) throw new Error(`No option "${option}"`)
  filterSelect(wrapper, label).vm.$emit('update:modelValue', item.value)
  await flushPromises()
}

function optionLabels(wrapper: VueWrapper, label: string): string[] {
  return filterItems(wrapper, label).map((item) => item.label)
}

describe('AccountsPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.stubGlobal('localStorage', {
      getItem: vi.fn(() => null),
      setItem: vi.fn(),
      removeItem: vi.fn(),
    })
    mocks.desktop.value = true
    for (const mock of Object.values(mocks)) {
      if (typeof mock === 'function') mock.mockReset()
    }
    mocks.listAccounts.mockResolvedValue(ACCOUNTS)
    mocks.getAccountBalances.mockImplementation(async (ids: string[]) => balanceRows(ids))
    mocks.listCurrencies.mockResolvedValue([
      { code: 'EUR', minorUnit: 2, name: 'Euro' },
      { code: 'USD', minorUnit: 2, name: 'US Dollar' },
    ])
    mocks.listHouseholds.mockResolvedValue([])
    mocks.listSharedAccounts.mockResolvedValue([])
    mocks.createAccount.mockResolvedValue(account({}))
    mocks.updateAccount.mockResolvedValue(account({}))
    mocks.archiveAccount.mockResolvedValue(undefined)
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
    vi.unstubAllGlobals()
  })

  describe('list', () => {
    it('lists only active accounts with one h1 per breakpoint and the design columns', async () => {
      const wrapper = await mountPage()

      expect(rowNames(wrapper)).toEqual([
        'Daily account',
        'Savings',
        'Credit card',
        'Salary',
        'Living expenses',
        'USD cash',
        'Jamie wallet',
      ])
      expect(wrapper.findAll('h1')).toHaveLength(2)
      expect(wrapper.find('h1.lg\\:hidden').exists()).toBe(true)
      expect(wrapper.findAll('main thead th').map((th) => th.text())).toEqual([
        'Account',
        'Type',
        'Currency',
        'Balance',
        'Actions',
      ])
      expect(wrapper.text()).toContain('A clear home for every balance.')

      wrapper.unmount()
    })

    it('loads the balances of all active accounts in one batch and formats them by currency', async () => {
      const wrapper = await mountPage()

      expect(mocks.getAccountBalances).toHaveBeenCalledTimes(1)
      expect(mocks.getAccountBalances).toHaveBeenCalledWith(
        ['daily', 'savings', 'card', 'salary', 'living', 'usd', 'jamie'],
        undefined,
        undefined,
      )
      expect(mocks.listAccounts).toHaveBeenCalledTimes(1)
      expect(row(wrapper, 'Daily account').text()).toContain('€1,845.00')
      expect(row(wrapper, 'USD cash').text()).toContain('$50.00')

      wrapper.unmount()
    })

    it('shows "..." while a balance is unknown and stays silent when balances fail to load', async () => {
      mocks.getAccountBalances.mockRejectedValue(new Error('boom'))
      const wrapper = await mountPage()

      expect(row(wrapper, 'Daily account').text()).toContain('...')
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(rowNames(wrapper)).toContain('Daily account')

      wrapper.unmount()
    })

    it('uses the type badge colours of the colour language', async () => {
      const wrapper = await mountPage()

      const badge = (name: string) =>
        row(wrapper, name).get('td:nth-child(2) span').classes().join(' ')
      expect(badge('Salary')).toContain('success')
      expect(badge('Living expenses')).toContain('error')
      expect(badge('Credit card')).toContain('warning')
      expect(badge('Daily account')).toContain('primary')

      wrapper.unmount()
    })

    it('shows the loading, error and empty states', async () => {
      mocks.listAccounts.mockRejectedValue(new Error('boom'))
      const failed = await mountPage()
      expect(failed.get('[role="alert"]').text()).toContain('Failed to load accounts.')
      expect(failed.find('main table').exists()).toBe(false)
      failed.unmount()
      document.body.innerHTML = ''

      mocks.listAccounts.mockResolvedValue([])
      const empty = await mountPage()
      expect(empty.text()).toContain('No accounts found.')
      empty.unmount()
      document.body.innerHTML = ''

      mocks.listAccounts.mockReturnValue(new Promise(() => {}))
      const loading = await mountPage()
      expect(loading.text()).toContain('Loading accounts...')
      loading.unmount()
    })
  })

  describe('summary cards', () => {
    it('totals asset balances as money available and liability balances as debt', async () => {
      const wrapper = await mountPage()

      expect(wrapper.text()).toContain('Money available')
      expect(wrapper.text()).toContain('Debt outstanding')
      const cards = wrapper.findAll('main [data-slot="root"]').map((card) => card.text())
      const available = cards.find((text) => text.includes('Money available'))!
      const debt = cards.find((text) => text.includes('Debt outstanding'))!
      // Own and housemate EUR assets (1,845 + 1,000 + 200) with the USD account as a secondary amount.
      expect(available).toContain('€3,045.00')
      expect(available).toContain('EUR accounts')
      expect(available).toContain('$50.00')
      expect(debt).toContain('€320.00')

      wrapper.unmount()
    })

    it('follows the currency filter and makes no extra requests', async () => {
      const wrapper = await mountPage()

      await chooseFilter(wrapper, 'Filter currency', 'USD')

      const text = wrapper.get('main').text()
      expect(text).toContain('$50.00')
      expect(text).toContain('USD accounts')
      expect(mocks.getAccountBalances).toHaveBeenCalledTimes(1)
      expect(mocks.listAccounts).toHaveBeenCalledTimes(1)

      wrapper.unmount()
    })

    it('shows "..." instead of a total while a balance is unknown', async () => {
      mocks.getAccountBalances.mockRejectedValue(new Error('boom'))
      const wrapper = await mountPage()

      const first = wrapper.findAll('main [data-slot="root"]')[0]!
      expect(first.text()).not.toContain('€0.00')
      expect(first.text()).toContain('...')

      wrapper.unmount()
    })
  })

  describe('filters', () => {
    it('filters by type', async () => {
      const wrapper = await mountPage()

      await chooseFilter(wrapper, 'Filter type', 'Liability')

      expect(rowNames(wrapper)).toEqual(['Credit card'])

      await chooseFilter(wrapper, 'Filter type', 'All types')
      expect(rowNames(wrapper)).toHaveLength(7)

      wrapper.unmount()
    })

    it('derives the currencies from the active accounts and filters by currency', async () => {
      const wrapper = await mountPage()

      await openFilters(wrapper)
      expect(optionLabels(wrapper, 'Filter currency')).toEqual(['All currencies', 'EUR', 'USD'])
      await chooseFilter(wrapper, 'Filter currency', 'USD')

      expect(rowNames(wrapper)).toEqual(['USD cash'])

      wrapper.unmount()
    })

    it('keeps the filters out of the way until the Filters button opens them', async () => {
      const wrapper = await mountPage()
      const toggle = buttonIn(wrapper.element as HTMLElement, 'Filters')

      expect(toggle.getAttribute('aria-expanded')).toBe('false')
      expect(wrapper.find('button[aria-label="Filter type"]').exists()).toBe(false)
      expect(wrapper.find('#account-filters').exists()).toBe(false)

      await click(toggle)
      expect(toggle.getAttribute('aria-expanded')).toBe('true')
      expect(toggle.getAttribute('aria-controls')).toBe('account-filters')
      expect(wrapper.find('#account-filters').exists()).toBe(true)
      expect(wrapper.find('button[aria-label="Filter type"]').exists()).toBe(true)
      expect(wrapper.find('button[aria-label="Filter currency"]').exists()).toBe(true)
      expect(wrapper.text()).not.toContain('Clear filters')

      await click(toggle)
      expect(wrapper.find('#account-filters').exists()).toBe(false)

      wrapper.unmount()
    })

    it('counts the filters in use, clears them, and keeps them applied while the panel is closed', async () => {
      const wrapper = await mountPage()
      const toggle = buttonIn(wrapper.element as HTMLElement, 'Filters')
      expect(toggle.textContent).not.toMatch(/\d/)

      await chooseFilter(wrapper, 'Filter type', 'Liability')
      expect(toggle.textContent).toContain('1')
      await chooseFilter(wrapper, 'Filter currency', 'EUR')
      expect(toggle.textContent).toContain('2')

      // Closing the panel keeps both filters applied and counted.
      await click(toggle)
      expect(wrapper.find('#account-filters').exists()).toBe(false)
      expect(rowNames(wrapper)).toEqual(['Credit card'])
      expect(toggle.textContent).toContain('2')

      await click(toggle)
      await click(buttonIn(wrapper.element as HTMLElement, 'Clear filters'))
      expect(rowNames(wrapper)).toHaveLength(7)
      expect(toggle.textContent).not.toMatch(/\d/)
      expect(wrapper.text()).not.toContain('Clear filters')

      wrapper.unmount()
    })

    it('shows the filtered empty state', async () => {
      const wrapper = await mountPage()

      await chooseFilter(wrapper, 'Filter currency', 'USD')
      await chooseFilter(wrapper, 'Filter type', 'Liability')

      expect(wrapper.text()).toContain('No accounts found.')

      wrapper.unmount()
    })
  })

  describe('ownership', () => {
    it('offers rename and archive only for accounts the current user owns', async () => {
      const wrapper = await mountPage()

      expect(wrapper.find('[aria-label="Edit Daily account"]').exists()).toBe(true)
      expect(wrapper.find('[aria-label="Archive account Daily account"]').exists()).toBe(true)
      const shared = row(wrapper, 'Jamie wallet')
      expect(shared.find('button').exists()).toBe(false)
      expect(shared.text()).toContain('Not your account · Shared by another household member')

      wrapper.unmount()
    })
  })

  describe('rename', () => {
    it('names the action Edit on both breakpoints and gives the name field the shared name icon', async () => {
      const wrapper = await mountPage()

      const edit = wrapper.get('[aria-label="Edit Daily account"]')
      expect(edit.findComponent(UIcon).props('name')).toBe('i-lucide-pencil')
      expect(wrapper.find('[aria-label="Rename account Daily account"]').exists()).toBe(false)

      await click(edit.element)
      dialogByTitle('Rename Account')
      const icons = wrapper.findAllComponents(UIcon).map((icon) => icon.props('name'))
      expect(icons).toContain('i-lucide-text-cursor-input')

      wrapper.unmount()
    })

    it('renames through an overlay prefilled with the name and reloads the accounts', async () => {
      const wrapper = await mountPage()

      await click(wrapper.get('[aria-label="Edit Daily account"]').element)
      const dialog = dialogByTitle('Rename Account')
      expect(dialog.textContent).toContain('Enter a new name for "Daily account".')
      const input = dialog.querySelector<HTMLInputElement>('input')!
      expect(input.value).toBe('Daily account')

      await setInput(input, '  Everyday  ')
      await click(buttonIn(dialog, 'Save'))

      expect(mocks.updateAccount).toHaveBeenCalledWith('daily', { name: 'Everyday' })
      expect(mocks.listAccounts).toHaveBeenCalledTimes(2)
      expect(dialogs()).toHaveLength(0)

      wrapper.unmount()
    })

    it('cannot save an empty name', async () => {
      const wrapper = await mountPage()

      await click(wrapper.get('[aria-label="Edit Daily account"]').element)
      const dialog = dialogByTitle('Rename Account')
      await setInput(dialog.querySelector<HTMLInputElement>('input')!, '   ')

      expect(buttonIn(dialog, 'Save').disabled).toBe(true)

      wrapper.unmount()
    })

    it('keeps the overlay open and shows an error when renaming fails', async () => {
      mocks.updateAccount.mockRejectedValue(new Error('boom'))
      const wrapper = await mountPage()

      await click(wrapper.get('[aria-label="Edit Daily account"]').element)
      const dialog = dialogByTitle('Rename Account')
      await setInput(dialog.querySelector<HTMLInputElement>('input')!, 'Everyday')
      await click(buttonIn(dialog, 'Save'))

      expect(dialogByTitle('Rename Account').textContent).toContain('Failed to rename account.')
      expect(mocks.listAccounts).toHaveBeenCalledTimes(1)

      wrapper.unmount()
    })

    it('closes without renaming through Cancel', async () => {
      const wrapper = await mountPage()

      await click(wrapper.get('[aria-label="Edit Daily account"]').element)
      await click(buttonIn(dialogByTitle('Rename Account'), 'Cancel'))

      expect(mocks.updateAccount).not.toHaveBeenCalled()
      expect(dialogs()).toHaveLength(0)

      wrapper.unmount()
    })
  })

  describe('archive', () => {
    it('archives only after confirmation and reloads the accounts and balances', async () => {
      const wrapper = await mountPage()

      await click(wrapper.get('[aria-label="Archive account Daily account"]').element)
      expect(mocks.archiveAccount).not.toHaveBeenCalled()
      const dialog = dialogByTitle('Archive Account')
      expect(dialog.textContent).toContain('Archive "Daily account"?')

      await click(buttonIn(dialog, 'Archive account'))

      expect(mocks.archiveAccount).toHaveBeenCalledTimes(1)
      expect(mocks.archiveAccount).toHaveBeenCalledWith('daily')
      expect(mocks.listAccounts).toHaveBeenCalledTimes(2)
      expect(mocks.getAccountBalances).toHaveBeenCalledTimes(2)
      expect(dialogs()).toHaveLength(0)

      wrapper.unmount()
    })

    it('does not archive when the confirmation is cancelled', async () => {
      const wrapper = await mountPage()

      await click(wrapper.get('[aria-label="Archive account Daily account"]').element)
      await click(buttonIn(dialogByTitle('Archive Account'), 'Cancel'))

      expect(mocks.archiveAccount).not.toHaveBeenCalled()
      expect(dialogs()).toHaveLength(0)

      wrapper.unmount()
    })

    it('shows the server message on the page when archiving fails', async () => {
      mocks.archiveAccount.mockRejectedValue(httpError('Account still has a balance'))
      const wrapper = await mountPage()

      await click(wrapper.get('[aria-label="Archive account Daily account"]').element)
      await click(buttonIn(dialogByTitle('Archive Account'), 'Archive account'))

      expect(wrapper.get('[role="alert"]').text()).toContain('Account still has a balance')
      expect(dialogs()).toHaveLength(0)
      expect(mocks.listAccounts).toHaveBeenCalledTimes(1)
      expect(rowNames(wrapper)).toContain('Daily account')

      wrapper.unmount()
    })

    it('falls back to the generic message when the failure carries none', async () => {
      mocks.archiveAccount.mockRejectedValue(new Error('request failed'))
      const wrapper = await mountPage()

      await click(wrapper.get('[aria-label="Archive account Daily account"]').element)
      await click(buttonIn(dialogByTitle('Archive Account'), 'Archive account'))

      expect(wrapper.get('[role="alert"]').text()).toContain('Failed to archive account.')

      wrapper.unmount()
    })

    it('ignores a second confirmation while the archive request is running', async () => {
      let finish: () => void = () => {}
      mocks.archiveAccount.mockReturnValue(new Promise<void>((resolve) => (finish = resolve)))
      const wrapper = await mountPage()

      await click(wrapper.get('[aria-label="Archive account Daily account"]').element)
      const confirm = buttonIn(dialogByTitle('Archive Account'), 'Archive account')
      await click(confirm)
      await click(confirm)

      expect(mocks.archiveAccount).toHaveBeenCalledTimes(1)
      expect(
        wrapper.get<HTMLButtonElement>('[aria-label="Archive account Savings"]').element.disabled,
      ).toBe(true)

      finish()
      await flushPromises()
      wrapper.unmount()
    })
  })

  describe('create', () => {
    async function openCreate(wrapper: VueWrapper) {
      await click(buttonIn(wrapper.element as HTMLElement, 'New account'))
      return dialogByTitle('Create Account')
    }

    it('creates an account with the opening balance and reloads the page data', async () => {
      const wrapper = await mountPage()
      const dialog = await openCreate(wrapper)

      expect(buttonIn(dialog, 'Create Account').disabled).toBe(true)
      await setInput(
        dialog.querySelector<HTMLInputElement>('input[id$="-name"]')!,
        'Holiday savings',
      )
      await setInput(
        dialog.querySelector<HTMLInputElement>('input[id$="-initial-balance"]')!,
        '500',
      )
      expect(buttonIn(dialog, 'Create Account').disabled).toBe(false)
      await click(buttonIn(dialog, 'Create Account'))

      expect(mocks.createAccount).toHaveBeenCalledTimes(1)
      expect(mocks.createAccount.mock.calls[0]![0]).toMatchObject({
        name: 'Holiday savings',
        type: 'ASSET',
        currency: 'EUR',
        openingBalanceMinor: 50000,
      })
      expect(mocks.listAccounts).toHaveBeenCalledTimes(2)
      expect(mocks.getAccountBalances).toHaveBeenCalledTimes(2)
      expect(dialogs()).toHaveLength(0)

      wrapper.unmount()
    })

    it('starts every new account from an empty draft', async () => {
      const wrapper = await mountPage()
      let dialog = await openCreate(wrapper)
      await setInput(dialog.querySelector<HTMLInputElement>('input[id$="-name"]')!, 'Draft')
      await click(buttonIn(dialog, 'Cancel'))

      dialog = await openCreate(wrapper)
      expect(dialog.querySelector<HTMLInputElement>('input[id$="-name"]')!.value).toBe('')

      wrapper.unmount()
    })

    it('keeps the overlay open and shows an error when creating fails', async () => {
      mocks.createAccount.mockRejectedValue(new Error('boom'))
      const wrapper = await mountPage()
      const dialog = await openCreate(wrapper)

      await setInput(dialog.querySelector<HTMLInputElement>('input[id$="-name"]')!, 'Holiday')
      await click(buttonIn(dialog, 'Create Account'))

      expect(dialogByTitle('Create Account').textContent).toContain('Failed to create account.')
      expect(mocks.listAccounts).toHaveBeenCalledTimes(1)

      wrapper.unmount()
    })

    it('does not close on a backdrop click', async () => {
      const wrapper = await mountPage()
      await openCreate(wrapper)

      const overlay = document.body.querySelector<HTMLElement>(
        '[data-state="open"][class*="fixed"]',
      )
      overlay?.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }))
      await flushPromises()

      expect(dialogs()).toHaveLength(1)

      wrapper.unmount()
    })
  })

  describe('household mode', () => {
    beforeEach(() => {
      // The household store drops a selected household that is not in the member's list.
      mocks.listHouseholds.mockResolvedValue([
        {
          id: 'h1',
          name: 'Morgan household',
          role: 'OWNER',
          status: 'ACTIVE',
          rolloverDay: 1,
          createdAt: '',
        },
      ])
      mocks.listSharedAccounts.mockResolvedValue([{ accountId: 'daily' }, { accountId: 'jamie' }])
    })

    it('loads the shared accounts, marks them and counts only them in the summary', async () => {
      useModeStore().switchToHousehold('h1')
      const wrapper = await mountPage()

      expect(mocks.listSharedAccounts).toHaveBeenCalledWith('h1')
      expect(mocks.getAccountBalances).toHaveBeenCalledWith(expect.any(Array), undefined, 'h1')
      expect(wrapper.findAll('main thead th').map((th) => th.text())).toContain('Shared')
      expect(row(wrapper, 'Daily account').text()).toContain('Shared')
      expect(row(wrapper, 'Savings').text()).not.toContain('Shared')
      const available = wrapper
        .findAll('main [data-slot="root"]')
        .find((card) => card.text().includes('Money available'))!
      expect(available.text()).toContain('€2,045.00')

      wrapper.unmount()
    })

    it('has no Shared column outside household mode', async () => {
      const wrapper = await mountPage()

      expect(wrapper.findAll('main thead th').map((th) => th.text())).not.toContain('Shared')
      expect(mocks.listSharedAccounts).not.toHaveBeenCalled()

      wrapper.unmount()
    })

    it('reloads when the view mode changes', async () => {
      const wrapper = await mountPage()
      expect(mocks.listAccounts).toHaveBeenCalledTimes(1)

      useModeStore().switchToHousehold('h1')
      await nextTick()
      await flushPromises()

      expect(mocks.listAccounts).toHaveBeenCalledTimes(2)
      expect(mocks.listSharedAccounts).toHaveBeenCalledTimes(1)
      expect(mocks.getAccountBalances).toHaveBeenCalledTimes(2)

      wrapper.unmount()
    })
  })

  describe('mobile', () => {
    beforeEach(() => {
      mocks.desktop.value = false
    })

    it('shows a readable list with the summary and a sticky New account action', async () => {
      const wrapper = await mountPage()

      expect(wrapper.find('main table').exists()).toBe(false)
      expect(mobileRowNames(wrapper)).toEqual([
        'Daily account',
        'Savings',
        'Credit card',
        'Salary',
        'Living expenses',
        'USD cash',
        'Jamie wallet',
      ])
      const first = wrapper.get('main ul li > button')
      expect(first.text()).toContain('Asset · EUR')
      expect(first.text()).toContain('€1,845.00')
      expect(wrapper.text()).toContain('Money available')
      expect(wrapper.text()).toContain('€2,845.00'.replace('2,845', '3,045'))
      expect(wrapper.find('[aria-label="Edit Daily account"]').exists()).toBe(false)

      wrapper.unmount()
    })

    it('expands a row in place to show Edit and Archive', async () => {
      const wrapper = await mountPage()

      const toggle = wrapper.get('main ul li > button')
      expect(toggle.attributes('aria-expanded')).toBe('false')
      await toggle.trigger('click')

      expect(toggle.attributes('aria-expanded')).toBe('true')
      const panel = wrapper.get('#account-actions-daily')
      expect(panel.text()).toContain('Edit')
      expect(panel.text()).toContain('Archive')

      await click(panel.get('[aria-label="Archive account Daily account"]').element)
      await click(buttonIn(dialogByTitle('Archive Account'), 'Archive account'))
      expect(mocks.archiveAccount).toHaveBeenCalledWith('daily')

      wrapper.unmount()
    })

    it('shows the shared-from-another-member note instead of actions for foreign accounts', async () => {
      const wrapper = await mountPage()

      const rows = wrapper.findAll('main ul li > button')
      await rows[rows.length - 1]!.trigger('click')

      const panel = wrapper.get('#account-actions-jamie')
      expect(panel.text()).toContain('Not your account · Shared by another household member')
      expect(panel.find('button').exists()).toBe(false)

      wrapper.unmount()
    })

    it('offers an icon-only Filters button and a labelled Clear action below lg', async () => {
      const wrapper = await mountPage()
      const toggle = buttonIn(wrapper.element as HTMLElement, 'Filters')

      expect(toggle.textContent?.trim()).toBe('')
      await chooseFilter(wrapper, 'Filter type', 'Liability')
      const clear = buttonIn(wrapper.element as HTMLElement, 'Clear filters')
      expect(clear.textContent).toContain('Clear filters')

      wrapper.unmount()
    })

    it('opens the create overlay from the footer button', async () => {
      const wrapper = await mountPage()

      await click(buttonIn(document.body, 'New account'))

      expect(dialogByTitle('Create Account')).toBeTruthy()

      wrapper.unmount()
    })
  })
})
