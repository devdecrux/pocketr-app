import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { h } from 'vue'
import ui from '@nuxt/ui/vue-plugin'
import UIcon from '@nuxt/ui/components/Icon.vue'
import AppExpandableRow from '@/components/shared/AppExpandableRow.vue'

function mountRow(expanded: boolean) {
  return mount(AppExpandableRow, {
    props: { panelId: 'row-details', expanded },
    slots: { default: () => h('span', 'Row content'), details: () => h('p', 'Row actions') },
    global: { plugins: [ui] },
  })
}

describe('AppExpandableRow', () => {
  it('is a closed row with a left chevron and no panel', () => {
    const wrapper = mountRow(false)

    const button = wrapper.get('button')
    expect(button.attributes('aria-expanded')).toBe('false')
    expect(button.attributes('aria-controls')).toBe('row-details')
    expect(button.text()).toBe('Row content')
    expect(wrapper.getComponent(UIcon).props('name')).toBe('i-lucide-chevron-left')
    expect(wrapper.find('#row-details').exists()).toBe(false)
  })

  it('shows the details panel and a down chevron when expanded', () => {
    const wrapper = mountRow(true)

    expect(wrapper.get('button').attributes('aria-expanded')).toBe('true')
    expect(wrapper.getComponent(UIcon).props('name')).toBe('i-lucide-chevron-down')
    expect(wrapper.get('#row-details').text()).toBe('Row actions')
  })

  it('asks its page to toggle on click', async () => {
    const wrapper = mountRow(false)

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('toggle')).toHaveLength(1)
  })
})
