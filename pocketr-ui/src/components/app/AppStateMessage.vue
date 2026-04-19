<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { computed } from 'vue'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<{
    variant?: 'muted' | 'error'
    center?: boolean
    class?: HTMLAttributes['class']
  }>(),
  {
    variant: 'muted',
    center: false,
  },
)

const variantClass = computed(() =>
  props.variant === 'error' ? 'text-[var(--app-feedback-error-fg)]' : 'text-muted-foreground',
)
</script>

<template>
  <div
    :class="
      cn(
        props.center && 'flex flex-1 items-center justify-center',
        'text-sm',
        variantClass,
        props.class,
      )
    "
  >
    <slot />
  </div>
</template>
