import { createPinia } from 'pinia'
import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { h, ref } from 'vue'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import AccountForm from '@/components/forms/AccountForm.vue'
import AppTypeTabs from '@/components/forms/AppTypeTabs.vue'
import TransactionForm from '@/components/forms/TransactionForm.vue'
import { i18n } from '@/i18n'

const ITEMS = [
  { value: 'one', label: 'One' },
  { value: 'two', label: 'Two' },
  { value: 'three', label: 'Three' },
]

function mountInApp(content: () => ReturnType<typeof h>) {
  return mount(
    { render: () => h(UApp, null, { default: content }) },
    { attachTo: document.body, global: { plugins: [i18n, ui, createPinia()] } },
  )
}

function mountTabs() {
  const selected = ref('one')
  const wrapper = mountInApp(() =>
    h(
      AppTypeTabs<string>,
      {
        modelValue: selected.value,
        'onUpdate:modelValue': (value: string) => (selected.value = value),
        items: ITEMS,
        groupLabel: 'Pick one',
      },
      { content: ({ item }: { item: { value: string } }) => h('p', `panel ${item.value}`) },
    ),
  )
  return { wrapper, selected }
}

/** The classes that define how the segmented control looks, independent of its content. */
function trackClasses(wrapper: ReturnType<typeof mountInApp>) {
  return {
    list: wrapper.get('[role="tablist"]').classes().sort().join(' '),
    tab: wrapper.get('[role="tab"]').classes().sort().join(' '),
  }
}

describe('AppTypeTabs', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('is a named group of tabs and renders the panel of the selected item only', async () => {
    const { wrapper } = mountTabs()

    expect(wrapper.get('[role="group"]').attributes('aria-label')).toBe('Pick one')
    expect(wrapper.findAll('[role="tab"]').map((tab) => tab.text())).toEqual([
      'One',
      'Two',
      'Three',
    ])
    expect(wrapper.text()).toContain('panel one')
    expect(wrapper.text()).not.toContain('panel two')

    wrapper.unmount()
  })

  it('selects a segment on click', async () => {
    const { wrapper, selected } = mountTabs()

    const tabs = wrapper.findAll('[role="tab"]')
    // Arrow-key movement is Reka UI's roving focus, which jsdom cannot lay out; it is checked in
    // the browser walk-through.
    await tabs[1]!.trigger('mousedown')
    await flushPromises()
    expect(selected.value).toBe('two')
    expect(wrapper.text()).toContain('panel two')

    wrapper.unmount()
  })

  it('looks the same in the create-account and create-transaction forms', async () => {
    const account = mountInApp(() => h(AccountForm, { id: 'account-form' }))
    const transaction = mountInApp(() => h(TransactionForm, { id: 'transaction-form' }))
    await flushPromises()

    expect(trackClasses(account)).toEqual(trackClasses(transaction))
    expect(account.get('[role="tablist"]').classes()).toContain('rounded-lg')
    expect(account.get('[role="tab"]').classes()).toContain('h-8')
    expect(account.get('[role="tab"]').classes()).toContain('lg:h-9')

    account.unmount()
    transaction.unmount()
  })
})
