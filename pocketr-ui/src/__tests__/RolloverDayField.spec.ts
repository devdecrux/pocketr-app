import { h, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import RolloverDayField from '@/components/fields/RolloverDayField.vue'
import { i18n } from '@/i18n'

function mountField(props: { disabled?: boolean; error?: string } = {}) {
  const day = ref<number | null>(5)
  const wrapper = mount(
    () =>
      h(UApp, null, () =>
        h(RolloverDayField, {
          modelValue: day.value,
          'onUpdate:modelValue': (next: number | null) => (day.value = next),
          label: 'Rollover period',
          helpText: 'Day the month starts',
          helpLabel: 'About rollover period',
          helpId: 'test-rollover-help',
          fieldUi: {},
          inputClass: 'lg:w-28',
          ...props,
        }),
      ),
    { attachTo: document.body, global: { plugins: [i18n, ui] } },
  )
  return { wrapper, day }
}

describe('RolloverDayField', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('renders a labelled 1-31 stepper, full width, described by the help text', () => {
    const { wrapper } = mountField()
    const input = wrapper.get('input')

    expect(wrapper.get('label').text()).toBe('Rollover period')
    expect(wrapper.get('label').attributes('for')).toBe(input.attributes('id'))
    expect(input.attributes('aria-describedby')).toBe('test-rollover-help')
    expect(wrapper.get('#test-rollover-help').classes()).toContain('sr-only')
    expect(wrapper.get('#test-rollover-help').text()).toBe('Day the month starts')
    const root = wrapper.get('input').element.closest('[data-slot="root"]')
    expect(root?.className).toContain('w-full')
    expect(root?.className).toContain('lg:w-28')
    wrapper.unmount()
  })

  it('updates the model when the value is typed', async () => {
    const { wrapper, day } = mountField()
    const input = wrapper.get('input')

    await input.setValue('12')
    await input.trigger('blur')
    expect(day.value).toBe(12)
    wrapper.unmount()
  })

  it('shows the error and honours the disabled state', () => {
    const { wrapper } = mountField({ disabled: true, error: 'Pick 1 to 31' })

    expect(wrapper.text()).toContain('Pick 1 to 31')
    expect(wrapper.get('input').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
})
