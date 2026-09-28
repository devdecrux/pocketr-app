import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import UDashboardGroup from '@nuxt/ui/components/DashboardGroup.vue'
import LostExplorer from '@/components/shared/LostExplorer.vue'
import { i18n, setLocale } from '@/i18n'
import appRouter from '@/router'
import { useAuthStore } from '@/stores/auth'
import NotFoundPage from '@/views/NotFoundPage.vue'

const { desktop } = vi.hoisted(() => ({ desktop: { value: false } }))

vi.mock('@vueuse/core', async () => {
  const actual = await vi.importActual<typeof import('@vueuse/core')>('@vueuse/core')
  const { ref } = await vi.importActual<typeof import('vue')>('vue')
  return {
    ...actual,
    useColorMode: () => ref('light'),
    useMediaQuery: () => ref(desktop.value),
  }
})

vi.mock('@/api/csrf', () => ({ primeCsrfToken: vi.fn() }))
vi.mock('@/api/http', () => ({ api: { post: vi.fn(), get: vi.fn() } }))
vi.mock('@/api/households', () => ({ listHouseholds: vi.fn(async () => []) }))

const Dashboard = { template: '<div data-test="dashboard">dashboard</div>' }

async function mountAt(path = '/somewhere/unknown') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/dashboard', component: Dashboard },
      { path: '/:pathMatch(.*)*', component: NotFoundPage },
    ],
  })
  await router.push(path)
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

describe('NotFoundPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    desktop.value = false
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        unobserve() {}
        disconnect() {}
      },
    )
    useAuthStore().setUser({
      id: 1,
      email: 'alex@example.com',
      firstName: 'Alex',
      lastName: 'Morgan',
      language: 'en',
      rolloverDay: 1,
    } as never)
  })

  afterEach(() => {
    setLocale('en')
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
  })

  it('shows the 404 code, message and explanation', async () => {
    const { wrapper } = await mountAt()

    expect(wrapper.text()).toContain('404')
    expect(wrapper.text()).toContain('This page wandered off.')
    expect(wrapper.text()).toContain('The page you’re looking for doesn’t exist or has moved.')

    wrapper.unmount()
  })

  it('uses the message as the mobile h1 and steps it down to h2 under the desktop header', async () => {
    const mobile = await mountAt()
    expect(mobile.wrapper.find('main h1').text()).toBe('This page wandered off.')
    expect(mobile.wrapper.find('main h2').exists()).toBe(false)
    mobile.wrapper.unmount()

    desktop.value = true
    const wide = await mountAt()
    expect(wide.wrapper.find('main h1').exists()).toBe(false)
    expect(wide.wrapper.find('main h2').text()).toBe('This page wandered off.')
    expect(wide.wrapper.find('header h1').text()).toBe('Page not found')
    wide.wrapper.unmount()
  })

  it('links back to the dashboard', async () => {
    const { router, wrapper } = await mountAt()

    const link = wrapper.get('main a')
    expect(link.text()).toBe('Back to dashboard')
    expect(link.attributes('href')).toBe('/dashboard')

    await link.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/dashboard')

    wrapper.unmount()
  })

  it('renders the Bulgarian copy', async () => {
    setLocale('bg')
    const { wrapper } = await mountAt()

    expect(wrapper.text()).toContain('Тази страница се е загубила.')
    expect(wrapper.get('main a').text()).toBe('Обратно към таблото')

    wrapper.unmount()
  })

  it('keeps the catch-all route behind sign-in on the Nuxt UI path', () => {
    const route = appRouter.getRoutes().find((record) => record.name === 'not-found')

    expect(route?.meta).toMatchObject({ requiresAuth: true, uiV2: true })
    expect(route?.meta.layout).toBeUndefined()
  })
})

describe('LostExplorer', () => {
  it('is decorative: hidden from assistive tech, not focusable and without text', () => {
    const wrapper = mount(LostExplorer)
    const svg = wrapper.get('svg')

    expect(svg.attributes('aria-hidden')).toBe('true')
    expect(svg.attributes('focusable')).toBe('false')
    expect(svg.text()).toBe('')
    expect(svg.findAll('a, button, [tabindex], text, title').length).toBe(0)
  })
})
