import { createPinia } from 'pinia'
import { h } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import { i18n } from '@/i18n'
import LoginPage from '@/views/auth/LoginPage.vue'

const { apiMocks, primeCsrfToken } = vi.hoisted(() => ({
  apiMocks: {
    get: vi.fn(),
    post: vi.fn(),
  },
  primeCsrfToken: vi.fn(),
}))

vi.mock('@/api/csrf', () => ({
  primeCsrfToken,
}))

vi.mock('@/api/http', () => ({
  api: apiMocks,
}))

const SlotStub = {
  template: '<div><slot /></div>',
}

const authUser = {
  id: 1,
  email: 'user@example.com',
  firstName: 'Test',
  lastName: 'User',
  language: 'en',
  rolloverDay: 1,
}

async function mountLoginAt(path = '/login') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', component: LoginPage },
      { path: '/dashboard', component: SlotStub },
      { path: '/registration', component: SlotStub },
      { path: '/transactions', component: SlotStub },
    ],
  })
  await router.push(path)
  await router.isReady()

  // `UApp` provides the tooltip context the header theme toggle needs, as in the app.
  const wrapper = mount(() => h(UApp, null, () => h(LoginPage)), {
    attachTo: document.body,
    global: {
      plugins: [createPinia(), router, i18n, ui],
    },
  })

  return { router, wrapper }
}

function fillAndSubmit(wrapper: Awaited<ReturnType<typeof mountLoginAt>>['wrapper']) {
  wrapper.get<HTMLInputElement>('#email').element.value = 'user@example.com'
  wrapper.get<HTMLInputElement>('#password').element.value = 'secret'
  return wrapper.get('form').trigger('submit')
}

describe('LoginPage', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
    apiMocks.get.mockReturnValue({ json: vi.fn().mockResolvedValue(authUser) })
  })

  afterEach(() => {
    vi.resetAllMocks()
    document.body.innerHTML = ''
  })

  it('submits the current form values even when v-model has not synchronized them', async () => {
    apiMocks.post.mockResolvedValue(undefined)
    const { wrapper } = await mountLoginAt()

    wrapper.get<HTMLInputElement>('#email').element.value = ' user@example.com '
    wrapper.get<HTMLInputElement>('#password').element.value = 'secret'
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const request = apiMocks.post.mock.calls[0]?.[1]
    expect(apiMocks.post.mock.calls[0]?.[0]).toBe('/api/v1/user/login')
    expect(request?.body.get('email')).toBe('user@example.com')
    expect(request?.body.get('password')).toBe('secret')
    expect(primeCsrfToken.mock.invocationCallOrder[0]).toBeLessThan(
      apiMocks.post.mock.invocationCallOrder[0]!,
    )
    wrapper.unmount()
  })

  it('renders labelled fields with autocomplete hints and the registration link', async () => {
    const { wrapper } = await mountLoginAt()

    expect(wrapper.get('h1').text()).toBe('Welcome back!')
    expect(wrapper.text().split('Simple on the surface. Solid underneath.')).toHaveLength(2)
    expect(wrapper.get('label[for="email"]').text()).toBe('Email')
    expect(wrapper.get('label[for="password"]').text()).toBe('Password')

    const email = wrapper.get('#email')
    expect(email.attributes('type')).toBe('email')
    expect(email.attributes('autocomplete')).toBe('username')
    expect(email.attributes('required')).toBeDefined()
    const password = wrapper.get('#password')
    expect(password.attributes('type')).toBe('password')
    expect(password.attributes('autocomplete')).toBe('current-password')
    expect(password.attributes('required')).toBeDefined()

    expect(wrapper.get('button[type="submit"]').text()).toBe('Sign in')
    expect(wrapper.get('a[href="/registration"]').text()).toBe('Create an account')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('redirects to the sanitized internal redirect target after signing in', async () => {
    apiMocks.post.mockResolvedValue(undefined)
    const { router, wrapper } = await mountLoginAt('/login?redirect=%2Ftransactions%3Fpage%3D2')

    await fillAndSubmit(wrapper)
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/transactions?page=2')
    wrapper.unmount()
  })

  it.each(['//evil.example', 'https://evil.example', 'javascript:alert(1)'])(
    'falls back to the dashboard for the unsafe redirect %s',
    async (redirect) => {
      apiMocks.post.mockResolvedValue(undefined)
      const { router, wrapper } = await mountLoginAt(
        `/login?redirect=${encodeURIComponent(redirect)}`,
      )

      await fillAndSubmit(wrapper)
      await flushPromises()

      expect(router.currentRoute.value.fullPath).toBe('/dashboard')
      wrapper.unmount()
    },
  )

  it('shows the loading state while the request is pending', async () => {
    let resolveLogin: (value?: unknown) => void = () => {}
    apiMocks.post.mockReturnValue(new Promise((resolve) => (resolveLogin = resolve)))
    const { wrapper } = await mountLoginAt()

    await fillAndSubmit(wrapper)
    await flushPromises()

    const submit = wrapper.get('button[type="submit"]')
    expect(submit.attributes('disabled')).toBeDefined()
    expect(submit.text()).toBe('Signing in...')

    resolveLogin()
    await flushPromises()
    wrapper.unmount()
  })

  it.each([
    ['invalid credentials', () => Promise.reject(new Error('401 Unauthorized'))],
    ['a network error', () => Promise.reject(new TypeError('Failed to fetch'))],
  ])('announces the error and marks both fields invalid on %s', async (_label, failure) => {
    apiMocks.post.mockImplementation(failure)
    const { router, wrapper } = await mountLoginAt('/login?redirect=%2Ftransactions')

    await fillAndSubmit(wrapper)
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/login')
    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toBe('Invalid email or password. Please try again.')

    for (const id of ['email', 'password']) {
      const input = wrapper.get(`#${id}`)
      expect(input.attributes('aria-invalid')).toBe('true')
      const describedBy = input.attributes('aria-describedby') ?? ''
      expect(describedBy).not.toBe('')
      expect(document.getElementById(describedBy)?.textContent).toContain(alert.text())
    }

    const submit = wrapper.get('button[type="submit"]')
    expect(submit.attributes('disabled')).toBeUndefined()
    expect(submit.text()).toBe('Sign in')

    // A new attempt clears the previous error.
    apiMocks.post.mockResolvedValue(undefined)
    await fillAndSubmit(wrapper)
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows the session-expired notice', async () => {
    const { wrapper } = await mountLoginAt('/login?reason=session-expired')

    expect(wrapper.text()).toContain('Your session has expired. Please log in again.')
    wrapper.unmount()
  })
})
