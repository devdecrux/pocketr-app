import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref } from 'vue'
import ui from '@nuxt/ui/vue-plugin'
import UApp from '@nuxt/ui/components/App.vue'
import { i18n } from '@/i18n'
import AppFormOverlay from '@/components/shared/AppFormOverlay.vue'

const mocks = vi.hoisted(() => ({ desktop: { value: true } }))

vi.mock('@vueuse/core', async () => {
  const actual = await vi.importActual<typeof import('@vueuse/core')>('@vueuse/core')
  const { ref } = await vi.importActual<typeof import('vue')>('vue')
  return { ...actual, useMediaQuery: () => ref(mocks.desktop.value) }
})

interface HarnessOptions {
  formId?: string
  footer?: boolean
  size?: 'md' | 'lg'
  cancelVariant?: 'outline' | 'ghost'
}

function mountOverlay(options: HarnessOptions = {}) {
  const open = ref(false)
  const submitted = vi.fn()
  const formSubmitted = vi.fn()

  const Harness = defineComponent({
    setup() {
      return () =>
        h(UApp, null, {
          default: () =>
            h('div', [
              h('button', { id: 'trigger', onClick: () => (open.value = true) }, 'Open'),
              h(
                AppFormOverlay,
                {
                  open: open.value,
                  'onUpdate:open': (value: boolean) => (open.value = value),
                  title: 'Create thing',
                  description: 'Add a thing.',
                  submitLabel: 'Create',
                  formId: options.formId,
                  size: options.size,
                  cancelVariant: options.cancelVariant,
                  onSubmit: submitted,
                },
                {
                  default: () =>
                    options.formId
                      ? h(
                          'form',
                          {
                            id: options.formId,
                            onSubmit: (event: Event) => {
                              event.preventDefault()
                              formSubmitted()
                            },
                          },
                          [h('input', { id: 'thing-name' })],
                        )
                      : null,
                  ...(options.footer
                    ? {
                        footer: ({ close }: { close: () => void }) =>
                          h('button', { id: 'custom-footer', onClick: close }, 'Done'),
                      }
                    : {}),
                },
              ),
            ]),
        })
    },
  })

  const wrapper = mount(Harness, { attachTo: document.body, global: { plugins: [i18n, ui] } })
  return { wrapper, open, submitted, formSubmitted }
}

function dialog(): HTMLElement | null {
  return document.body.querySelector<HTMLElement>('[role="dialog"]')
}

function dialogButton(text: string): HTMLButtonElement {
  const button = [...(dialog()?.querySelectorAll<HTMLButtonElement>('button') ?? [])].find(
    (candidate) => candidate.textContent?.trim() === text || candidate.ariaLabel === text,
  )
  if (!button) throw new Error(`No dialog button "${text}"`)
  return button
}

async function openOverlay(open: { value: boolean }) {
  document.getElementById('trigger')?.focus()
  open.value = true
  await flushPromises()
  await nextTick()
}

function pressEscape(target: EventTarget = document.activeElement ?? document.body) {
  target.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
}

describe('AppFormOverlay', () => {
  beforeEach(() => {
    mocks.desktop.value = true
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('renders a modal with title, description and the Cancel / submit footer on desktop', async () => {
    const { wrapper, open } = mountOverlay({ formId: 'thing-form' })
    await openOverlay(open)

    expect(dialog()?.textContent).toContain('Create thing')
    expect(dialog()?.textContent).toContain('Add a thing.')
    expect(document.body.querySelector('[data-vaul-drawer]')).toBeNull()
    expect(dialogButton('Cancel')).toBeTruthy()
    expect(dialogButton('Close')).toBeTruthy()

    wrapper.unmount()
  })

  it('widens the desktop modal for the large size and can render Cancel as plain text', async () => {
    const regular = mountOverlay({ formId: 'thing-form' })
    await openOverlay(regular.open)
    expect(dialog()?.className).toContain('max-w-[440px]')
    expect(dialogButton('Cancel').className).toContain('ring-accented')
    regular.wrapper.unmount()
    document.body.innerHTML = ''

    const wide = mountOverlay({ formId: 'thing-form', size: 'lg', cancelVariant: 'ghost' })
    await openOverlay(wide.open)
    expect(dialog()?.className).toContain('max-w-[536px]')
    expect(dialogButton('Cancel').className).not.toContain('ring-accented')
    wide.wrapper.unmount()
  })

  it('renders a drawer below the desktop breakpoint', async () => {
    mocks.desktop.value = false
    const { wrapper, open } = mountOverlay({ formId: 'thing-form' })
    await openOverlay(open)

    expect(document.body.querySelector('[data-vaul-drawer]')).not.toBeNull()
    expect(dialog()?.textContent).toContain('Create thing')

    wrapper.unmount()
  })

  it('wires the footer submit button to the form in the body', async () => {
    const { wrapper, open, submitted, formSubmitted } = mountOverlay({ formId: 'thing-form' })
    await openOverlay(open)

    const submit = dialogButton('Create')
    expect(submit.getAttribute('type')).toBe('submit')
    expect(submit.getAttribute('form')).toBe('thing-form')
    submit.click()
    await flushPromises()

    expect(formSubmitted).toHaveBeenCalledTimes(1)
    expect(submitted).not.toHaveBeenCalled()
    expect(open.value).toBe(true)

    wrapper.unmount()
  })

  it('emits submit from a form-less overlay', async () => {
    const { wrapper, open, submitted } = mountOverlay()
    await openOverlay(open)

    const submit = dialogButton('Create')
    expect(submit.getAttribute('type')).toBe('button')
    submit.click()
    await flushPromises()

    expect(submitted).toHaveBeenCalledTimes(1)

    wrapper.unmount()
  })

  it('focuses the first field on open in the desktop modal', async () => {
    const { wrapper, open } = mountOverlay({ formId: 'thing-form' })
    await openOverlay(open)

    expect(document.activeElement?.id).toBe('thing-name')

    wrapper.unmount()
  })

  it('keeps focus on the dialog, not a field, when the mobile drawer opens', async () => {
    mocks.desktop.value = false
    const { wrapper, open } = mountOverlay({ formId: 'thing-form' })
    await openOverlay(open)

    expect(document.activeElement).toBe(dialog())
    expect(document.activeElement?.id).not.toBe('thing-name')
    expect(dialog()?.contains(document.activeElement)).toBe(true)

    wrapper.unmount()
  })

  it.each([
    ['desktop', true],
    ['mobile', false],
  ])('closes through Cancel, X and Escape on %s', async (_label, desktop) => {
    mocks.desktop.value = desktop
    const { wrapper, open } = mountOverlay({ formId: 'thing-form' })

    await openOverlay(open)
    dialogButton('Cancel').click()
    await flushPromises()
    expect(open.value).toBe(false)

    await openOverlay(open)
    dialogButton('Close').click()
    await flushPromises()
    expect(open.value).toBe(false)

    await openOverlay(open)
    pressEscape()
    await flushPromises()
    expect(open.value).toBe(false)

    wrapper.unmount()
  })

  it.each([
    ['desktop', true],
    ['mobile', false],
  ])('never closes on an outside press on %s', async (_label, desktop) => {
    mocks.desktop.value = desktop
    const { wrapper, open } = mountOverlay({ formId: 'thing-form' })
    await openOverlay(open)

    const outside = document.getElementById('trigger') ?? document.body
    outside.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }))
    outside.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }))
    outside.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()

    expect(open.value).toBe(true)
    expect(dialog()).not.toBeNull()

    wrapper.unmount()
  })

  it('accepts a custom footer', async () => {
    const { wrapper, open } = mountOverlay({ formId: 'thing-form', footer: true })
    await openOverlay(open)

    expect(() => dialogButton('Cancel')).toThrow()
    dialogButton('Done').click()
    await flushPromises()
    expect(open.value).toBe(false)

    wrapper.unmount()
  })
})
