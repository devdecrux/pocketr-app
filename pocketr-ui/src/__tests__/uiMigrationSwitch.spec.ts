import { createPinia } from 'pinia'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '../App.vue'
import UiV2Root from '@/components/layout/UiV2Root.vue'
import { i18n } from '@/i18n'
import appRouter from '@/router'
import { isUiV2Route, UI_MIGRATION_ATTRIBUTE } from '@/composables/useUiMigration'

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

const LegacyPage = { template: '<div>Legacy page</div>' }
const LoginScreen = { template: '<div>Login screen</div>' }
const MigratedPage = { template: '<div>Migrated page</div>' }

async function mountAppAt(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/legacy', component: LegacyPage },
      { path: '/login', component: LoginScreen, meta: { layout: 'auth' } },
      { path: '/migrated', component: MigratedPage, meta: { uiV2: true } },
      { path: '/migrated-auth', component: LoginScreen, meta: { uiV2: true, layout: 'auth' } },
    ],
  })

  await router.push(path)
  await router.isReady()

  const wrapper = mount(App, {
    attachTo: document.body,
    global: {
      plugins: [createPinia(), router, i18n],
      stubs: {
        Sidebar: { template: '<aside data-test="legacy-sidebar" />' },
        // The Nuxt UI sidebar has its own spec (AppSidebar.spec.ts).
        AppSidebar: { template: '<aside data-test="v2-sidebar" />' },
      },
    },
  })
  await flushPromises()

  return { router, wrapper }
}

describe('UI migration switch (temporary)', () => {
  afterEach(() => {
    document.body.innerHTML = ''
    document.body.removeAttribute(UI_MIGRATION_ATTRIBUTE)
  })

  it('reads only an explicit `meta.uiV2: true` flag', () => {
    expect(isUiV2Route({ meta: {} })).toBe(false)
    expect(isUiV2Route({ meta: { uiV2: false } })).toBe(false)
    expect(isUiV2Route({ meta: { uiV2: true } })).toBe(true)
  })

  it('flags only the sign-in, registration, dashboard, categories, settings and not-found routes (Phases 1-3, 6, 7, 9)', () => {
    const flagged = appRouter.getRoutes().filter((route) => isUiV2Route(route))

    expect(flagged.map((route) => route.path)).toEqual([
      '/login',
      '/registration',
      '/dashboard',
      '/categories',
      '/settings',
      '/:pathMatch(.*)*',
    ])
  })

  it('renders unflagged routes through the legacy shell', async () => {
    const { wrapper } = await mountAppAt('/legacy')

    expect(wrapper.text()).toContain('Legacy page')
    expect(wrapper.find('[data-test="legacy-sidebar"]').exists()).toBe(true)
    expect(wrapper.findComponent(UiV2Root).exists()).toBe(false)
    expect(document.body.hasAttribute(UI_MIGRATION_ATTRIBUTE)).toBe(false)

    wrapper.unmount()
  })

  it('keeps the legacy auth layout without the sidebar', async () => {
    const { wrapper } = await mountAppAt('/login')

    expect(wrapper.text()).toContain('Login screen')
    expect(wrapper.find('[data-test="legacy-sidebar"]').exists()).toBe(false)
    expect(wrapper.findComponent(UiV2Root).exists()).toBe(false)
    expect(document.body.hasAttribute(UI_MIGRATION_ATTRIBUTE)).toBe(false)

    wrapper.unmount()
  })

  it('renders flagged routes through the Nuxt UI path without the legacy shell', async () => {
    const { router, wrapper } = await mountAppAt('/migrated')

    const uiRoot = wrapper.findComponent(UiV2Root)
    expect(uiRoot.exists()).toBe(true)
    expect(uiRoot.text()).toContain('Migrated page')
    expect(uiRoot.find('[data-test="v2-sidebar"]').exists()).toBe(true)
    // `UApp` wraps the page in Reka UI's ConfigProvider.
    expect(uiRoot.findComponent({ name: 'ConfigProvider' }).exists()).toBe(true)
    expect(wrapper.find('[data-test="legacy-sidebar"]').exists()).toBe(false)
    expect(wrapper.find('.app-shell').exists()).toBe(false)
    expect(document.body.getAttribute(UI_MIGRATION_ATTRIBUTE)).toBe('v2')

    await router.push('/legacy')
    await flushPromises()

    expect(wrapper.findComponent(UiV2Root).exists()).toBe(false)
    expect(wrapper.find('[data-test="legacy-sidebar"]').exists()).toBe(true)
    expect(document.body.hasAttribute(UI_MIGRATION_ATTRIBUTE)).toBe(false)

    wrapper.unmount()
  })

  it('renders flagged auth-layout routes on the Nuxt UI path without the sidebar', async () => {
    const { wrapper } = await mountAppAt('/migrated-auth')

    const uiRoot = wrapper.findComponent(UiV2Root)
    expect(uiRoot.exists()).toBe(true)
    expect(uiRoot.text()).toContain('Login screen')
    expect(uiRoot.find('[data-test="v2-sidebar"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="legacy-sidebar"]').exists()).toBe(false)

    wrapper.unmount()
  })
})
