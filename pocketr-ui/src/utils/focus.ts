import { type ComponentPublicInstance, nextTick } from 'vue'

/** A template ref that points at a DOM element or at a component whose root element is focusable. */
export type FocusTarget = ComponentPublicInstance | HTMLElement | null

/** The DOM element behind a template ref. */
export function elementOf(target: FocusTarget): HTMLElement | undefined {
  return target instanceof HTMLElement ? target : (target?.$el as HTMLElement | undefined)
}

export function focusTarget(target: FocusTarget): void {
  elementOf(target)?.focus()
}

/** Focuses the target once the pending DOM update has rendered it (`target` is read after it). */
export async function focusWhenRendered(target: () => FocusTarget): Promise<void> {
  await nextTick()
  focusTarget(target())
}

const FIELD_SELECTOR =
  'input:not([type="hidden"]):not([type="radio"]):not([disabled]), textarea:not([disabled]), select:not([disabled])'

/** Focuses the first editable field inside `container`; `false` when it has none. */
export function focusFirstField(container: HTMLElement | null | undefined): boolean {
  const field = container?.querySelector<HTMLElement>(FIELD_SELECTOR)
  field?.focus()
  return Boolean(field)
}

const CONTROL_SELECTOR =
  'input:not([type="hidden"]), textarea, select, button, [role="tab"], [tabindex]:not([role="tabpanel"])'

/**
 * Focuses the first control of the form inside `container` that sits in the Tab order: the active
 * type tab when the form has tabs, else the first field. Adornments and the dialog's own Close
 * button are outside the form or out of the Tab order, so they never win. With `skipTextEntry`
 * (touch layouts) a text field is left alone, because focusing it would raise the on-screen
 * keyboard. Returns `true` when a control was focused.
 */
export function focusFirstControl(
  container: HTMLElement | null | undefined,
  options: { skipTextEntry?: boolean } = {},
): boolean {
  const form = container?.querySelector<HTMLElement>('form')
  const control = [...(form?.querySelectorAll<HTMLElement>(CONTROL_SELECTOR) ?? [])].find(
    (element) =>
      element.getAttribute('tabindex') !== '-1' &&
      !element.hasAttribute('disabled') &&
      !element.closest('[hidden]') &&
      !(element.tagName === 'INPUT' && element.getAttribute('type') === 'radio'),
  )
  if (!control) return false
  if (options.skipTextEntry && (control.tagName === 'INPUT' || control.tagName === 'TEXTAREA')) {
    return false
  }
  control.focus({ preventScroll: true })
  return true
}
