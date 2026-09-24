import { describe, expect, it } from 'vitest'
import { i18n, setLocale } from '@/i18n'
import bg from '@/i18n/locales/bg.json'
import en from '@/i18n/locales/en.json'

function flattenMessages(messages: Record<string, unknown>, prefix = ''): Map<string, string> {
  const flattened = new Map<string, string>()

  for (const [key, value] of Object.entries(messages)) {
    const path = prefix ? `${prefix}.${key}` : key
    if (typeof value === 'string') {
      flattened.set(path, value)
    } else if (value && typeof value === 'object') {
      for (const [nestedPath, nestedValue] of flattenMessages(
        value as Record<string, unknown>,
        path,
      )) {
        flattened.set(nestedPath, nestedValue)
      }
    }
  }

  return flattened
}

describe('Bulgarian locale', () => {
  it('translates every English message with matching placeholders', () => {
    const englishMessages = flattenMessages(en)
    const bulgarianMessages = flattenMessages(bg)

    expect([...bulgarianMessages.keys()].sort()).toEqual([...englishMessages.keys()].sort())

    for (const [key, english] of englishMessages) {
      const placeholders = (value: string) => (value.match(/\{[^{}]+\}/g) ?? []).sort()
      expect({ key, placeholders: placeholders(bulgarianMessages.get(key) ?? '') }).toEqual({
        key,
        placeholders: placeholders(english),
      })
    }
  })

  it('shows Bulgarian settings text after switching locale', () => {
    try {
      setLocale('bg')

      expect(i18n.global.t('views.settings.profile.title')).toBe('Настройки на профила')
      expect(i18n.global.t('common.actions.save')).toBe('Запази')
    } finally {
      setLocale('en')
    }
  })
})
