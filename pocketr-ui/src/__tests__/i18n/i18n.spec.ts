import { describe, expect, it } from 'vitest'
import { i18n, initLocale, setLocale, uiLocaleFor } from '@/i18n'
import bg from '@/i18n/locales/bg.json'
import de from '@/i18n/locales/de.json'
import en from '@/i18n/locales/en.json'

function flattenMessages(messages: Record<string, unknown>, prefix = ''): Map<string, string> {
  const flattened = new Map<string, string>()
  for (const [key, value] of Object.entries(messages)) {
    const path = prefix ? `${prefix}.${key}` : key
    if (typeof value === 'string') {
      flattened.set(path, value)
    } else if (value && typeof value === 'object') {
      for (const [nestedPath, nested] of flattenMessages(value as Record<string, unknown>, path)) {
        flattened.set(nestedPath, nested)
      }
    }
  }
  return flattened
}

const placeholders = (value: string) => (value.match(/\{[^{}]+\}/g) ?? []).sort()

describe('lazy locale loading', () => {
  it('bundles English only; other locales are not loaded until requested', () => {
    expect(i18n.global.availableLocales).toEqual(['en'])
  })

  it('loads the Nuxt UI locale together with the messages, English until then', async () => {
    expect(uiLocaleFor('bg').code).toBe('en')

    await setLocale('bg')

    expect(uiLocaleFor('bg').code).toBe('bg')
    expect(uiLocaleFor('de').code).toBe('en')
  })

  it('initialises to the saved locale and loads its messages before resolving', async () => {
    localStorage.setItem('pocketr-locale', 'bg')

    await expect(initLocale()).resolves.toBe('bg')

    expect(i18n.global.locale.value).toBe('bg')
    expect(i18n.global.t('common.actions.save')).toBe('Запази')
  })

  it('switches locale only after the messages have loaded and remembers the choice', async () => {
    const pending = setLocale('bg')
    expect(i18n.global.locale.value).toBe('en')

    await pending

    expect(i18n.global.locale.value).toBe('bg')
    expect(localStorage.getItem('pocketr-locale')).toBe('bg')
  })

  it('resolves a regional locale to its language and unsupported or missing ones to English', async () => {
    await expect(setLocale('de-AT')).resolves.toBe('de')
    await expect(setLocale('fr')).resolves.toBe('en')
    await expect(setLocale(null)).resolves.toBe('en')
  })
})

describe('locale messages', () => {
  const english = flattenMessages(en)

  it('has no message English lacks, keeps every placeholder and translates every Bulgarian message', () => {
    for (const messages of [bg, de]) {
      const translated = flattenMessages(messages)

      const unknownKeys = [...translated.keys()].filter((key) => !english.has(key))
      expect(unknownKeys).toEqual([])
      for (const [key, text] of translated) {
        expect({ key, placeholders: placeholders(text) }).toEqual({
          key,
          placeholders: placeholders(english.get(key)!),
        })
      }
    }
    expect([...flattenMessages(bg).keys()].sort()).toEqual([...english.keys()].sort())
  })
})
