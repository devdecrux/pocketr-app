<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppSidebar from '@/components/AppSidebar.vue'
import { SidebarProvider, SidebarTrigger } from '@/components/ui/sidebar'
import { useColorMode } from '@vueuse/core'

const route = useRoute()

const isAuthLayout = computed(() => route.meta.layout === 'auth')

useColorMode()
</script>

<template>
  <div class="min-h-screen">
    <RouterView v-if="isAuthLayout" />
    <SidebarProvider v-else>
      <AppSidebar />
      <div class="flex flex-1 flex-col">
        <SidebarTrigger class="lg:hidden" />
        <main class="p-2">
          <RouterView />
        </main>
      </div>
    </SidebarProvider>
  </div>
</template>
