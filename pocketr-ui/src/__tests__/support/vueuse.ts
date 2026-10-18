import { vi } from 'vitest'
import { ref } from 'vue'

/**
 * Viewport the mocked `useMediaQuery` reports: `desktop` is the `lg` breakpoint, so a spec sets
 * `viewport.desktop = false` before mounting to render the phone layout.
 */
export const viewport = { desktop: true }

type ColorModeChoice = 'light' | 'dark' | 'auto'

/** The stored colour-mode choice the mocked `useColorMode` reads and writes. */
export const colorMode = { store: ref<ColorModeChoice>('light') }

/** VueUse with a media query that follows `viewport` and a colour mode backed by `colorMode`. */
export async function mockVueuse() {
  const actual = await vi.importActual<typeof import('@vueuse/core')>('@vueuse/core')
  return {
    ...actual,
    useMediaQuery: () => ref(viewport.desktop),
    // Like VueUse: `value` resolves "auto" to the system theme (light here); `store` keeps the choice.
    useColorMode: () => ({
      store: colorMode.store,
      get value() {
        return colorMode.store.value === 'auto' ? 'light' : colorMode.store.value
      },
      set value(next: ColorModeChoice) {
        colorMode.store.value = next
      },
    }),
  }
}
