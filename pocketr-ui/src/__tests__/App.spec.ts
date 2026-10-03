import { createPinia } from 'pinia'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '../App.vue'
import { i18n } from '@/i18n'

vi.mock('@/api/csrf', () => ({
  primeCsrfToken: vi.fn(),
}))

vi.mock('@vueuse/core', async () => {
  const actual = await vi.importActual<typeof import('@vueuse/core')>('@vueuse/core')
  return {
    ...actual,
    useColorMode: () => ({
      value: 'light',
    }),
  }
})

const LoginScreen = { template: '<div>Login screen</div>' }
const DashboardScreen = { template: '<div>Dashboard screen</div>' }

async function mountAppAt(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', component: LoginScreen, meta: { layout: 'auth' } },
      { path: '/dashboard', component: DashboardScreen, meta: { requiresAuth: true } },
    ],
  })

  await router.push(path)
  await router.isReady()

  const wrapper = mount(App, {
    attachTo: document.body,
    global: {
      plugins: [createPinia(), router, i18n],
      stubs: {
        // The sidebar has its own spec (AppSidebar.spec.ts).
        AppSidebar: { template: '<aside data-test="sidebar" />' },
      },
    },
  })
  await flushPromises()

  return { router, wrapper }
}

describe('App', () => {
  afterEach(() => {
    i18n.global.locale.value = 'en'
    document.body.innerHTML = ''
  })

  it('renders auth-layout routes without the sidebar', async () => {
    const { wrapper } = await mountAppAt('/login')

    expect(wrapper.text()).toContain('Login screen')
    expect(wrapper.find('[data-test="sidebar"]').exists()).toBe(false)

    wrapper.unmount()
  })

  it('renders every other route inside the dashboard shell with the sidebar', async () => {
    const { router, wrapper } = await mountAppAt('/dashboard')

    expect(wrapper.text()).toContain('Dashboard screen')
    expect(wrapper.find('[data-test="sidebar"]').exists()).toBe(true)

    await router.push('/login')
    await flushPromises()

    expect(wrapper.text()).toContain('Login screen')
    expect(wrapper.find('[data-test="sidebar"]').exists()).toBe(false)

    wrapper.unmount()
  })

  it('wraps the app in Nuxt UI `UApp` and follows the vue-i18n locale', async () => {
    const { wrapper } = await mountAppAt('/login')
    const provider = () => wrapper.findComponent({ name: 'ConfigProvider' })

    expect(provider().exists()).toBe(true)
    expect(provider().props('locale')).toBe('en')

    i18n.global.locale.value = 'bg'
    await flushPromises()
    expect(provider().props('locale')).toBe('bg')

    i18n.global.locale.value = 'de'
    await flushPromises()
    expect(provider().props('locale')).toBe('de')

    wrapper.unmount()
  })
})
