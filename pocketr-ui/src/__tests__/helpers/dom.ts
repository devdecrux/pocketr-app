import { flushPromises } from '@vue/test-utils'

/** The open overlays (modals and drawers) in the document body; Nuxt UI teleports them there. */
export function dialogs(): HTMLElement[] {
  return [...document.body.querySelectorAll<HTMLElement>('[role="dialog"]')].filter(
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
