<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

const props = defineProps<{
  as?: 'div' | 'li'
  class?: HTMLAttributes['class']
}>()
</script>

<template>
  <component
    :is="props.as ?? 'div'"
    :class="
      cn('flex items-center justify-between rounded-md border border-border px-3 py-2', props.class)
    "
  >
    <slot />
    <slot name="actions" />
  </component>
</template>
