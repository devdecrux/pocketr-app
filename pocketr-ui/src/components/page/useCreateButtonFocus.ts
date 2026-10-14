import { ref } from 'vue'
import { elementOf, type FocusTarget } from '@/utils/focus'
import { useIsDesktop } from '@/utils/useIsDesktop'

/**
 * Focus target for a page whose delete or archive removes the button that opened it. The page binds
 * its two `AppCreateButton`s (`ref="headingButton"`, `ref="footerButton"`); `createButton()` is the
 * visible one, because the heading button exists from `lg` and the footer button below it.
 */
export function useCreateButtonFocus() {
  const isDesktop = useIsDesktop()
  const headingButton = ref<FocusTarget>(null)
  const footerButton = ref<FocusTarget>(null)

  function createButton(): HTMLElement | null {
    return elementOf((isDesktop.value ? headingButton : footerButton).value) ?? null
  }

  return { headingButton, footerButton, createButton }
}
