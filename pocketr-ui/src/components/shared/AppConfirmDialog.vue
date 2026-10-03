<script setup lang="ts">
/**
 * Confirmation for destructive actions, on the shared overlay shell: a modal on desktop, a bottom
 * drawer below `lg`, closed through X, Cancel or Escape (and swipe on mobile), never a backdrop tap.
 */
import AppFormOverlay from '@/components/shared/AppFormOverlay.vue'

const open = defineModel<boolean>('open', { required: true })

defineProps<{
  title: string
  description: string
  confirmLabel: string
  loading?: boolean
  returnFocusTo?: HTMLElement | null
}>()

const emit = defineEmits<{ confirm: [] }>()
</script>

<template>
  <AppFormOverlay
    v-model:open="open"
    submit-color="error"
    :title="title"
    :description="description"
    :submit-label="confirmLabel"
    :loading="loading"
    :return-focus-to="returnFocusTo"
    @submit="emit('confirm')"
  />
</template>
