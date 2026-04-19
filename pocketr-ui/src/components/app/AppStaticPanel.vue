<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

const props = defineProps<{
  class?: HTMLAttributes['class']
}>()
</script>

<template>
  <section
    :class="
      cn(
        'rounded-2xl border border-[color:var(--app-static-panel-border)] bg-[var(--app-static-panel-bg)] p-6 shadow-sm',
        props.class,
      )
    "
  >
    <slot />
  </section>
</template>
