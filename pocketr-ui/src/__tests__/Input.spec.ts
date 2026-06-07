import { defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { Input as AppInput } from '@/components/ui/input'

describe('Input', () => {
  it('updates its model before a form is submitted in the same event turn', () => {
    const wrapper = mount(
      defineComponent({
        components: { AppInput },
        setup() {
          const value = ref('')
          const submittedValue = ref('')

          return { value, submittedValue }
        },
        template: `
          <form @submit.prevent="submittedValue = value">
            <AppInput v-model="value" />
          </form>
        `,
      }),
    )

    const input = wrapper.get('input').element
    input.value = 'user@example.com'
    input.dispatchEvent(new Event('input', { bubbles: true }))
    wrapper.get('form').element.dispatchEvent(new Event('submit', { bubbles: true }))

    expect(wrapper.vm.submittedValue).toBe('user@example.com')
  })
})
