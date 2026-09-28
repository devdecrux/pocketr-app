export const CATEGORY_COLOR_PRESETS = [
  { key: 'cyan', value: '#2cc4e6' },
  { key: 'green', value: '#62a864' },
  { key: 'blue', value: '#4a7fd0' },
  { key: 'rose', value: '#e45a77' },
  { key: 'amber', value: '#f5a616' },
  { key: 'slate', value: '#8391a5' },
  { key: 'neutral', value: '#c8cdd4' },
] as const

export type CategoryColorKey = (typeof CATEGORY_COLOR_PRESETS)[number]['key']

export function isPresetCategoryColor(color: string | null | undefined): boolean {
  const normalized = color?.toLowerCase()
  return CATEGORY_COLOR_PRESETS.some((preset) => preset.value === normalized)
}
