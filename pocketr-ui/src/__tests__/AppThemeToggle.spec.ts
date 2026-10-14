import { flushPromises, mount } from '@vue/test-utils'
import { h } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import UIcon from '@nuxt/ui/components/Icon.vue'
import AppThemeToggle from '@/layout/AppThemeToggle.vue'
import { i18n } from '@/i18n'
import { stubResizeObserver } from '@/__tests__/helpers/stubs'

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
  beforeEach(stubResizeObserver)

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('opens the shared theme picker and shows the chosen mode on the button', async () => {
    i18n.global.locale.value = 'en'
    const wrapper = mountToggle()
    const button = wrapper.get('button')

    expect(button.attributes('aria-label')).toBe('Appearance')
    expect(wrapper.getComponent(UIcon).props('name')).toBe('i-lucide-sun')

    await button.trigger('click')
    await flushPromises()
    const radio = (label: string) =>
      [...document.querySelectorAll<HTMLElement>('[role="radio"]')].find((item) =>
        item.closest('label')?.textContent?.includes(label),
      )
    expect(radio('Light')?.getAttribute('aria-checked')).toBe('true')

    radio('Dark')?.click()
    await flushPromises()
    expect(colorMode.value).toBe('dark')
    expect(radio('Dark')?.getAttribute('aria-checked')).toBe('true')
    expect(wrapper.getComponent(UIcon).props('name')).toBe('i-lucide-moon')

    radio('System')?.click()
    await flushPromises()
    expect(colorMode.value).toBe('auto')
    expect(wrapper.getComponent(UIcon).props('name')).toBe('i-lucide-monitor')

    wrapper.unmount()
  })
})
