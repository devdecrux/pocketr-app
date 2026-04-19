<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from '@/components/Sidebar.vue'
import { SidebarProvider, SidebarTrigger } from '@/components/ui/sidebar'
import { useAppTheme } from '@/composables/useAppTheme'
import { useSessionManager } from '@/composables/useSessionManager'

const route = useRoute()

const isAuthLayout = computed(() => route.meta.layout === 'auth')

useAppTheme()
useSessionManager()
</script>

<template>
  <div class="h-dvh overflow-hidden">
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
