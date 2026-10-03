import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { h, ref } from 'vue'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import { i18n } from '@/i18n'
import AppFilterPanel from '@/components/shared/AppFilterPanel.vue'
import AppFiltersToggle from '@/components/shared/AppFiltersToggle.vue'
import AppPageHeading from '@/components/shared/AppPageHeading.vue'

const mocks = vi.hoisted(() => ({ desktop: { value: true } }))

vi.mock('@vueuse/core', async () => {
  const actual = await vi.importActual<typeof import('@vueuse/core')>('@vueuse/core')
  const { ref } = await vi.importActual<typeof import('vue')>('vue')
  return { ...actual, useMediaQuery: () => ref(mocks.desktop.value) }
})

function mountInApp(content: () => ReturnType<typeof h>) {
  return mount(
    { render: () => h(UApp, null, { default: content }) },
    { attachTo: document.body, global: { plugins: [i18n, ui] } },
  )
}

describe('list page filter components', () => {
  beforeEach(() => {
    mocks.desktop.value = true
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  describe('AppFiltersToggle', () => {
    function mountToggle(count: number) {
      const open = ref(false)
      const wrapper = mountInApp(() =>
        h(AppFiltersToggle, {
          open: open.value,
          'onUpdate:open': (value: boolean) => (open.value = value),
          panelId: 'panel',
          count,
        }),
      )
      return { wrapper, open }
    }

    it('is a labelled button on desktop that controls the panel and toggles it', async () => {
      const { wrapper, open } = mountToggle(0)
      const button = wrapper.get('button')

      expect(button.text()).toBe('Filters')
      expect(button.attributes('aria-expanded')).toBe('false')
      expect(button.attributes('aria-controls')).toBe('panel')

      await button.trigger('click')
      expect(open.value).toBe(true)

      wrapper.unmount()
    })

    it('is icon-only with an accessible name below lg', () => {
      mocks.desktop.value = false
      const { wrapper } = mountToggle(0)
      const button = wrapper.get('button')

      expect(button.text()).toBe('')
      expect(button.attributes('aria-label')).toBe('Filters')

      wrapper.unmount()
    })

    it('shows the number of filters in use only when there are some', () => {
      const none = mountToggle(0)
      expect(none.wrapper.get('button').text()).toBe('Filters')
      none.wrapper.unmount()

      const some = mountToggle(3)
      expect(some.wrapper.get('button').text()).toContain('3')
      some.wrapper.unmount()
    })
  })

  describe('AppFilterPanel', () => {
    function mountPanel(count: number) {
      const cleared = vi.fn()
      const wrapper = mountInApp(() =>
        h(
          AppFilterPanel,
          { id: 'panel', count, onClear: cleared },
          { default: () => h('input', { 'aria-label': 'Control' }) },
        ),
      )
      return { wrapper, cleared }
    }

    it('is a named group holding the page controls', () => {
      const { wrapper } = mountPanel(0)

      const group = wrapper.get('#panel')
      expect(group.attributes('role')).toBe('group')
      expect(group.attributes('aria-label')).toBe('Filters')
      expect(group.find('input').exists()).toBe(true)
      expect(group.classes()).toEqual(
        expect.arrayContaining(['grid', 'gap-2.5', 'lg:flex', 'lg:flex-wrap', 'lg:items-center']),
      )
      expect(group.classes()).not.toContain('lg:justify-end')
      expect(wrapper.find('button').exists()).toBe(false)

      wrapper.unmount()
    })

    it('offers Clear while a filter is in use and reports it', async () => {
      const { wrapper, cleared } = mountPanel(2)
      const clear = wrapper.get('button[aria-label="Clear filters"]')

      expect(clear.text()).toBe('')
      await clear.trigger('click')
      expect(cleared).toHaveBeenCalledTimes(1)

      wrapper.unmount()
    })

    it('labels Clear below lg', () => {
      mocks.desktop.value = false
      const { wrapper } = mountPanel(1)

      expect(wrapper.get('button').text()).toBe('Clear filters')

      wrapper.unmount()
    })
  })

  describe('AppPageHeading', () => {
    it('has no right-column cell: the panel is its own row under the heading', () => {
      const wrapper = mountInApp(() =>
        h(AppPageHeading, { title: 'Things', subtitle: 'All the things.' }),
      )

      expect(wrapper.find('.lg\\:justify-self-end').exists()).toBe(false)
      expect(wrapper.find('.lg\\:col-start-2.lg\\:row-start-2').exists()).toBe(false)

      wrapper.unmount()
    })

    it('renders the title as h1 and h2, the subtitle and the actions', () => {
      const wrapper = mountInApp(() =>
        h(
          AppPageHeading,
          { title: 'Things', subtitle: 'All the things.' },
          { actions: () => h('button', 'Act') },
        ),
      )

      expect(wrapper.get('h1').text()).toBe('Things')
      expect(wrapper.get('h2').text()).toBe('Things')
      expect(wrapper.text()).toContain('All the things.')
      expect(wrapper.get('button').text()).toBe('Act')

      wrapper.unmount()
    })
  })
})
