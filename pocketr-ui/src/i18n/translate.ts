import { i18n } from '@/i18n'

export function translate(key: string, values?: Record<string, string | number>): string {
  return i18n.global.t(key, values ?? {})
}
