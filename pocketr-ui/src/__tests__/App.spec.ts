import { createPinia } from 'pinia'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '../App.vue'
import { i18n, setLocale } from '@/i18n'

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
      {
        path: '/dashboard',
        component: DashboardScreen,
        meta: { requiresAuth: true, titleKey: 'views.dashboard.title' },
      },
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

    await setLocale('bg')
    await flushPromises()
    expect(provider().props('locale')).toBe('bg')

    await setLocale('de')
    await flushPromises()
    expect(provider().props('locale')).toBe('de')

    wrapper.unmount()
  })

  it('keeps <html lang> and the tab title in step with the locale and the route', async () => {
    const { router, wrapper } = await mountAppAt('/login')
    expect(document.documentElement.lang).toBe('en')
    expect(document.title).toBe('Pocketr')

    await router.push('/dashboard')
    await flushPromises()
    expect(document.title).toBe('Dashboard · Pocketr')

    await setLocale('de')
    await flushPromises()
    expect(document.documentElement.lang).toBe('de')
    expect(document.title).toBe(`${i18n.global.t('views.dashboard.title')} · Pocketr`)

    wrapper.unmount()
  })

  describe('while the first route is unresolved', () => {
    afterEach(() => vi.useRealTimers())

    function mountUnresolved() {
      const router = createRouter({
        history: createMemoryHistory(),
        routes: [
          { path: '/login', component: LoginScreen, meta: { layout: 'auth' } },
          { path: '/dashboard', component: DashboardScreen, meta: { requiresAuth: true } },
        ],
      })
      const wrapper = mount(App, {
        attachTo: document.body,
        global: {
          plugins: [createPinia(), router, i18n],
          stubs: { AppSidebar: { template: '<aside data-test="sidebar" />' } },
        },
      })
      return { router, wrapper }
    }

    it('shows a loading status only after a short delay, without the shell', async () => {
      vi.useFakeTimers()
      const { wrapper } = mountUnresolved()

      expect(wrapper.find('[role="status"]').exists()).toBe(false)

      await vi.advanceTimersByTimeAsync(199)
      expect(wrapper.find('[role="status"]').exists()).toBe(false)

      await vi.advanceTimersByTimeAsync(1)
      const status = wrapper.find('[role="status"]')
      expect(status.exists()).toBe(true)
      expect(status.text()).toBe(i18n.global.t('common.feedback.loading'))
      expect(wrapper.find('[data-test="sidebar"]').exists()).toBe(false)

      wrapper.unmount()
    })

    it('never appears when the route resolves within the delay, and goes once it resolves', async () => {
      vi.useFakeTimers()
      const { router, wrapper } = mountUnresolved()

      await router.push('/dashboard')
      await vi.advanceTimersByTimeAsync(500)
      expect(wrapper.find('[role="status"]').exists()).toBe(false)
      expect(wrapper.text()).toContain('Dashboard screen')

      wrapper.unmount()

      const late = mountUnresolved()
      await vi.advanceTimersByTimeAsync(300)
      expect(late.wrapper.find('[role="status"]').exists()).toBe(true)
      await late.router.push('/login')
      await vi.advanceTimersByTimeAsync(0)
      expect(late.wrapper.find('[role="status"]').exists()).toBe(false)
      expect(late.wrapper.text()).toContain('Login screen')

      late.wrapper.unmount()
    })
  })
})
