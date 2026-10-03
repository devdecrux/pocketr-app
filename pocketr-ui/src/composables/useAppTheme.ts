import { computed } from 'vue'
import { useColorMode } from '@vueuse/core'

export const APP_THEME_OPTIONS = [
  { value: 'light', labelKey: 'components.theme.modes.light' },
  { value: 'dark', labelKey: 'components.theme.modes.dark' },
  { value: 'auto', labelKey: 'components.theme.modes.auto' },
] as const

export type AppThemeMode = (typeof APP_THEME_OPTIONS)[number]['value']

export function useAppTheme() {
  const mode = useColorMode()

  /** The stored choice. `mode.value` resolves "auto" to the system theme, so read `mode.store`. */
  const selectedMode = computed<AppThemeMode>({
    get: () => {
      const stored = mode.store.value
      return APP_THEME_OPTIONS.some((option) => option.value === stored)
        ? (stored as AppThemeMode)
        : 'auto'
    },
    set: (value) => {
      mode.value = value
    },
  })

  return {
    selectedMode,
    options: APP_THEME_OPTIONS,
  }
}
