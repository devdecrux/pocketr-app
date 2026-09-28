<script setup lang="ts">
/**
 * The single form-level message slot of the sign-in and registration forms, rendered above the
 * first field. Errors are announced assertively (`role="alert"`), warnings politely (`role="status"`).
 */
import { computed } from 'vue'

const props = defineProps<{ tone: 'error' | 'warning'; message: string }>()

const isError = computed(() => props.tone === 'error')
</script>

<template>
  <UAlert
    :role="isError ? 'alert' : 'status'"
    :color="props.tone"
    variant="subtle"
    :icon="isError ? 'i-lucide-circle-alert' : 'i-lucide-triangle-alert'"
    :title="props.message"
    :ui="{
      root: ['items-center gap-2.5 px-3.5 py-3', !isError && 'text-(color:--pocketr-warning-fg)'],
      icon: 'size-4',
      title: 'text-sm',
    }"
  />
</template>
