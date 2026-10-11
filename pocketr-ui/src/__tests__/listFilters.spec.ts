import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h, ref } from 'vue'
import AppFilterPanel from '@/components/page/AppFilterPanel.vue'
import AppFiltersToggle from '@/components/page/AppFiltersToggle.vue'
import AppPageHeading from '@/components/page/AppPageHeading.vue'
import { mountInApp } from '@/__tests__/helpers/mount'
import { viewport } from '@/__tests__/helpers/vueuse'

vi.mock('@vueuse/core', async () => (await import('@/__tests__/helpers/vueuse')).mockVueuse())

describe('list page filter components', () => {
  beforeEach(() => {
    viewport.desktop = true
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
      viewport.desktop = false
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
      viewport.desktop = false
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

    it("renders only the active breakpoint's title: h1 below lg, h2 from lg", () => {
      const mountHeading = () =>
        mountInApp(() =>
          h(
            AppPageHeading,
            { title: 'Things', subtitle: 'All the things.' },
            { actions: () => h('button', 'Act') },
          ),
        )

      const desktop = mountHeading()
      expect(desktop.find('h1').exists()).toBe(false)
      expect(desktop.get('h2').text()).toBe('Things')
      expect(desktop.text()).toContain('All the things.')
      expect(desktop.get('button').text()).toBe('Act')
      desktop.unmount()

      viewport.desktop = false
      const mobile = mountHeading()
      expect(mobile.get('h1').text()).toBe('Things')
      expect(mobile.find('h2').exists()).toBe(false)
      expect(mobile.findAll('p').map((p) => p.text())).toEqual(['All the things.'])
      mobile.unmount()
    })

    it('takes a separate desktop title and subtitle, a badge next to the title and no subtitle', () => {
      const mountHeading = () =>
        mountInApp(() =>
          h(
            AppPageHeading,
            {
              title: 'Settings',
              desktopTitle: 'Make it yours',
              subtitle: 'Short',
              desktopSubtitle: 'Long',
            },
            { badge: () => h('span', 'Owner') },
          ),
        )

      const desktop = mountHeading()
      expect(desktop.get('h2').text()).toBe('Make it yours')
      expect(desktop.findAll('p').map((p) => p.text())).toEqual(['Long'])
      expect(desktop.text()).toContain('Owner')
      desktop.unmount()

      viewport.desktop = false
      const mobile = mountHeading()
      expect(mobile.get('h1').text()).toBe('Settings')
      expect(mobile.findAll('p').map((p) => p.text())).toEqual(['Short'])
      expect(mobile.text()).toContain('Owner')
      mobile.unmount()

      const bare = mountInApp(() => h(AppPageHeading, { title: 'Things' }))
      expect(bare.find('p').exists()).toBe(false)
      bare.unmount()
    })

    it('shows a skeleton instead of the title while loading', () => {
      const wrapper = mountInApp(() => h(AppPageHeading, { title: 'Things', loading: true }))

      expect(wrapper.find('h1').exists()).toBe(false)
      expect(wrapper.find('h2').exists()).toBe(false)
      wrapper.unmount()
    })
  })
})
