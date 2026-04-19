<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'
import { CardHeader, CardTitle } from '@/components/ui/card'

const props = defineProps<{
  title: string
  class?: HTMLAttributes['class']
  titleClass?: HTMLAttributes['class']
}>()
</script>

<template>
  <CardHeader :class="cn('flex flex-row items-center justify-between', props.class)">
    <div class="flex items-center gap-2">
      <slot name="leading" />
      <CardTitle :class="props.titleClass">{{ props.title }}</CardTitle>
    </div>
    <slot />
  </CardHeader>
</template>
