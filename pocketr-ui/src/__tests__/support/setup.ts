/**
 * Global test setup (vitest `setupFiles`).
 *
 * Mocking mechanism: `vi.mock` calls in a setup file apply to every spec. `@/api/http` and
 * `@/api/csrf` use factories and every endpoint module is auto-mocked, so no spec can reach the
 * network. `installApiDefaults()` gives each auto-mocked function a harmless resolved value before
 * every test (config has `mockReset: true`); specs override per scenario with
 * `vi.mocked(listAccounts).mockResolvedValue(ACCOUNTS)`. A spec that needs the real module (for
 * example `api/ledger.spec.ts`) calls `vi.unmock('@/api/ledger')`. `@/api/errors` stays real.
 */
import { enableAutoUnmount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, vi } from 'vitest'
import { i18n } from '@/i18n'
import { installApiDefaults } from '@/__tests__/support/api'
import { colorMode, viewport } from '@/__tests__/support/vueuse'

vi.mock('@vueuse/core', async () => (await import('@/__tests__/support/vueuse')).mockVueuse())

vi.mock('@/api/http', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}))
vi.mock('@/api/csrf')
vi.mock('@/api/accounts')
vi.mock('@/api/categories')
vi.mock('@/api/currencies')
vi.mock('@/api/households')
vi.mock('@/api/ledger')
vi.mock('@/api/reports')
vi.mock('@/api/user')

// Hooks after a test run in reverse order: unmount the wrappers first, then clear what is left.
afterEach(() => {
  document.body.innerHTML = ''
  i18n.global.locale.value = 'en'
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

enableAutoUnmount(afterEach)

beforeEach(() => {
  setActivePinia(createPinia())

  // jsdom has no `ResizeObserver`, which Reka UI's popper and select primitives construct.
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  )
  // Node's experimental `localStorage` is not usable without a file, so keep one in memory.
  const storage = new Map<string, string>()
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
    removeItem: (key: string) => storage.delete(key),
  })

  // jsdom does not implement `scrollIntoView`, which lists call to keep a focused row visible.
  Element.prototype.scrollIntoView = vi.fn()

  viewport.desktop = true
  colorMode.store.value = 'light'
  installApiDefaults()
})
