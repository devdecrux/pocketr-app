<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { computed } from 'vue'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<{
    gap?: '2' | '4'
    class?: HTMLAttributes['class']
  }>(),
  {
    gap: '4',
  },
)

const gapClass = computed(() => (props.gap === '2' ? 'gap-2' : 'gap-4'))
</script>

<template>
  <div :class="cn('grid py-4', gapClass, props.class)">
    <slot />
  </div>
</template>
