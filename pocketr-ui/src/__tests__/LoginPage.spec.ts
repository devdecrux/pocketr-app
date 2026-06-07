import { createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import LoginPage from '@/views/auth/LoginPage.vue'

const apiMocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('@/api/csrf', () => ({
  primeCsrfToken: vi.fn(),
}))

vi.mock('@/api/http', () => ({
  api: apiMocks,
}))

const SlotStub = {
  template: '<div><slot /></div>',
}

describe('LoginPage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('submits the current form values even when v-model has not synchronized them', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/login', component: LoginPage },
        { path: '/dashboard', component: SlotStub },
        { path: '/registration', component: SlotStub },
      ],
    })
    await router.push('/login')
    await router.isReady()

    apiMocks.post.mockResolvedValue(undefined)
    apiMocks.get.mockReturnValue({
      json: vi.fn().mockResolvedValue({
        id: 1,
        email: 'user@example.com',
        firstName: 'Test',
        lastName: 'User',
        language: 'en',
        rolloverDay: 1,
      }),
    })

    const wrapper = mount(LoginPage, {
      global: {
        plugins: [createPinia(), router],
        mocks: {
          $t: (key: string) => key,
        },
        stubs: {
          AppFormField: SlotStub,
          AuthPageShell: SlotStub,
          Card: SlotStub,
          CardContent: SlotStub,
          CardDescription: SlotStub,
          CardHeader: SlotStub,
          CardTitle: SlotStub,
          RouterLink: SlotStub,
        },
      },
    })

    const emailInput = wrapper.get<HTMLInputElement>('#email').element
    const passwordInput = wrapper.get<HTMLInputElement>('#password').element
    emailInput.value = ' user@example.com '
    passwordInput.value = 'secret'

    await wrapper.get('form').trigger('submit')

    const request = apiMocks.post.mock.calls[0]?.[1]
    expect(request?.body.get('email')).toBe('user@example.com')
    expect(request?.body.get('password')).toBe('secret')
  })
})
