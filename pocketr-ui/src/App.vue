<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from '@/components/Sidebar.vue'
import { SidebarProvider, SidebarTrigger } from '@/components/ui/sidebar'
import { useAppTheme } from '@/composables/useAppTheme'
import { useSessionManager } from '@/composables/useSessionManager'
import { useUiMigration } from '@/composables/useUiMigration'

// TEMPORARY (UI migration): Nuxt UI render path for `meta.uiV2` routes, loaded only when needed.
const UiV2Root = defineAsyncComponent(() => import('@/components/layout/UiV2Root.vue'))

const route = useRoute()

const isAuthLayout = computed(() => route.meta.layout === 'auth')
const { isUiV2 } = useUiMigration()

useAppTheme()
useSessionManager()
</script>

<template>
  <UiV2Root v-if="isUiV2" />
  <div v-else class="h-dvh overflow-hidden">
    <RouterView v-if="isAuthLayout" />
    <SidebarProvider v-else class="app-shell h-full">
      <Sidebar />
      <div class="app-shell-content flex min-h-0 flex-1 flex-col">
        <SidebarTrigger class="app-shell-trigger lg:hidden" />
        <main class="app-shell-main min-h-0 flex-1 overflow-y-auto p-2">
          <RouterView />
        </main>
      </div>
    </SidebarProvider>
  </div>
</template>
