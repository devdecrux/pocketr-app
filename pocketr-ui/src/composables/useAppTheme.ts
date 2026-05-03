import { useColorMode } from '@vueuse/core'

export const APP_THEME_OPTIONS = [
  { value: 'light', labelKey: 'components.theme.modes.light' },
  { value: 'dark', labelKey: 'components.theme.modes.dark' },
  { value: 'auto', labelKey: 'components.theme.modes.auto' },
] as const

export const APP_THEME_PRESETS = {
  pocketr: {
    labelKey: 'components.theme.presets.pocketr',
  },
} as const

export type AppThemeMode = (typeof APP_THEME_OPTIONS)[number]['value']
export type AppThemePreset = keyof typeof APP_THEME_PRESETS

export const DEFAULT_THEME_PRESET: AppThemePreset = 'pocketr'

function applyThemePreset(preset: AppThemePreset): void {
  if (typeof document === 'undefined') return

  document.documentElement.dataset.themePreset = preset
}

export function useAppTheme() {
  const mode = useColorMode()

  applyThemePreset(DEFAULT_THEME_PRESET)

  return {
    mode,
    options: APP_THEME_OPTIONS,
    preset: DEFAULT_THEME_PRESET,
    presets: APP_THEME_PRESETS,
  }
}
