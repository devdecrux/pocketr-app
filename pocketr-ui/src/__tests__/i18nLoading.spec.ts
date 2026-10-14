import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { stubLocalStorage } from '@/__tests__/helpers/stubs'
import { i18n, initLocale, setLocale, uiLocaleFor } from '@/i18n'

describe('lazy locale loading', () => {
  beforeEach(() => {
    stubLocalStorage()
  })

  afterEach(async () => {
    await setLocale('en')
    vi.unstubAllGlobals()
  })

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

  it('normalises regional and unsupported locales', async () => {
    await expect(setLocale('de-AT')).resolves.toBe('de')
    await expect(setLocale('fr')).resolves.toBe('en')
    await expect(setLocale(null)).resolves.toBe('en')
  })

  it('falls back to English for keys missing from the active locale', async () => {
    await setLocale('de')
    i18n.global.mergeLocaleMessage('en', { fallbackProbe: 'English only' })

    expect(i18n.global.t('fallbackProbe')).toBe('English only')
  })
})
