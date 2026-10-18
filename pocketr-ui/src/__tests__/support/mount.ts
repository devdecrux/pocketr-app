import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { type Component, h } from 'vue'
import { createMemoryHistory, createRouter, type RouteRecordRaw, RouterView } from 'vue-router'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import UDashboardGroup from '@nuxt/ui/components/DashboardGroup.vue'
import { i18n } from '@/i18n'

/**
 * Mounts `render` inside `UApp` with i18n and Nuxt UI. Pass `pinia: true` when the component needs
 * its own Pinia; otherwise stores come from the global setup's active Pinia.
 */
export function mountInApp(render: () => ReturnType<typeof h>, options: { pinia?: boolean } = {}) {
  return mount(
    { render: () => h(UApp, null, { default: render }) },
    {
      attachTo: document.body,
      global: { plugins: options.pinia ? [i18n, ui, createPinia()] : [i18n, ui] },
    },
  )
}

/**
 * Mounts a routed page the way the app shell does (`UApp > UDashboardGroup > RouterView`) on a
 * memory router with the page route, a `/dashboard` placeholder and any `routes`. The page route
 * is registered at `pattern` (default: `path` without its query), e.g. `/household/:householdId/settings`.
 */
export async function mountRoute(
  component: Component,
  options: { path: string; pattern?: string; routes?: RouteRecordRaw[] },
) {
  const pagePath = options.pattern ?? options.path.split('?')[0]!
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: pagePath, component },
      { path: '/dashboard', name: 'dashboard', component: { render: () => null } },
      ...(options.routes ?? []).filter((route) => route.path !== pagePath),
    ],
  })
  await router.push(options.path)
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
  return { wrapper, router }
}
