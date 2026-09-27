import { createPinia } from 'pinia'
import { h } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import { i18n } from '@/i18n'
import RegistrationPage from '@/views/auth/RegistrationPage.vue'

const { apiMocks } = vi.hoisted(() => ({
  apiMocks: {
    post: vi.fn(),
  },
}))

vi.mock('@/api/http', () => ({
  api: apiMocks,
}))

const SlotStub = {
  template: '<div><slot /></div>',
}

async function mountRegistration() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/registration', component: RegistrationPage },
      { path: '/login', component: SlotStub },
      { path: '/dashboard', component: SlotStub },
    ],
  })
  await router.push('/registration')
  await router.isReady()

  // `UApp` provides the tooltip context the header theme toggle needs, as in the app.
  const wrapper = mount(() => h(UApp, null, () => h(RegistrationPage)), {
    attachTo: document.body,
    global: {
      plugins: [createPinia(), router, i18n, ui],
    },
  })

  return { router, wrapper }
}

type Wrapper = Awaited<ReturnType<typeof mountRegistration>>['wrapper']

async function fillAndSubmit(wrapper: Wrapper, overrides: Record<string, string> = {}) {
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
}

describe('RegistrationPage', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  afterEach(() => {
    vi.resetAllMocks()
    document.body.innerHTML = ''
  })

  it('renders labelled, required fields with autocomplete hints and the sign-in link', async () => {
    const { wrapper } = await mountRegistration()

    expect(wrapper.get('h1').text()).toBe('Create your account')
    expect(wrapper.text().split('Simple on the surface. Solid underneath.')).toHaveLength(2)

    const fields = [
      ['first-name', 'First name', 'text', 'given-name'],
      ['last-name', 'Last name', 'text', 'family-name'],
      ['email', 'Email', 'email', 'username'],
      ['password', 'Password', 'password', 'new-password'],
      ['confirm-password', 'Confirm password', 'password', 'new-password'],
    ]
    for (const [id, label, type, autocomplete] of fields) {
      expect(wrapper.get(`label[for="${id}"]`).text()).toBe(label)
      const input = wrapper.get(`#${id}`)
      expect(input.attributes('type')).toBe(type)
      expect(input.attributes('autocomplete')).toBe(autocomplete)
      expect(input.attributes('required')).toBeDefined()
    }

    expect(wrapper.get('button[type="submit"]').text()).toBe('Create account')
    expect(wrapper.get('a[href="/login"]').text()).toBe('Sign in')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('posts the trimmed profile and untouched password, then goes to sign in', async () => {
    apiMocks.post.mockResolvedValue(undefined)
    const { router, wrapper } = await mountRegistration()

    await fillAndSubmit(wrapper)
    await flushPromises()

    expect(apiMocks.post).toHaveBeenCalledExactlyOnceWith('/api/v1/user/register', {
      json: {
        password: ' s3cret ',
        email: 'alex@example.com',
        firstName: 'Alex',
        lastName: 'Morgan',
      },
    })
    expect(router.currentRoute.value.path).toBe('/login')
    wrapper.unmount()
  })

  it('toggles each password field independently and hides both after a successful submit', async () => {
    apiMocks.post.mockResolvedValue(undefined)
    const { wrapper } = await mountRegistration()
    const toggle = (id: string) => wrapper.get(`button[aria-controls="${id}"]`)

    await toggle('password').trigger('click')
    expect(wrapper.get('#password').attributes('type')).toBe('text')
    expect(wrapper.get('#confirm-password').attributes('type')).toBe('password')
    await toggle('confirm-password').trigger('click')
    expect(wrapper.get('#confirm-password').attributes('type')).toBe('text')
    expect(toggle('password').attributes('aria-pressed')).toBe('true')
    expect(apiMocks.post).not.toHaveBeenCalled()

    await fillAndSubmit(wrapper)
    await flushPromises()

    expect(apiMocks.post).toHaveBeenCalledOnce()
    expect(wrapper.get('#password').attributes('type')).toBe('password')
    expect(wrapper.get('#confirm-password').attributes('type')).toBe('password')
    wrapper.unmount()
  })

  it('blocks mismatched passwords on the confirmation field without a request', async () => {
    const { router, wrapper } = await mountRegistration()

    await fillAndSubmit(wrapper, { 'confirm-password': 'different' })
    await flushPromises()

    expect(apiMocks.post).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/registration')
    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toBe('Passwords do not match')

    const confirm = wrapper.get('#confirm-password')
    expect(confirm.attributes('aria-invalid')).toBe('true')
    const describedBy = confirm.attributes('aria-describedby') ?? ''
    expect(document.getElementById(describedBy)?.textContent).toContain(alert.text())
    expect(document.activeElement).toBe(confirm.element)
    expect(wrapper.get('#password').attributes('aria-invalid')).not.toBe('true')

    // Fixing the confirmation clears the error on the next attempt.
    apiMocks.post.mockResolvedValue(undefined)
    await fillAndSubmit(wrapper)
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(apiMocks.post).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('shows the loading state while the request is pending', async () => {
    let resolveRegistration: (value?: unknown) => void = () => {}
    apiMocks.post.mockReturnValue(new Promise((resolve) => (resolveRegistration = resolve)))
    const { wrapper } = await mountRegistration()

    await fillAndSubmit(wrapper)
    await flushPromises()

    const submit = wrapper.get('button[type="submit"]')
    expect(submit.attributes('disabled')).toBeDefined()
    expect(submit.text()).toBe('Creating account...')

    resolveRegistration()
    await flushPromises()
    wrapper.unmount()
  })

  it.each([
    ['an existing account', () => Promise.reject(new Error('409 Conflict'))],
    ['a network error', () => Promise.reject(new TypeError('Failed to fetch'))],
  ])('announces the failure and stays on the page on %s', async (_label, failure) => {
    apiMocks.post.mockImplementation(failure)
    const { router, wrapper } = await mountRegistration()

    await fillAndSubmit(wrapper)
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/registration')
    expect(wrapper.get('[role="alert"]').text()).toBe('Unable to register user')
    const submit = wrapper.get('button[type="submit"]')
    expect(submit.attributes('disabled')).toBeUndefined()
    expect(submit.text()).toBe('Create account')
    expect(document.activeElement).toBe(wrapper.get('#email').element)

    // A new attempt clears the previous error.
    apiMocks.post.mockResolvedValue(undefined)
    await fillAndSubmit(wrapper)
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
