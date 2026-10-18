<script setup lang="ts">
/**
 * Shown by `App.vue` while the first navigation (session check, guards) is still pending. It stays
 * empty for the first `SHOW_DELAY_MS`, so a fast start never flashes it. It knows nothing about the
 * layout (the app does not yet know whether the user is signed in), so it renders no shell.
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useAppConfig } from '@nuxt/ui/runtime/vue/composables/useAppConfig.js'

const SHOW_DELAY_MS = 200

const loadingIcon: string = useAppConfig().ui.icons.loading
const isVisible = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined

onMounted(() => {
  timer = setTimeout(() => (isVisible.value = true), SHOW_DELAY_MS)
})
onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <output
    v-if="isVisible"
    class="flex min-h-dvh flex-col items-center justify-center gap-4 bg-(--pocketr-bg-main)"
  >
    <img src="/pocketr-logo-128x128.png" width="64" height="64" alt="" class="size-16" />
    <UIcon :name="loadingIcon" class="size-6 text-muted motion-safe:animate-spin" />
    <span class="sr-only">{{ $t('common.feedback.loading') }}</span>
  </output>
</template>
