import { h, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import PasswordInput from '@/components/forms/PasswordInput.vue'
import { i18n } from '@/i18n'

function mountInForm() {
  const onSubmit = vi.fn()
  const value = ref('secret')
  const field = ref<InstanceType<typeof PasswordInput> | null>(null)
  const wrapper = mount(
    () =>
      h(UApp, null, () =>
        h('form', { onSubmit: (event: Event) => (event.preventDefault(), onSubmit()) }, [
          h(PasswordInput, {
            ref: field,
            id: 'password',
            name: 'password',
            autocomplete: 'new-password',
            required: true,
            'aria-label': 'Password',
            modelValue: value.value,
            'onUpdate:modelValue': (next: string) => (value.value = next),
          }),
        ]),
      ),
    { attachTo: document.body, global: { plugins: [i18n, ui] } },
  )
  return { wrapper, onSubmit, value, field }
}

describe('PasswordInput', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('forwards the input attributes and starts hidden', () => {
    const { wrapper } = mountInForm()
    const input = wrapper.get('#password')

    expect(input.attributes('type')).toBe('password')
    expect(input.attributes('name')).toBe('password')
    expect(input.attributes('autocomplete')).toBe('new-password')
    expect(input.attributes('required')).toBeDefined()
    expect(input.attributes('aria-label')).toBe('Password')
    expect((input.element as HTMLInputElement).value).toBe('secret')

    const toggle = wrapper.get('button')
    expect(toggle.attributes('type')).toBe('button')
    expect(toggle.attributes('aria-label')).toBe('Show password')
    expect(toggle.attributes('aria-pressed')).toBe('false')
    expect(toggle.attributes('aria-controls')).toBe('password')
    wrapper.unmount()
  })

  it('toggles visibility without submitting the form and keeps focus on the button', async () => {
    const { wrapper, onSubmit } = mountInForm()
    const toggle = wrapper.get('button')
    ;(toggle.element as HTMLButtonElement).focus()

    await toggle.trigger('click')
    expect(wrapper.get('#password').attributes('type')).toBe('text')
    expect(toggle.attributes('aria-label')).toBe('Hide password')
    expect(toggle.attributes('aria-pressed')).toBe('true')
    expect(document.activeElement).toBe(toggle.element)

    await toggle.trigger('click')
    expect(wrapper.get('#password').attributes('type')).toBe('password')
    expect(toggle.attributes('aria-pressed')).toBe('false')

    // A real click on a `type="button"` never submits its form.
    ;(toggle.element as HTMLButtonElement).click()
    expect(onSubmit).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('updates the model and hides again through the exposed `hide()`', async () => {
    const { wrapper, value, field } = mountInForm()

    await wrapper.get('#password').setValue('changed')
    expect(value.value).toBe('changed')

    await wrapper.get('button').trigger('click')
    expect(wrapper.get('#password').attributes('type')).toBe('text')
    field.value?.hide()
    await wrapper.vm.$nextTick()
    expect(wrapper.get('#password').attributes('type')).toBe('password')
    expect(field.value?.inputRef).toBe(wrapper.get('#password').element)
    wrapper.unmount()
  })
})
