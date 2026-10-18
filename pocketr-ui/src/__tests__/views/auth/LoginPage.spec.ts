import { describe, expect, it, vi } from 'vitest'
import { flushPromises, type VueWrapper } from '@vue/test-utils'
import { api } from '@/api/http'
import { primeCsrfToken } from '@/api/csrf'
import LoginPage from '@/views/auth/LoginPage.vue'
import { buildUser } from '@/__tests__/support/fixtures'
import { mountRoute } from '@/__tests__/support/mount'

const Page = { render: () => null }

async function mountLoginAt(path = '/login') {
  vi.mocked(api.get).mockReturnValue({ json: vi.fn().mockResolvedValue(buildUser()) } as never)
  return mountRoute(LoginPage, {
    path,
    routes: ['/registration', '/transactions'].map((route) => ({ path: route, component: Page })),
  })
}

async function submitCredentials(wrapper: VueWrapper) {
  wrapper.get<HTMLInputElement>('#email').element.value = 'user@example.com'
  wrapper.get<HTMLInputElement>('#password').element.value = 'secret'
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

describe('LoginPage', () => {
  it('submits the current form values even when v-model has not synchronized them', async () => {
    vi.mocked(api.post).mockResolvedValue(undefined as never)
    const { wrapper } = await mountLoginAt()

    wrapper.get<HTMLInputElement>('#email').element.value = ' user@example.com '
    wrapper.get<HTMLInputElement>('#password').element.value = 'secret'
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const [url, request] = vi.mocked(api.post).mock.calls[0]!
    const body = (request as { body: URLSearchParams }).body
    expect(url).toBe('/api/v1/user/login')
    expect(body.get('email')).toBe('user@example.com')
    expect(body.get('password')).toBe('secret')
    expect(vi.mocked(primeCsrfToken).mock.invocationCallOrder[0]).toBeLessThan(
      vi.mocked(api.post).mock.invocationCallOrder[0]!,
    )
  })

  it('goes to the internal redirect target after signing in', async () => {
    vi.mocked(api.post).mockResolvedValue(undefined as never)
    const { wrapper, router } = await mountLoginAt('/login?redirect=%2Ftransactions%3Fpage%3D2')

    await submitCredentials(wrapper)

    expect(router.currentRoute.value.fullPath).toBe('/transactions?page=2')
  })

  it('falls back to the dashboard for an unsafe redirect', async () => {
    vi.mocked(api.post).mockResolvedValue(undefined as never)
    const { wrapper, router } = await mountLoginAt(
      `/login?redirect=${encodeURIComponent('https://evil.example')}`,
    )

    await submitCredentials(wrapper)

    expect(router.currentRoute.value.fullPath).toBe('/dashboard')
  })

  it('stays on the login page and clears the error on a new attempt when sign-in fails', async () => {
    vi.mocked(api.post).mockRejectedValue(new Error('401 Unauthorized'))
    const { wrapper, router } = await mountLoginAt('/login?redirect=%2Ftransactions')

    await submitCredentials(wrapper)

    expect(router.currentRoute.value.path).toBe('/login')
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)

    // A new attempt clears the previous error.
    vi.mocked(api.post).mockResolvedValue(undefined as never)
    await submitCredentials(wrapper)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })
})
