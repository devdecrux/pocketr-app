import { watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

/**
 * Keeps the browser tab in step with the app: `<html lang>` follows the active locale and the
 * title reads "Page · Pocketr" (just "Pocketr" on pages without a title key).
 */
export function useDocumentMeta(): void {
  const { t, locale } = useI18n()
  const route = useRoute()

  watchEffect(() => {
    document.documentElement.lang = locale.value
    const titleKey = route.meta.titleKey
    document.title = titleKey ? `${t(titleKey)} · ${t('app.name')}` : t('app.name')
  })
}
