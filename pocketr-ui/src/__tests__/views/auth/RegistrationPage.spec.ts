import { describe, expect, it, vi } from 'vitest'
import { flushPromises, type VueWrapper } from '@vue/test-utils'
import { api } from '@/api/http'
import RegistrationPage from '@/views/auth/RegistrationPage.vue'
import { mountRoute } from '@/__tests__/support/mount'

const Page = { render: () => null }

function mountRegistration() {
  return mountRoute(RegistrationPage, {
    path: '/registration',
    routes: [{ path: '/login', name: 'login', component: Page }],
  })
}

async function fillAndSubmit(wrapper: VueWrapper, overrides: Record<string, string> = {}) {
  const values: Record<string, string> = {
    'first-name': ' Alex ',
    'last-name': ' Morgan ',
    email: ' alex@example.com ',
    password: ' s3cret ',
    'confirm-password': ' s3cret ',
    ...overrides,
  }
  for (const [id, value] of Object.entries(values)) {
    await wrapper.get(`#${id}`).setValue(value)
  }
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

describe('RegistrationPage', () => {
  it('posts the trimmed profile and untouched password, then goes to sign in', async () => {
    vi.mocked(api.post).mockResolvedValue(undefined as never)
    const { wrapper, router } = await mountRegistration()

    await fillAndSubmit(wrapper)

    expect(api.post).toHaveBeenCalledExactlyOnceWith('/api/v1/user/register', {
      json: {
        password: ' s3cret ',
        email: 'alex@example.com',
        firstName: 'Alex',
        lastName: 'Morgan',
      },
    })
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('blocks mismatched passwords on the confirmation field without a request', async () => {
    const { wrapper, router } = await mountRegistration()

    await fillAndSubmit(wrapper, { 'confirm-password': 'different' })

    expect(api.post).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/registration')

    // Fixing the confirmation clears the error on the next attempt.
    vi.mocked(api.post).mockResolvedValue(undefined as never)
    await fillAndSubmit(wrapper)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(api.post).toHaveBeenCalledOnce()
  })

  it('stays on the page and clears the failure on a new attempt when registration fails', async () => {
    vi.mocked(api.post).mockRejectedValue(new Error('409 Conflict'))
    const { wrapper, router } = await mountRegistration()

    await fillAndSubmit(wrapper)

    expect(router.currentRoute.value.path).toBe('/registration')
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)

    // A new attempt clears the previous error.
    vi.mocked(api.post).mockResolvedValue(undefined as never)
    await fillAndSubmit(wrapper)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })
})
