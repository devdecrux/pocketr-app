import { createI18n } from 'vue-i18n'

import bg from './locales/bg.json'
import de from './locales/de.json'
import en from './locales/en.json'

const LOCALE_STORAGE_KEY = 'pocketr-locale'

export const fallbackLocale = 'en'

export const messages = {
  bg,
  de,
  en,
} as const

export type SupportedLocale = keyof typeof messages

export const supportedLocales = Object.keys(messages) as SupportedLocale[]

export const supportedLocaleLabels: Record<SupportedLocale, string> = {
  en: 'English',
  bg: 'Български',
  de: 'Deutsch',
}

function isSupportedLocale(locale: string): locale is SupportedLocale {
  return supportedLocales.includes(locale as SupportedLocale)
}

function normalizeLocale(locale: string): string {
  return locale.split('-')[0]?.toLowerCase() ?? fallbackLocale
}

function readCachedLocale(): SupportedLocale | null {
  if (typeof localStorage === 'undefined' || typeof localStorage.getItem !== 'function') {
    return null
  }

  const cachedLocale = localStorage.getItem(LOCALE_STORAGE_KEY)
  if (!cachedLocale) {
    return null
  }

  const normalizedLocale = normalizeLocale(cachedLocale)
  return isSupportedLocale(normalizedLocale) ? normalizedLocale : null
}

function resolveInitialLocale(): SupportedLocale {
  const cachedLocale = readCachedLocale()
  if (cachedLocale) {
    return cachedLocale
  }

  const preferredLocales =
    navigator.languages.length > 0 ? navigator.languages : [navigator.language]

  for (const locale of preferredLocales) {
    const normalizedLocale = normalizeLocale(locale)

    if (isSupportedLocale(normalizedLocale)) {
      return normalizedLocale
    }
  }

  return fallbackLocale
}

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: resolveInitialLocale(),
  fallbackLocale,
  messages,
})

export function resolveSupportedLocale(locale: string | null | undefined): SupportedLocale {
  if (!locale) {
    return fallbackLocale
  }

  const normalizedLocale = normalizeLocale(locale)
  return isSupportedLocale(normalizedLocale) ? normalizedLocale : fallbackLocale
}

export function setLocale(locale: string | null | undefined): SupportedLocale {
  const supportedLocale = resolveSupportedLocale(locale)
  i18n.global.locale.value = supportedLocale

  if (typeof localStorage !== 'undefined' && typeof localStorage.setItem === 'function') {
    localStorage.setItem(LOCALE_STORAGE_KEY, supportedLocale)
  }

  return supportedLocale
}
