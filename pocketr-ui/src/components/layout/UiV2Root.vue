<script setup lang="ts">
/**
 * TEMPORARY Nuxt UI render path for routes flagged with `meta.uiV2` (see `useUiMigration`).
 * Lazy-loaded by `App.vue` so legacy pages do not download Nuxt UI runtime code. In Phase 10 this
 * wrapper is folded back into `App.vue`.
 */
import { computed, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { bg, de, en } from '@nuxt/ui/locale'
import type { SupportedLocale } from '@/i18n'

// Lazy so public (auth-layout) pages do not download the sidebar and profile-menu code.
const AppSidebar = defineAsyncComponent(() => import('@/components/layout/AppSidebar.vue'))

const uiLocales = { bg, de, en } satisfies Record<SupportedLocale, typeof en>

const { locale } = useI18n()
const uiLocale = computed(() => uiLocales[locale.value as SupportedLocale] ?? en)

const route = useRoute()
const isAuthLayout = computed(() => route.meta.layout === 'auth')
</script>

<template>
  <UApp :locale="uiLocale">
    <RouterView v-if="isAuthLayout" />
    <UDashboardGroup v-else unit="px" :persistent="false" class="bg-(--pocketr-bg-main)">
      <AppSidebar />
      <RouterView />
    </UDashboardGroup>
  </UApp>
</template>
