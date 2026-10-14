import { shallowReactive } from 'vue'
import { createI18n } from 'vue-i18n'
import { en as uiEn } from '@nuxt/ui/locale'

import en from './locales/en.json'

const LOCALE_STORAGE_KEY = 'pocketr-locale'

const fallbackLocale = 'en'

type MessageSchema = typeof en

/**
 * English is bundled: it is the fallback for keys a locale lacks (`de` is intentionally
 * incomplete), so it must always be available. The other locales are separate chunks that are
 * downloaded only when they become active.
 */
const localeLoaders = {
  bg: () => import('./locales/bg.json'),
  de: () => import('./locales/de.json'),
} satisfies Record<string, () => Promise<{ default: unknown }>>

/** Nuxt UI's own component strings (aria labels, "No results"), loaded with the app messages. */
const uiLocaleLoaders = {
  bg: () => import('@nuxt/ui/locale').then(({ bg }) => bg),
  de: () => import('@nuxt/ui/locale').then(({ de }) => de),
} satisfies Record<string, () => Promise<typeof uiEn>>

const loadedUiLocales = shallowReactive<Record<string, typeof uiEn>>({ en: uiEn })

/** The Nuxt UI locale for `locale`; English until that locale's strings have loaded. */
export function uiLocaleFor(locale: string): typeof uiEn {
  return loadedUiLocales[locale] ?? uiEn
}

export const supportedLocales = ['en', 'bg', 'de'] as const

type SupportedLocale = (typeof supportedLocales)[number]

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
  locale: fallbackLocale,
  fallbackLocale,
  messages: { en } as Record<string, MessageSchema>,
})

async function loadLocaleMessages(locale: SupportedLocale): Promise<void> {
  if (locale === 'en' || i18n.global.availableLocales.includes(locale)) {
    return
  }

  const [loaded, uiLocale] = await Promise.all([localeLoaders[locale](), uiLocaleLoaders[locale]()])
  i18n.global.setLocaleMessage(locale, loaded.default as MessageSchema)
  loadedUiLocales[locale] = uiLocale
}

let latestRequestedLocale: SupportedLocale = fallbackLocale

/** Loads the locale determined at startup (saved choice, then browser language) before first render. */
export async function initLocale(): Promise<SupportedLocale> {
  return applyLocale(resolveInitialLocale())
}

function resolveSupportedLocale(locale: string | null | undefined): SupportedLocale {
  if (!locale) {
    return fallbackLocale
  }

  const normalizedLocale = normalizeLocale(locale)
  return isSupportedLocale(normalizedLocale) ? normalizedLocale : fallbackLocale
}

/**
 * Switches the active locale once its messages are loaded. If the download fails the current
 * locale stays active; a newer request supersedes an older one still loading.
 */
export async function setLocale(locale: string | null | undefined): Promise<SupportedLocale> {
  const supportedLocale = resolveSupportedLocale(locale)
  const activeLocale = await applyLocale(supportedLocale)
  if (activeLocale === supportedLocale) {
    persistLocale(supportedLocale)
  }

  return activeLocale
}

async function applyLocale(supportedLocale: SupportedLocale): Promise<SupportedLocale> {
  latestRequestedLocale = supportedLocale

  try {
    await loadLocaleMessages(supportedLocale)
  } catch {
    return i18n.global.locale.value as SupportedLocale
  }

  if (latestRequestedLocale === supportedLocale) {
    i18n.global.locale.value = supportedLocale
  }

  return i18n.global.locale.value as SupportedLocale
}

function persistLocale(supportedLocale: SupportedLocale): void {
  if (typeof localStorage !== 'undefined' && typeof localStorage.setItem === 'function') {
    localStorage.setItem(LOCALE_STORAGE_KEY, supportedLocale)
  }
}
