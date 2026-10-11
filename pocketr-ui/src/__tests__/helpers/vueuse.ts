import { vi } from 'vitest'

/**
 * Viewport the mocked `useMediaQuery` reports: `desktop` is the `lg` breakpoint, so a spec sets
 * `viewport.desktop = false` to render the phone layout. A spec mocks VueUse with a dynamic import,
 * because `vi.mock` is hoisted above the imports:
 * `vi.mock('@vueuse/core', async () => (await import('@/__tests__/helpers/vueuse')).mockVueuse())`.
 */
export const viewport = { desktop: true }

/** VueUse with a fixed light colour mode and a media query that follows `viewport`. */
export async function mockVueuse() {
  const actual = await vi.importActual<typeof import('@vueuse/core')>('@vueuse/core')
  const { ref } = await vi.importActual<typeof import('vue')>('vue')
  return {
    ...actual,
    useColorMode: () => ref('light'),
    useMediaQuery: () => ref(viewport.desktop),
  }
}
