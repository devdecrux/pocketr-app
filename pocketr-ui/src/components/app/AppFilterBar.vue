<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { computed } from 'vue'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<{
    align?: 'center' | 'end'
    class?: HTMLAttributes['class']
  }>(),
  {
    align: 'end',
  },
)

const alignClass = computed(() => (props.align === 'center' ? 'items-center' : 'items-end'))
</script>

<template>
  <div :class="cn('mb-4 flex flex-wrap gap-3', alignClass, props.class)">
    <slot />
  </div>
</template>
