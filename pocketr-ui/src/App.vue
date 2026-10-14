<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { useAppTheme } from '@/layout/useAppTheme'
import { useDocumentMeta } from '@/layout/useDocumentMeta'
import { useSessionManager } from '@/views/auth/useSessionManager'
import { uiLocaleFor } from '@/i18n'
import AppStartupLoading from '@/layout/AppStartupLoading.vue'

// Lazy so public (auth-layout) pages do not download the sidebar and profile-menu code.
const AppSidebar = defineAsyncComponent(() => import('@/layout/AppSidebar.vue'))

const { locale } = useI18n()
const uiLocale = computed(() => uiLocaleFor(locale.value))

const route = useRoute()
// Until the first navigation (session check, guards) resolves nothing is known about the layout:
// rendering the signed-in shell then would make guest pages download it. Do not await
// `router.isReady()` in main.ts instead: the route chunks import the entry module, so a top-level
// await there deadlocks the first navigation.
const isRouteResolved = computed(() => route.matched.length > 0)
const isAuthLayout = computed(() => route.meta.layout === 'auth')

useAppTheme()
useDocumentMeta()
useSessionManager()
</script>

<template>
  <UApp :locale="uiLocale">
    <template v-if="isRouteResolved">
      <RouterView v-if="isAuthLayout" />
      <UDashboardGroup v-else unit="px" :persistent="false" class="bg-(--pocketr-bg-main)">
        <AppSidebar />
        <RouterView />
      </UDashboardGroup>
    </template>
    <AppStartupLoading v-else />
  </UApp>
</template>
