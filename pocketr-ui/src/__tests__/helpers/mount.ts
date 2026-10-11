import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { h } from 'vue'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import { i18n } from '@/i18n'

/**
 * Mounts `render` inside `UApp` with i18n and Nuxt UI. Pass `pinia: true` when the component needs
 * its own Pinia; otherwise stores come from the test's `setActivePinia`.
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
