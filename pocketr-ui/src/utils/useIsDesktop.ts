import { useMediaQuery } from '@vueuse/core'

/** `true` from the `lg` breakpoint (1024px), where Pocketr switches from the mobile to the desktop layout. */
export function useIsDesktop() {
  return useMediaQuery('(min-width: 1024px)')
}
