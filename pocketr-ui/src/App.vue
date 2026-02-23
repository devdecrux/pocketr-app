<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from '@/components/Sidebar.vue'
import { SidebarProvider, SidebarTrigger } from '@/components/ui/sidebar'
import { useColorMode } from '@vueuse/core'
import { useSessionManager } from '@/composables/useSessionManager'

const route = useRoute()

const isAuthLayout = computed(() => route.meta.layout === 'auth')

useColorMode()
useSessionManager()
</script>

<template>
  <div class="h-dvh overflow-hidden">
    <RouterView v-if="isAuthLayout" />
    <SidebarProvider v-else class="h-full">
      <Sidebar />
      <div class="flex min-h-0 flex-1 flex-col">
        <SidebarTrigger class="lg:hidden" />
        <main class="min-h-0 flex-1 overflow-y-auto p-2">
          <RouterView />
        </main>
      </div>
    </SidebarProvider>
  </div>
</template>
