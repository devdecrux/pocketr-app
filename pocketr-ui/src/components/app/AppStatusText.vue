<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { computed } from 'vue'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<{
    variant?: 'error' | 'success' | 'muted'
    size?: 'xs' | 'sm'
    class?: HTMLAttributes['class']
  }>(),
  {
    variant: 'error',
    size: 'sm',
  },
)

const variantClass = computed(() => {
  if (props.variant === 'success') return 'text-[var(--app-feedback-success-fg)]'
  if (props.variant === 'muted') return 'text-muted-foreground'
  return 'text-[var(--app-feedback-error-fg)]'
})
</script>

<template>
  <p :class="cn(props.size === 'xs' ? 'text-xs' : 'text-sm', variantClass, props.class)">
    <slot />
  </p>
</template>
