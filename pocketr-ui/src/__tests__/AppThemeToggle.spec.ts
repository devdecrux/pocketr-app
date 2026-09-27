import { mount } from '@vue/test-utils'
import { h } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import UIcon from '@nuxt/ui/components/Icon.vue'
import AppThemeToggle from '@/components/layout/AppThemeToggle.vue'
import { i18n } from '@/i18n'

const { colorMode } = vi.hoisted(() => ({
  colorMode: { value: 'light' as string },
}))

vi.mock('@vueuse/core', async () => {
  const actual = await vi.importActual<typeof import('@vueuse/core')>('@vueuse/core')
  const { ref } = await vi.importActual<typeof import('vue')>('vue')
  // Like VueUse: `value` resolves "auto" to the system theme (light here); `store` keeps the choice.
  const store = ref(colorMode.value)
  return {
    ...actual,
    useColorMode: () => ({
      store,
      get value() {
        return store.value === 'auto' ? 'light' : store.value
      },
      set value(next: string) {
        colorMode.value = next
        store.value = next
      },
    }),
  }
})

function mountToggle() {
  return mount(() => h(UApp, null, () => h(AppThemeToggle)), {
    attachTo: document.body,
    global: { plugins: [i18n, ui] },
  })
}

describe('AppThemeToggle', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('cycles light → dark → system → light and names the current mode and next action', async () => {
    i18n.global.locale.value = 'en'
    const wrapper = mountToggle()
    const button = wrapper.get('button')

    const steps = [
      ['light', 'Theme: Light. Switch to Dark', 'i-lucide-sun'],
      ['dark', 'Theme: Dark. Switch to System', 'i-lucide-moon'],
      ['auto', 'Theme: System. Switch to Light', 'i-lucide-monitor'],
      ['light', 'Theme: Light. Switch to Dark', 'i-lucide-sun'],
    ] as const

    for (const [index, [mode, label, icon]] of steps.entries()) {
      expect(colorMode.value).toBe(mode)
      expect(button.attributes('aria-label')).toBe(label)
      expect(wrapper.getComponent(UIcon).props('name')).toBe(icon)
      if (index < steps.length - 1) await button.trigger('click')
    }

    wrapper.unmount()
  })
})
