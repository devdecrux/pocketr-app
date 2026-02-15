<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from '@/components/Sidebar.vue'
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
      <Sidebar />
      <div class="flex flex-1 flex-col">
        <SidebarTrigger class="lg:hidden" />
        <main class="p-2">
          <RouterView />
        </main>
      </div>
    </SidebarProvider>
  </div>
</template>
