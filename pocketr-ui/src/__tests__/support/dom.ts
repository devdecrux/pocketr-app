import { flushPromises, type VueWrapper } from '@vue/test-utils'
import USelect from '@nuxt/ui/components/Select.vue'
import USelectMenu from '@nuxt/ui/components/SelectMenu.vue'

/** The open overlays (modals and drawers) in the document body; Nuxt UI teleports them there. */
export function dialogs(): HTMLElement[] {
  return [...document.body.querySelectorAll<HTMLElement>('[role="dialog"]')].filter(
    // A closed overlay stays mounted while its exit animation would run, so only open ones count.
    (dialog) => dialog.getAttribute('data-state') !== 'closed',
  )
}

export function dialogByTitle(title: string): HTMLElement {
  const match = dialogs().find((dialog) => dialog.textContent?.includes(title))
  if (!match) throw new Error(`No dialog "${title}"`)
  return match
}

/** The button inside `root` whose text or accessible name is `text`. */
export function buttonIn(root: ParentNode, text: string): HTMLButtonElement {
  const button = [...root.querySelectorAll<HTMLButtonElement>('button')].find(
    (candidate) =>
      candidate.textContent?.trim() === text || candidate.getAttribute('aria-label') === text,
  )
  if (!button) throw new Error(`No button "${text}"`)
  return button
}

export async function click(button: Element): Promise<void> {
  ;(button as HTMLElement).click()
  await flushPromises()
}

export async function typeInto(input: HTMLInputElement, value: string): Promise<void> {
  input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
  await flushPromises()
}

/** The desktop table's body rows. */
export function tableRows(wrapper: VueWrapper) {
  return wrapper.findAll('main tbody tr')
}

export function rowWith(wrapper: VueWrapper, text: string) {
  const match = tableRows(wrapper).find((row) => row.text().includes(text))
  if (!match) throw new Error(`No row "${text}"`)
  return match
}

export function columnHeaders(wrapper: VueWrapper): string[] {
  return wrapper.findAll('main thead th').map((th) => th.text())
}

/** The first column of every desktop row (the item name). */
export function rowNames(wrapper: VueWrapper): string[] {
  return tableRows(wrapper).map((row) => row.find('td').text())
}

/** The text of the first `role="alert"` inside `root`, or `null` when there is none. */
export function alertText(root: ParentNode): string | null {
  return root.querySelector('[role="alert"]')?.textContent?.trim() ?? null
}

type SelectItem = { value: string | number; label?: string; type?: string }

type SelectProps = { items: (SelectItem | SelectItem[])[] }

function findSelect(wrapper: VueWrapper, ariaLabel: string) {
  const match = [
    ...wrapper.findAllComponents(USelect),
    ...wrapper.findAllComponents(USelectMenu),
  ].find((select) => select.vm.$attrs['aria-label'] === ariaLabel)
  if (!match) throw new Error(`No select "${ariaLabel}"`)
  return match
}

/** The option labels of the select named `ariaLabel`. */
export function optionLabels(wrapper: VueWrapper, ariaLabel: string): string[] {
  const { items } = findSelect(wrapper, ariaLabel).props() as SelectProps
  return items
    .flat()
    .filter((item) => item.type !== 'label' && item.label !== undefined)
    .map((item) => item.label!)
}

/**
 * Picks `optionLabel` in the select named `ariaLabel`. jsdom lacks the pointer capture API that
 * Reka UI's select trigger calls, so the select is driven by emitting its model update.
 */
export async function chooseOption(
  wrapper: VueWrapper,
  ariaLabel: string,
  optionLabel: string,
): Promise<void> {
  const select = findSelect(wrapper, ariaLabel)
  const { items } = select.props() as SelectProps
  const item = items.flat().find((candidate) => candidate.label === optionLabel)
  if (!item) throw new Error(`No option "${optionLabel}" in "${ariaLabel}"`)
  select.vm.$emit('update:modelValue', item.value)
  await flushPromises()
}

/** Opens the page's filter panel through its Filters toggle (a no-op when it is already open). */
export async function openFilters(wrapper: VueWrapper): Promise<void> {
  const toggle = buttonIn(wrapper.element as HTMLElement, 'Filters')
  if (toggle.getAttribute('aria-expanded') !== 'true') await click(toggle)
}

/** Picks `optionLabel` in the filter select named `ariaLabel`, opening the panel first. */
export async function chooseFilter(
  wrapper: VueWrapper,
  ariaLabel: string,
  optionLabel: string,
): Promise<void> {
  await openFilters(wrapper)
  await chooseOption(wrapper, ariaLabel, optionLabel)
}
