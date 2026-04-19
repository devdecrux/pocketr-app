<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { computed } from 'vue'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<{
    variant?: 'info' | 'warning'
    class?: HTMLAttributes['class']
  }>(),
  {
    variant: 'info',
  },
)

const variantClass = computed(() =>
  props.variant === 'warning'
    ? 'border-[color:var(--app-notice-warning-border)] bg-[var(--app-notice-warning-bg)] text-[var(--app-notice-warning-fg)]'
    : 'border-[color:var(--app-notice-info-border)] bg-[var(--app-notice-info-bg)] text-[var(--app-notice-info-fg)]',
)
</script>

<template>
  <div :class="cn('rounded-md border px-3 py-2 text-xs', variantClass, props.class)">
    <slot />
  </div>
</template>
