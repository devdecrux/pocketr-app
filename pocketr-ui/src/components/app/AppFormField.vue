<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'
import { Label } from '@/components/ui/label'

const props = defineProps<{
  controlId?: string
  label: string
  class?: HTMLAttributes['class']
  labelClass?: HTMLAttributes['class']
}>()
</script>

<template>
  <div :class="cn('grid gap-2', props.class)">
    <Label v-if="props.controlId" :for="props.controlId" :class="props.labelClass">
      {{ props.label }}
    </Label>
    <Label v-else :class="props.labelClass">{{ props.label }}</Label>
    <slot />
  </div>
</template>
