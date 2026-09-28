import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { HTTPError } from 'ky'
import { h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import UDashboardGroup from '@nuxt/ui/components/DashboardGroup.vue'
import { i18n } from '@/i18n'
import { useAuthStore } from '@/stores/auth'
import type { CategoryTag } from '@/types/ledger'
import CategoriesPage from '@/views/CategoriesPage.vue'

const mocks = vi.hoisted(() => ({
  desktop: { value: true },
  listCategories: vi.fn(),
  createCategory: vi.fn(),
  updateCategory: vi.fn(),
  deleteCategory: vi.fn(),
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
vi.mock('@/api/households', () => ({ listHouseholds: vi.fn().mockResolvedValue([]) }))
vi.mock('@/api/categories', () => ({
  listCategories: mocks.listCategories,
  createCategory: mocks.createCategory,
  updateCategory: mocks.updateCategory,
  deleteCategory: mocks.deleteCategory,
}))

function category(id: string, name: string, color: string | null = '#62a864'): CategoryTag {
  return { id, name, color, createdAt: '2026-09-12T09:00:00Z' }
}

const CATEGORIES = [
  category('c2', 'Transport', '#8391a5'),
  category('c1', 'Groceries', '#62a864'),
  category('c3', 'Health', '#e45a77'),
]

function httpError(message: string, status = 409): HTTPError {
  const request = new Request('http://localhost/api')
  const response = new Response(JSON.stringify({ message }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
  return new HTTPError(response, request, {} as never)
}

async function mountCategories() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/categories', name: 'categories', component: CategoriesPage },
      { path: '/dashboard', name: 'dashboard', component: { render: () => null } },
    ],
  })
  await router.push('/categories')
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

// A closed drawer stays mounted while its exit animation would run, so only open ones count.
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

async function click(button: HTMLButtonElement) {
  button.click()
  await flushPromises()
}

async function typeName(root: ParentNode, value: string) {
  const input = root.querySelector<HTMLInputElement>('input:not([type="radio"])')
  if (!input) throw new Error('No name input')
  input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
  await flushPromises()
}

function rowNames(wrapper: VueWrapper): string[] {
  return wrapper.findAll('main tbody tr').map((row) => row.find('td').text())
}

describe('CategoriesPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mocks.desktop.value = true
    for (const mock of [
      mocks.listCategories,
      mocks.createCategory,
      mocks.updateCategory,
      mocks.deleteCategory,
    ]) {
      mock.mockReset()
    }
    mocks.listCategories.mockResolvedValue([...CATEGORIES])
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

  it('lists categories alphabetically with one h1 per breakpoint', async () => {
    const wrapper = await mountCategories()

    expect(rowNames(wrapper)).toEqual(['Groceries', 'Health', 'Transport'])
    expect(wrapper.findAll('h1')).toHaveLength(2)
    expect(wrapper.find('h1.lg\\:hidden').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('Your personal spending categories')
    expect(wrapper.text()).not.toContain('Small labels.')
    expect(wrapper.find('[aria-label="Edit Groceries"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="Delete Groceries"]').exists()).toBe(true)

    wrapper.unmount()
  })

  it('shows a readable list with row menus on mobile', async () => {
    mocks.desktop.value = false
    const wrapper = await mountCategories()

    expect(wrapper.find('main table').exists()).toBe(false)
    expect(wrapper.findAll('main ul li').map((row) => row.find('p').text())).toEqual([
      'Groceries',
      'Health',
      'Transport',
    ])
    expect(wrapper.find('[aria-label="More actions for Health"]').exists()).toBe(true)

    wrapper.unmount()
  })

  it('shows the load error instead of the list', async () => {
    mocks.listCategories.mockRejectedValue(httpError('Categories are unavailable', 500))
    const wrapper = await mountCategories()

    expect(wrapper.get('[role="alert"]').text()).toContain('Categories are unavailable')
    expect(wrapper.find('main table').exists()).toBe(false)

    wrapper.unmount()
  })

  it('shows the empty state', async () => {
    mocks.listCategories.mockResolvedValue([])
    const wrapper = await mountCategories()

    expect(wrapper.text()).toContain('No categories yet.')

    wrapper.unmount()
  })

  it('creates a category with a colour', async () => {
    mocks.createCategory.mockImplementation(async (req: { name: string; color: string | null }) =>
      category('c9', req.name, req.color),
    )
    const wrapper = await mountCategories()

    await click(buttonIn(document.body, 'New category'))
    const dialog = dialogByTitle('Create Category')
    const submit = buttonIn(dialog, 'Create Category')
    expect(submit.disabled).toBe(true)

    await typeName(dialog, '  Dining out ')
    dialog.querySelector<HTMLInputElement>('input[aria-label="Blue"]')?.click()
    await flushPromises()
    expect(dialog.textContent).toContain('Dining out')
    await click(submit)

    expect(mocks.createCategory).toHaveBeenCalledWith({ name: 'Dining out', color: '#4a7fd0' })
    expect(rowNames(wrapper)).toContain('Dining out')
    expect(dialogs()).toHaveLength(0)

    wrapper.unmount()
  })

  it('rejects a duplicate name on the field without calling the API', async () => {
    const wrapper = await mountCategories()

    await click(buttonIn(document.body, 'New category'))
    const dialog = dialogByTitle('Create Category')
    await typeName(dialog, 'groceries')
    await click(buttonIn(dialog, 'Create Category'))

    expect(mocks.createCategory).not.toHaveBeenCalled()
    const input = dialog.querySelector('input:not([type="radio"])')
    expect(input?.getAttribute('aria-invalid')).toBe('true')
    expect(dialog.textContent).toContain("Category 'groceries' already exists.")
    expect(dialog.querySelector('[role="alert"]')).toBeNull()

    wrapper.unmount()
  })

  it('shows a server error at the top of the form and stays open', async () => {
    mocks.createCategory.mockRejectedValue(httpError("Category 'Dining out' already exists"))
    const wrapper = await mountCategories()

    await click(buttonIn(document.body, 'New category'))
    const dialog = dialogByTitle('Create Category')
    await typeName(dialog, 'Dining out')
    await click(buttonIn(dialog, 'Create Category'))

    const alert = dialog.querySelector('form > [role="alert"]')
    expect(alert?.textContent).toContain("Category 'Dining out' already exists")
    expect(dialog.querySelector('form')?.firstElementChild).toBe(alert)
    expect(dialogs()).toHaveLength(1)
    expect(rowNames(wrapper)).toEqual(['Groceries', 'Health', 'Transport'])

    wrapper.unmount()
  })

  it.each([
    ['modal', true],
    ['drawer', false],
  ])('edits a category in a prefilled %s', async (_label, desktop) => {
    mocks.desktop.value = desktop
    mocks.updateCategory.mockImplementation(
      async (id: string, req: { name: string; color: string | null }) =>
        category(id, req.name, req.color),
    )
    const wrapper = await mountCategories()

    await click(buttonIn(document.body, 'Edit Health'))
    const dialog = dialogByTitle('Edit category')
    expect(
      Boolean(dialog.closest('[data-vaul-drawer]') ?? dialog.hasAttribute('data-vaul-drawer')),
    ).toBe(!desktop)
    expect(wrapper.find('section[role="dialog"]').exists()).toBe(false)
    expect(dialog.querySelector<HTMLInputElement>('input:not([type="radio"])')?.value).toBe(
      'Health',
    )
    expect(dialog.querySelector<HTMLInputElement>('input[aria-label="Rose"]')?.checked).toBe(true)

    await typeName(dialog, 'Wellbeing')
    dialog.querySelector<HTMLInputElement>('input[aria-label="No color"]')?.click()
    await flushPromises()
    await click(buttonIn(dialog, 'Save changes'))

    expect(mocks.updateCategory).toHaveBeenCalledWith('c3', { name: 'Wellbeing', color: null })
    expect(dialogs()).toHaveLength(0)
    const names = desktop
      ? rowNames(wrapper)
      : wrapper.findAll('main ul li').map((row) => row.find('p').text())
    expect(names).toEqual(['Groceries', 'Transport', 'Wellbeing'])

    wrapper.unmount()
  })

  it('keeps the list in the same columns while editing on desktop', async () => {
    const wrapper = await mountCategories()

    const listColumn = wrapper.get('main table').element.closest('.lg\\:col-span-3')
    expect(listColumn).not.toBeNull()
    await click(buttonIn(document.body, 'Edit Health'))
    expect(wrapper.get('main table').element.closest('.lg\\:col-span-3')).toBe(listColumn)

    wrapper.unmount()
  })

  it('keeps an off-palette colour selectable when editing', async () => {
    mocks.listCategories.mockResolvedValue([category('c7', 'Legacy', '#ef4444')])
    const wrapper = await mountCategories()

    await click(buttonIn(document.body, 'Edit Legacy'))
    const current = dialogByTitle('Edit category').querySelector<HTMLInputElement>(
      'input[aria-label="Current color"]',
    )
    expect(current?.checked).toBe(true)

    wrapper.unmount()
  })

  it('allows the edited category to keep its own name', async () => {
    mocks.updateCategory.mockImplementation(
      async (id: string, req: { name: string; color: string | null }) =>
        category(id, req.name, req.color),
    )
    const wrapper = await mountCategories()

    await click(buttonIn(document.body, 'Edit Health'))
    const dialog = dialogByTitle('Edit category')
    await typeName(dialog, 'HEALTH')
    await click(buttonIn(dialog, 'Save changes'))

    expect(mocks.updateCategory).toHaveBeenCalledWith('c3', { name: 'HEALTH', color: '#e45a77' })

    wrapper.unmount()
  })

  it('never shows the edit and create overlays together', async () => {
    const wrapper = await mountCategories()

    await click(buttonIn(document.body, 'Edit Health'))
    expect(dialogs()).toHaveLength(1)
    expect(dialogs()[0]?.textContent).toContain('Edit category')

    await click(buttonIn(document.body, 'New category'))
    expect(dialogs().map((dialog) => dialog.querySelector('h2')?.textContent?.trim())).toEqual([
      'Create Category',
    ])

    wrapper.unmount()
  })

  it('never shows the edit and create drawers together on mobile', async () => {
    mocks.desktop.value = false
    const wrapper = await mountCategories()

    await click(buttonIn(document.body, 'Edit Health'))
    expect(dialogs()).toHaveLength(1)
    expect(dialogs()[0]?.textContent).toContain('Edit category')

    await click(buttonIn(dialogs()[0]!, 'Cancel'))
    await click(buttonIn(document.body, 'New category'))
    expect(dialogs()).toHaveLength(1)
    expect(dialogs()[0]?.textContent).toContain('Create Category')

    wrapper.unmount()
  })

  it('deletes only after confirmation', async () => {
    mocks.deleteCategory.mockResolvedValue(undefined)
    const wrapper = await mountCategories()

    await click(buttonIn(document.body, 'Delete Transport'))
    expect(mocks.deleteCategory).not.toHaveBeenCalled()
    const dialog = dialogByTitle('Delete category "Transport"?')

    await click(buttonIn(dialog, 'Cancel'))
    expect(mocks.deleteCategory).not.toHaveBeenCalled()

    await click(buttonIn(document.body, 'Delete Transport'))
    await click(buttonIn(dialogByTitle('Delete category "Transport"?'), 'Delete category'))

    expect(mocks.deleteCategory).toHaveBeenCalledWith('c2')
    expect(rowNames(wrapper)).toEqual(['Groceries', 'Health'])

    wrapper.unmount()
  })

  it('shows the delete error above the list', async () => {
    mocks.deleteCategory.mockRejectedValue(httpError('Category is in use and cannot be deleted'))
    const wrapper = await mountCategories()

    await click(buttonIn(document.body, 'Delete Transport'))
    await click(buttonIn(dialogByTitle('Delete category "Transport"?'), 'Delete category'))

    expect(wrapper.get('main [role="alert"]').text()).toContain(
      'Category is in use and cannot be deleted',
    )
    expect(rowNames(wrapper)).toEqual(['Groceries', 'Health', 'Transport'])

    wrapper.unmount()
  })
})
