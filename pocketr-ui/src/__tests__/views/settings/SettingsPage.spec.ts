import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, type VueWrapper } from '@vue/test-utils'
import UInputNumber from '@nuxt/ui/components/InputNumber.vue'
import USelect from '@nuxt/ui/components/Select.vue'
import { api } from '@/api/http'
import { acceptInvite, createHousehold, leaveHousehold, listHouseholds } from '@/api/households'
import { updateUserLanguage, updateUserRolloverDay } from '@/api/user'
import { i18n, setLocale } from '@/i18n'
import { useAuthStore } from '@/stores/auth'
import { useModeStore } from '@/stores/mode'
import SettingsPage from '@/views/settings/SettingsPage.vue'
import { alertText, buttonIn, click, dialogByTitle, typeInto } from '@/__tests__/support/dom'
import {
  buildHousehold,
  buildHouseholdSummary,
  buildUser,
  httpError,
} from '@/__tests__/support/fixtures'
import { mountRoute } from '@/__tests__/support/mount'

const USER = buildUser()

function imageFile(name = 'me.png', type = 'image/png', size = 10): File {
  return new File([new Uint8Array(size)], name, { type })
}

function mountSettings() {
  return mountRoute(SettingsPage, {
    path: '/settings',
    routes: [
      {
        path: '/household/:householdId/settings',
        name: 'household-settings',
        component: { render: () => null },
      },
    ],
  })
}

function buttonByText(wrapper: VueWrapper, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text() === text)
  if (!button) throw new Error(`No button "${text}"`)
  return button
}

async function pickFile(wrapper: VueWrapper, file: File) {
  const input = wrapper.get<HTMLInputElement>('[data-test="avatar-file-input"]')
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
  await flushPromises()
}

const avatarSrc = (wrapper: VueWrapper) => wrapper.find('main img').attributes('src')

const FORM = 'form#settings-preferences'
const rolloverInput = (wrapper: VueWrapper) => wrapper.getComponent(UInputNumber)

describe('SettingsPage', () => {
  let objectUrlCount = 0
  const revokeObjectURL = vi.fn()

  // Load the lazy locales once so switching language in a test settles within a flush.
  beforeAll(async () => {
    await setLocale('bg')
    await setLocale('de')
    await setLocale('en')
  })

  beforeEach(async () => {
    objectUrlCount = 0
    revokeObjectURL.mockReset()
    Object.assign(URL, {
      createObjectURL: vi.fn(() => `blob:preview-${++objectUrlCount}`),
      revokeObjectURL,
    })
    vi.mocked(listHouseholds).mockResolvedValue([buildHouseholdSummary()])
    await useAuthStore().setUser({ ...USER })
  })

  describe('avatar', () => {
    it('previews a picked image without uploading, and discarding restores and revokes it', async () => {
      await useAuthStore().setUser({ ...USER, avatar: '/avatars/current.png' })
      const { wrapper } = await mountSettings()

      await pickFile(wrapper, imageFile())

      expect(avatarSrc(wrapper)).toBe('blob:preview-1')
      expect(api.post).not.toHaveBeenCalled()

      await buttonByText(wrapper, 'Cancel').trigger('click')
      await flushPromises()

      expect(avatarSrc(wrapper)).toBe('/avatars/current.png')
      expect(revokeObjectURL).toHaveBeenCalledWith('blob:preview-1')
    })

    it('rejects an unsupported type and a file over 5 MB before previewing', async () => {
      const { wrapper } = await mountSettings()

      for (const [file, message] of [
        [imageFile('notes.txt', 'text/plain'), 'Choose a JPG, PNG, GIF or WebP image.'],
        [imageFile('big.png', 'image/png', 5 * 1024 * 1024 + 1), 'larger than 5 MB'],
      ] as const) {
        await pickFile(wrapper, file)

        expect(alertText(wrapper.element)).toContain(message)
        expect(URL.createObjectURL).not.toHaveBeenCalled()
      }
    })

    it('uploads the confirmed image and applies the new avatar', async () => {
      const updated = { ...USER, avatar: '/avatars/new.png' }
      vi.mocked(api.post).mockReturnValue({ json: vi.fn(async () => updated) } as never)
      const { wrapper } = await mountSettings()
      const file = imageFile()

      await pickFile(wrapper, file)
      await buttonByText(wrapper, 'Save photo').trigger('click')
      await flushPromises()

      expect(api.post).toHaveBeenCalledOnce()
      const [url, options] = vi.mocked(api.post).mock.calls[0] as unknown as [
        string,
        { body: FormData },
      ]
      expect(url).toBe('/api/v1/user/avatar')
      expect(options.body.get('avatar')).toBe(file)
      expect(useAuthStore().user?.avatar).toBe('/avatars/new.png')
      expect(avatarSrc(wrapper)).toBe('/avatars/new.png')
      expect(revokeObjectURL).toHaveBeenCalledWith('blob:preview-1')
    })
  })

  it('keeps the state and shows the server message when the avatar upload, the preferences save or leaving the household fails', async () => {
    vi.mocked(api.post).mockReturnValue({
      json: vi.fn(async () => {
        throw httpError('Avatar file is too large. Maximum size is 5MB')
      }),
    } as never)
    vi.mocked(updateUserRolloverDay).mockRejectedValue(httpError('Rollover day rejected'))
    vi.mocked(leaveHousehold).mockRejectedValue(httpError('Owners must transfer ownership first'))

    const upload = (await mountSettings()).wrapper
    await pickFile(upload, imageFile())
    await buttonByText(upload, 'Save photo').trigger('click')
    await flushPromises()
    expect(alertText(upload.element)).toContain('Maximum size is 5MB')
    expect(avatarSrc(upload)).toBe('blob:preview-1')
    expect(revokeObjectURL).not.toHaveBeenCalled()
    upload.unmount()

    const preferences = (await mountSettings()).wrapper
    rolloverInput(preferences).vm.$emit('update:modelValue', null)
    await flushPromises()
    await preferences.get(FORM).trigger('submit')
    await flushPromises()
    expect(updateUserRolloverDay).not.toHaveBeenCalled()
    expect(preferences.text()).toContain('Rollover day must be between 1 and 31.')

    rolloverInput(preferences).vm.$emit('update:modelValue', 3)
    await flushPromises()
    await preferences.get(FORM).trigger('submit')
    await flushPromises()
    const alert = preferences.get(`${FORM} [role="alert"]`)
    expect(alert.text()).toContain('Rollover day rejected')
    expect(useAuthStore().user?.rolloverDay).toBe(1)
    preferences.unmount()

    const leave = (await mountSettings()).wrapper
    await click(buttonByText(leave, 'Leave household').element)
    await click(buttonIn(dialogByTitle('Leave Morgan family?'), 'Leave household'))
    expect(leaveHousehold).toHaveBeenCalledExactlyOnceWith('hh-1')
    expect(alertText(leave.element)).toContain('Owners must transfer ownership first')
    expect(leave.find('#household-name').exists()).toBe(false)
  })

  describe('preferences', () => {
    /** The language select; the view-mode select in the header is excluded. */
    const languageSelect = (wrapper: VueWrapper) =>
      wrapper
        .findAllComponents(USelect)
        .find((select) => (select.element as Element).closest(FORM))!
    const saveButton = (wrapper: VueWrapper) => wrapper.get(`${FORM} button[type="submit"]`)

    beforeEach(() => {
      vi.mocked(updateUserLanguage).mockImplementation(async (language) => ({
        ...USER,
        language,
      }))
      vi.mocked(updateUserRolloverDay).mockImplementation(async (rolloverDay) => ({
        ...USER,
        rolloverDay,
      }))
    })

    it('saves only the changed preferences through their own endpoints', async () => {
      const { wrapper } = await mountSettings()

      languageSelect(wrapper).vm.$emit('update:modelValue', 'bg')
      await flushPromises()
      await saveButton(wrapper).trigger('submit')
      await flushPromises()

      expect(updateUserLanguage).toHaveBeenCalledWith('bg')
      expect(updateUserRolloverDay).not.toHaveBeenCalled()
      expect(i18n.global.locale.value).toBe('bg')
    })

    it('saves language and rollover day together', async () => {
      vi.mocked(updateUserRolloverDay).mockImplementation(async (rolloverDay) => ({
        ...USER,
        language: 'de',
        rolloverDay,
      }))
      const { wrapper } = await mountSettings()

      languageSelect(wrapper).vm.$emit('update:modelValue', 'de')
      rolloverInput(wrapper).vm.$emit('update:modelValue', 15)
      await flushPromises()
      await wrapper.get(FORM).trigger('submit')
      await flushPromises()

      expect(updateUserLanguage).toHaveBeenCalledWith('de')
      expect(updateUserRolloverDay).toHaveBeenCalledWith(15)
      expect(useAuthStore().user).toMatchObject({ language: 'de', rolloverDay: 15 })
      expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
    })
  })

  describe('household', () => {
    it('links the membership to its household settings', async () => {
      const { wrapper } = await mountSettings()

      const manage = wrapper.findAll('a').find((link) => link.text() === 'Manage household')
      expect(manage?.attributes('href')).toBe('/household/hh-1/settings')
      expect(wrapper.find('#household-name').exists()).toBe(false)
    })

    it('asks for confirmation before leaving and switches back to personal mode', async () => {
      const modeStore = useModeStore()
      modeStore.switchToHousehold('hh-1')
      const { wrapper } = await mountSettings()

      await click(buttonByText(wrapper, 'Leave household').element)
      expect(leaveHousehold).not.toHaveBeenCalled()
      const dialog = dialogByTitle('Leave Morgan family?')

      vi.mocked(listHouseholds).mockResolvedValue([])
      await click(buttonIn(dialog, 'Leave household'))

      expect(leaveHousehold).toHaveBeenCalledExactlyOnceWith('hh-1')
      expect(modeStore.isHousehold).toBe(false)
      expect(wrapper.find('#household-name').exists()).toBe(true)
    })

    describe('without a household', () => {
      beforeEach(() => {
        vi.mocked(listHouseholds).mockResolvedValue([])
      })

      it('creates a household inline and opens its settings', async () => {
        vi.mocked(createHousehold).mockImplementation(async ({ name }) => {
          vi.mocked(listHouseholds).mockResolvedValue([buildHouseholdSummary({ id: 'hh-2', name })])
          return buildHousehold({ id: 'hh-2', name })
        })
        const { wrapper, router } = await mountSettings()
        const create = buttonByText(wrapper, 'Create household')

        await typeInto(wrapper.get<HTMLInputElement>('#household-name').element, 'ab')
        expect(create.attributes('disabled')).toBeDefined()

        await typeInto(wrapper.get<HTMLInputElement>('#household-name').element, '  Morgan home  ')
        await create.trigger('submit')
        await flushPromises()

        expect(createHousehold).toHaveBeenCalledExactlyOnceWith({ name: 'Morgan home' })
        expect(useModeStore().householdId).toBe('hh-2')
        expect(router.currentRoute.value.fullPath).toBe('/household/hh-2/settings')
      })
    })

    describe('invitations', () => {
      const INVITATION = buildHouseholdSummary({
        id: 'hh-3',
        name: 'Taylor flat',
        status: 'INVITED',
        role: 'MEMBER',
      })

      it('accepts a pending invitation and opens its household', async () => {
        vi.mocked(listHouseholds).mockResolvedValue([INVITATION])
        const { wrapper, router } = await mountSettings()

        await buttonByText(wrapper, 'Accept').trigger('click')
        await flushPromises()

        expect(acceptInvite).toHaveBeenCalledExactlyOnceWith('hh-3')
        expect(router.currentRoute.value.fullPath).toBe('/household/hh-3/settings')
      })

      it('blocks accepting an invitation while already in a household', async () => {
        vi.mocked(listHouseholds).mockResolvedValue([buildHouseholdSummary(), INVITATION])
        const { wrapper } = await mountSettings()

        expect(buttonByText(wrapper, 'Accept').attributes('disabled')).toBeDefined()
      })
    })
  })
})
