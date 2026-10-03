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
import USelect from '@nuxt/ui/components/Select.vue'
import { i18n, setLocale } from '@/i18n'
import appRouter from '@/router'
import { useAuthStore } from '@/stores/auth'
import { useModeStore } from '@/stores/mode'
import type { AuthUser } from '@/types/auth'
import type { HouseholdSummary } from '@/types/household'
import SettingsPage from '@/views/SettingsPage.vue'

const mocks = vi.hoisted(() => ({
  desktop: { value: true },
  apiPost: vi.fn(),
  updateUserLanguage: vi.fn(),
  updateUserRolloverDay: vi.fn(),
  listHouseholds: vi.fn(),
  leaveHousehold: vi.fn(),
  createHousehold: vi.fn(),
  acceptInvite: vi.fn(),
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
vi.mock('@/api/http', () => ({ api: { post: mocks.apiPost, get: vi.fn() } }))
vi.mock('@/api/user', () => ({
  updateUserLanguage: mocks.updateUserLanguage,
  updateUserRolloverDay: mocks.updateUserRolloverDay,
}))
vi.mock('@/api/households', () => ({
  listHouseholds: mocks.listHouseholds,
  leaveHousehold: mocks.leaveHousehold,
  createHousehold: mocks.createHousehold,
  acceptInvite: mocks.acceptInvite,
}))

const USER: AuthUser = {
  id: 1,
  email: 'alex@example.com',
  firstName: 'Alex',
  lastName: 'Morgan',
  language: 'en',
  rolloverDay: 1,
  avatar: null,
}

function household(overrides: Partial<HouseholdSummary> = {}): HouseholdSummary {
  return {
    id: 'hh-1',
    name: 'Morgan household',
    role: 'OWNER',
    status: 'ACTIVE',
    rolloverDay: 1,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

function httpError(message: string): HTTPError {
  const request = new Request('http://localhost/api')
  const response = new Response(JSON.stringify({ message }), {
    status: 400,
    headers: { 'Content-Type': 'application/json' },
  })
  return new HTTPError(response, request, {} as never)
}

function imageFile(name = 'me.png', type = 'image/png', size = 10): File {
  return new File([new Uint8Array(size)], name, { type })
}

async function mountSettings() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/settings', name: 'settings', component: SettingsPage },
      {
        path: '/household/:householdId/settings',
        name: 'household-settings',
        component: { template: '<div data-test="household-settings" />' },
      },
    ],
  })
  await router.push('/settings')
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
  return { router, wrapper }
}

function buttonByText(wrapper: VueWrapper, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text() === text)
  if (!button) throw new Error(`No button "${text}"`)
  return button
}

function bodyButton(text: string): HTMLButtonElement {
  const button = [
    ...document.body.querySelectorAll<HTMLButtonElement>('[role="dialog"] button'),
  ].find((candidate) => candidate.textContent?.trim() === text)
  if (!button) throw new Error(`No dialog button "${text}"`)
  return button
}

async function pickFile(wrapper: VueWrapper, file: File) {
  const input = wrapper.get<HTMLInputElement>('[data-test="avatar-file-input"]')
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
  await flushPromises()
}

function avatarSrc(wrapper: VueWrapper): string | undefined {
  return wrapper.find('main img').attributes('src')
}

describe('SettingsPage', () => {
  let objectUrlCount = 0
  const revokeObjectURL = vi.fn()

  beforeEach(() => {
    setActivePinia(createPinia())
    mocks.desktop.value = true
    objectUrlCount = 0
    revokeObjectURL.mockReset()
    for (const mock of [
      mocks.apiPost,
      mocks.updateUserLanguage,
      mocks.updateUserRolloverDay,
      mocks.listHouseholds,
      mocks.leaveHousehold,
      mocks.createHousehold,
      mocks.acceptInvite,
    ]) {
      mock.mockReset()
    }
    mocks.listHouseholds.mockResolvedValue([household()])
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
    Element.prototype.scrollIntoView = vi.fn()
    Object.assign(URL, {
      createObjectURL: vi.fn(() => `blob:preview-${++objectUrlCount}`),
      revokeObjectURL,
    })
    useAuthStore().setUser({ ...USER })
  })

  afterEach(() => {
    setLocale('en')
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
  })

  it('keeps the settings route behind sign-in', () => {
    const route = appRouter.getRoutes().find((record) => record.name === 'settings')
    expect(route?.meta).toMatchObject({ requiresAuth: true })
  })

  it('shows the profile read-only, with one h1 per breakpoint', async () => {
    const { wrapper } = await mountSettings()

    expect(wrapper.find('header h1').text()).toBe('Settings')
    expect(wrapper.find('main h1').classes()).toContain('lg:hidden')
    expect(wrapper.find('main h2').text()).toBe('Make Pocketr yours')
    expect(wrapper.text()).toContain('Alex Morgan')
    expect(wrapper.text()).toContain('alex@example.com')
    const values = wrapper
      .findAll<HTMLInputElement>('main input')
      .map((input) => input.element.value)
    expect(values).not.toContain('Alex')
    expect(values).not.toContain('alex@example.com')

    wrapper.unmount()
  })

  describe('avatar', () => {
    it('previews a picked image without uploading, and discarding restores and revokes it', async () => {
      useAuthStore().setUser({ ...USER, avatar: '/avatars/current.png' })
      const { wrapper } = await mountSettings()

      await pickFile(wrapper, imageFile())

      expect(avatarSrc(wrapper)).toBe('blob:preview-1')
      expect(mocks.apiPost).not.toHaveBeenCalled()
      expect(wrapper.text()).toContain('Previewing me.png')
      expect(document.activeElement?.textContent?.trim()).toBe('Save photo')

      await buttonByText(wrapper, 'Cancel').trigger('click')
      await flushPromises()

      expect(avatarSrc(wrapper)).toBe('/avatars/current.png')
      expect(revokeObjectURL).toHaveBeenCalledWith('blob:preview-1')
      expect(document.activeElement?.textContent?.trim()).toBe('Change photo')

      wrapper.unmount()
    })

    it('revokes the previous preview when another image is picked, and on unmount', async () => {
      const { wrapper } = await mountSettings()

      await pickFile(wrapper, imageFile())
      await buttonByText(wrapper, 'Cancel').trigger('click')
      await pickFile(wrapper, imageFile('second.webp', 'image/webp'))
      expect(avatarSrc(wrapper)).toBe('blob:preview-2')

      wrapper.unmount()
      expect(revokeObjectURL).toHaveBeenCalledWith('blob:preview-1')
      expect(revokeObjectURL).toHaveBeenCalledWith('blob:preview-2')
    })

    it('rejects unsupported types and files over 5 MB before previewing', async () => {
      const { wrapper } = await mountSettings()

      await pickFile(wrapper, imageFile('notes.txt', 'text/plain'))
      expect(wrapper.get('[role="alert"]').text()).toContain(
        'Choose a JPG, PNG, GIF or WebP image.',
      )

      await pickFile(wrapper, imageFile('big.png', 'image/png', 5 * 1024 * 1024 + 1))
      expect(wrapper.get('[role="alert"]').text()).toContain('larger than 5 MB')

      expect(URL.createObjectURL).not.toHaveBeenCalled()
      expect(wrapper.text()).toContain('Change photo')

      wrapper.unmount()
    })

    it('uploads the confirmed image with the existing request and applies the new avatar', async () => {
      const updated = { ...USER, avatar: '/avatars/new.png' }
      mocks.apiPost.mockReturnValue({ json: vi.fn(async () => updated) })
      const { wrapper } = await mountSettings()

      const file = imageFile()
      await pickFile(wrapper, file)
      await buttonByText(wrapper, 'Save photo').trigger('click')
      await flushPromises()

      expect(mocks.apiPost).toHaveBeenCalledTimes(1)
      const [url, options] = mocks.apiPost.mock.calls[0] as [string, { body: FormData }]
      expect(url).toBe('/api/v1/user/avatar')
      expect(options.body.get('avatar')).toBe(file)
      expect(useAuthStore().user?.avatar).toBe('/avatars/new.png')
      expect(avatarSrc(wrapper)).toBe('/avatars/new.png')
      expect(revokeObjectURL).toHaveBeenCalledWith('blob:preview-1')
      expect(wrapper.get('[role="status"]').text()).toContain('Avatar updated.')

      wrapper.unmount()
    })

    it('keeps the preview and shows the server message when the upload fails', async () => {
      mocks.apiPost.mockReturnValue({
        json: vi.fn(async () => {
          throw httpError('Avatar file is too large. Maximum size is 5MB')
        }),
      })
      const { wrapper } = await mountSettings()

      await pickFile(wrapper, imageFile())
      await buttonByText(wrapper, 'Save photo').trigger('click')
      await flushPromises()

      expect(wrapper.get('[role="alert"]').text()).toContain('Maximum size is 5MB')
      expect(avatarSrc(wrapper)).toBe('blob:preview-1')
      expect(revokeObjectURL).not.toHaveBeenCalled()

      wrapper.unmount()
    })
  })

  describe('preferences', () => {
    /** Language and rollover selects; the view-mode select in the header is excluded. */
    function selects(wrapper: VueWrapper) {
      return wrapper
        .findAllComponents(USelect)
        .filter((select) => (select.element as Element).closest('form#settings-preferences'))
    }

    function rolloverInput(wrapper: VueWrapper) {
      return wrapper.getComponent(UInputNumber)
    }

    function saveButton(wrapper: VueWrapper) {
      return wrapper.get('form#settings-preferences button[type="submit"]')
    }

    it('saves only the changed preferences through their own endpoints', async () => {
      mocks.updateUserLanguage.mockImplementation(async (language) => ({ ...USER, language }))
      const { wrapper } = await mountSettings()

      expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
      selects(wrapper)[0]!.vm.$emit('update:modelValue', 'bg')
      await flushPromises()
      await saveButton(wrapper).trigger('submit')
      await flushPromises()

      expect(mocks.updateUserLanguage).toHaveBeenCalledWith('bg')
      expect(mocks.updateUserRolloverDay).not.toHaveBeenCalled()
      expect(i18n.global.locale.value).toBe('bg')
      expect(wrapper.get('[role="status"]').text()).toContain('Предпочитанията са запазени.')

      wrapper.unmount()
    })

    it('saves language and rollover day together', async () => {
      mocks.updateUserLanguage.mockImplementation(async (language) => ({ ...USER, language }))
      mocks.updateUserRolloverDay.mockImplementation(async (rolloverDay) => ({
        ...USER,
        language: 'de',
        rolloverDay,
      }))
      const { wrapper } = await mountSettings()

      selects(wrapper)[0]!.vm.$emit('update:modelValue', 'de')
      rolloverInput(wrapper).vm.$emit('update:modelValue', 15)
      await flushPromises()
      await wrapper.get('form#settings-preferences').trigger('submit')
      await flushPromises()

      expect(mocks.updateUserLanguage).toHaveBeenCalledWith('de')
      expect(mocks.updateUserRolloverDay).toHaveBeenCalledWith(15)
      expect(useAuthStore().user).toMatchObject({ language: 'de', rolloverDay: 15 })
      expect(saveButton(wrapper).attributes('disabled')).toBeDefined()

      wrapper.unmount()
    })

    it('shows a focused error when the rollover day cannot be saved', async () => {
      mocks.updateUserRolloverDay.mockRejectedValue(httpError('Rollover day rejected'))
      const { wrapper } = await mountSettings()

      rolloverInput(wrapper).vm.$emit('update:modelValue', 3)
      await flushPromises()
      await wrapper.get('form#settings-preferences').trigger('submit')
      await flushPromises()

      const alert = wrapper.get('form#settings-preferences [role="alert"]')
      expect(alert.text()).toContain('Rollover day rejected')
      expect(document.activeElement).toBe(alert.element)
      expect(useAuthStore().user?.rolloverDay).toBe(1)

      wrapper.unmount()
    })

    it('edits the rollover day in a 1-31 number field with step buttons', async () => {
      const { wrapper } = await mountSettings()

      const input = rolloverInput(wrapper)
      expect(input.props()).toMatchObject({ min: 1, max: 31, step: 1 })
      const field = input.get('input')
      expect(field.attributes('id')).toBe(
        wrapper
          .findAll('label')
          .find((label) => label.text() === 'Rollover period')
          ?.attributes('for'),
      )
      expect(input.findAll('button')).toHaveLength(2)
      expect(field.attributes('aria-describedby')).toBe('settings-rollover-help')
      expect(wrapper.get('#settings-rollover-help').classes()).toContain('sr-only')
      expect(wrapper.get('#settings-rollover-help').text()).toContain('month end')
      expect(wrapper.find('button[aria-label="About rollover period"]').exists()).toBe(true)
      expect(wrapper.text()).not.toContain('English, Bulgarian, German')

      wrapper.unmount()
    })

    it('shows the range error on the field when the rollover day is cleared', async () => {
      const { wrapper } = await mountSettings()

      rolloverInput(wrapper).vm.$emit('update:modelValue', null)
      await flushPromises()
      await wrapper.get('form#settings-preferences').trigger('submit')
      await flushPromises()

      expect(mocks.updateUserRolloverDay).not.toHaveBeenCalled()
      expect(wrapper.text()).toContain('Rollover day must be between 1 and 31.')
      expect(rolloverInput(wrapper).get('input').attributes('aria-invalid')).toBe('true')

      wrapper.unmount()
    })

    it('keeps a single Save inside the Preferences card on mobile', async () => {
      mocks.desktop.value = false
      const { wrapper } = await mountSettings()

      const saves = wrapper.findAll('button').filter((button) => button.text() === 'Save')
      expect(saves).toHaveLength(1)
      expect(saves[0]!.element.closest('form#settings-preferences')).not.toBeNull()
      expect(saves[0]!.attributes('aria-describedby')).toBe('settings-preferences-title')
      expect(saves[0]!.attributes('type')).toBe('submit')
      expect(wrapper.find('button[form="settings-preferences"]').exists()).toBe(false)

      wrapper.unmount()
    })
  })

  describe('household', () => {
    it('shows the membership with manage and leave actions', async () => {
      const { wrapper } = await mountSettings()

      expect(wrapper.text()).toContain('Morgan household')
      expect(wrapper.text()).toContain('Owner')
      const manage = wrapper.findAll('a').find((link) => link.text() === 'Manage household')
      expect(manage?.attributes('href')).toBe('/household/hh-1/settings')
      expect(wrapper.find('#household-name').exists()).toBe(false)
      expect(wrapper.text()).not.toContain('Shared finances')

      wrapper.unmount()
    })

    it('asks for confirmation before leaving and switches back to personal mode', async () => {
      const modeStore = useModeStore()
      modeStore.switchToHousehold('hh-1')
      mocks.leaveHousehold.mockResolvedValue(undefined)
      const { wrapper } = await mountSettings()

      await buttonByText(wrapper, 'Leave household').trigger('click')
      await flushPromises()
      expect(mocks.leaveHousehold).not.toHaveBeenCalled()
      expect(document.body.querySelector('[role="dialog"]')?.textContent).toContain(
        'Leave Morgan household?',
      )

      mocks.listHouseholds.mockResolvedValue([])
      bodyButton('Leave household').click()
      await flushPromises()

      expect(mocks.leaveHousehold).toHaveBeenCalledWith('hh-1')
      expect(modeStore.isHousehold).toBe(false)
      expect(wrapper.find('#household-name').exists()).toBe(true)

      wrapper.unmount()
    })

    it('does not leave when the confirmation is cancelled', async () => {
      const { wrapper } = await mountSettings()

      await buttonByText(wrapper, 'Leave household').trigger('click')
      await flushPromises()
      bodyButton('Cancel').click()
      await flushPromises()

      expect(mocks.leaveHousehold).not.toHaveBeenCalled()

      wrapper.unmount()
    })

    it('shows the leave error', async () => {
      mocks.leaveHousehold.mockRejectedValue(httpError('Owners must transfer ownership first'))
      const { wrapper } = await mountSettings()

      await buttonByText(wrapper, 'Leave household').trigger('click')
      await flushPromises()
      bodyButton('Leave household').click()
      await flushPromises()

      expect(wrapper.get('[role="alert"]').text()).toContain('Owners must transfer ownership first')

      wrapper.unmount()
    })

    it('creates a household inline and opens its settings', async () => {
      mocks.listHouseholds.mockResolvedValue([])
      mocks.createHousehold.mockImplementation(async ({ name }) => {
        mocks.listHouseholds.mockResolvedValue([household({ id: 'hh-2', name })])
        return { id: 'hh-2', name, rolloverDay: 1, createdAt: '', members: [] }
      })
      const { router, wrapper } = await mountSettings()

      const create = buttonByText(wrapper, 'Create Household')
      await wrapper.get('#household-name').setValue('ab')
      expect(create.attributes('disabled')).toBeDefined()

      await wrapper.get('#household-name').setValue('  Morgan home  ')
      await create.trigger('submit')
      await flushPromises()

      expect(mocks.createHousehold).toHaveBeenCalledWith({ name: 'Morgan home' })
      expect(useModeStore().householdId).toBe('hh-2')
      expect(router.currentRoute.value.fullPath).toBe('/household/hh-2/settings')

      wrapper.unmount()
    })

    it('shows the create error', async () => {
      mocks.listHouseholds.mockResolvedValue([])
      mocks.createHousehold.mockRejectedValue(httpError('Name already taken'))
      const { wrapper } = await mountSettings()

      await wrapper.get('#household-name').setValue('Morgan home')
      await wrapper.get<HTMLInputElement>('#household-name').element.form!.requestSubmit()
      await flushPromises()

      expect(wrapper.get('[role="alert"]').text()).toContain('Name already taken')

      wrapper.unmount()
    })

    it('lists pending invitations and accepts one', async () => {
      mocks.listHouseholds.mockResolvedValue([
        household({ id: 'hh-3', name: 'Taylor flat', status: 'INVITED', role: 'MEMBER' }),
      ])
      mocks.acceptInvite.mockResolvedValue({})
      const { router, wrapper } = await mountSettings()

      expect(wrapper.text()).toContain('Taylor flat')
      expect(wrapper.text()).toContain('Invited')
      await buttonByText(wrapper, 'Accept').trigger('click')
      await flushPromises()

      expect(mocks.acceptInvite).toHaveBeenCalledWith('hh-3')
      expect(router.currentRoute.value.fullPath).toBe('/household/hh-3/settings')

      wrapper.unmount()
    })

    it('blocks accepting an invitation while already in a household', async () => {
      mocks.listHouseholds.mockResolvedValue([
        household(),
        household({ id: 'hh-3', name: 'Taylor flat', status: 'INVITED', role: 'MEMBER' }),
      ])
      const { wrapper } = await mountSettings()

      expect(buttonByText(wrapper, 'Accept').attributes('disabled')).toBeDefined()

      wrapper.unmount()
    })
  })
})
