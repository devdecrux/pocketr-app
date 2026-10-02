<script setup lang="ts">
/**
 * Form-level message shared by every migrated form (sign in, registration, settings).
 * Errors are announced assertively (`role="alert"`); warnings, successes and notices politely (`role="status"`).
 */
import { computed } from 'vue'

type FormMessageTone = 'error' | 'warning' | 'success' | 'info'

const props = defineProps<{ tone: FormMessageTone; message: string }>()

const toneIcons: Record<FormMessageTone, string> = {
  error: 'i-lucide-circle-alert',
  warning: 'i-lucide-triangle-alert',
  success: 'i-lucide-circle-check',
  info: 'i-lucide-info',
}

const toneText: Record<FormMessageTone, string | false> = {
  error: false,
  warning: 'text-(color:--pocketr-warning-fg)',
  success: 'text-(color:--pocketr-success-fg)',
  info: false,
}

const isError = computed(() => props.tone === 'error')
</script>

<template>
  <UAlert
    :role="isError ? 'alert' : 'status'"
    :color="props.tone"
    variant="subtle"
    :icon="toneIcons[props.tone]"
    :title="props.message"
    :ui="{
      root: ['items-center gap-2.5 px-3.5 py-3', toneText[props.tone]],
      icon: 'size-4',
      title: 'text-sm',
    }"
  />
</template>
