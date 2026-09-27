<script setup lang="ts">
/**
 * TEMPORARY Nuxt UI render path for routes flagged with `meta.uiV2` (see `useUiMigration`).
 * Lazy-loaded by `App.vue` so legacy pages do not download Nuxt UI runtime code. In Phase 10 this
 * wrapper is folded back into `App.vue`.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { bg, de, en } from '@nuxt/ui/locale'
import type { SupportedLocale } from '@/i18n'

const uiLocales = { bg, de, en } satisfies Record<SupportedLocale, typeof en>

const { locale } = useI18n()
const uiLocale = computed(() => uiLocales[locale.value as SupportedLocale] ?? en)
</script>

<template>
  <UApp :locale="uiLocale">
    <RouterView />
  </UApp>
</template>
