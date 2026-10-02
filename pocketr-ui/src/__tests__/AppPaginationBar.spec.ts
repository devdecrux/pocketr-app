import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { h } from 'vue'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import USelect from '@nuxt/ui/components/Select.vue'
import { i18n } from '@/i18n'
import AppPaginationBar from '@/components/shared/AppPaginationBar.vue'
import type { AppTablePagination } from '@/types/dataTable'

function mountBar(pagination: AppTablePagination) {
  const events: { name: string; args: unknown[] }[] = []
  const wrapper = mount(
    {
      render: () =>
        h(UApp, null, {
          default: () =>
            h(AppPaginationBar, {
              pagination,
              'onUpdate:page': (...args: unknown[]) => events.push({ name: 'page', args }),
              'onUpdate:pageSize': (...args: unknown[]) => events.push({ name: 'size', args }),
            }),
        }),
    },
    { attachTo: document.body, global: { plugins: [i18n, ui] } },
  )
  return { wrapper, events }
}

const nextButton = (wrapper: ReturnType<typeof mountBar>['wrapper']) =>
  wrapper.find('button[aria-label="Next page"]')
const previousButton = (wrapper: ReturnType<typeof mountBar>['wrapper']) =>
  wrapper.find('button[aria-label="Previous page"]')

describe('AppPaginationBar', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('shows the visible range and disables both arrows on a single page', () => {
    const { wrapper } = mountBar({ page: 0, pageSize: 15, totalPages: 1, totalElements: 6 })

    expect(wrapper.text()).toContain('1–6 of 6')
    expect(previousButton(wrapper).attributes('disabled')).toBeDefined()
    expect(nextButton(wrapper).attributes('disabled')).toBeDefined()

    wrapper.unmount()
  })

  it('clamps the range end on the last page and navigates back', async () => {
    const { wrapper, events } = mountBar({
      page: 2,
      pageSize: 10,
      totalPages: 3,
      totalElements: 25,
    })

    expect(wrapper.text()).toContain('21–25 of 25')
    expect(nextButton(wrapper).attributes('disabled')).toBeDefined()

    await previousButton(wrapper).trigger('click')
    expect(events).toEqual([{ name: 'page', args: [1] }])

    wrapper.unmount()
  })

  it('emits the chosen page size', async () => {
    const { wrapper, events } = mountBar({
      page: 0,
      pageSize: 10,
      totalPages: 3,
      totalElements: 25,
    })

    wrapper.findComponent(USelect).vm.$emit('update:modelValue', 25)
    await flushPromises()
    expect(events).toEqual([{ name: 'size', args: [25] }])

    wrapper.unmount()
  })

  it('renders nothing without pages', () => {
    const { wrapper } = mountBar({ page: 0, pageSize: 15, totalPages: 0, totalElements: 0 })

    expect(wrapper.text()).toBe('')

    wrapper.unmount()
  })
})
