<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import {
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

const props = defineProps<{
  title: string
  description?: string
  class?: HTMLAttributes['class']
}>()
</script>

<template>
  <DialogContent :class="props.class">
    <DialogHeader>
      <DialogTitle>{{ props.title }}</DialogTitle>
      <DialogDescription v-if="props.description">{{ props.description }}</DialogDescription>
    </DialogHeader>

    <slot />

    <DialogFooter v-if="$slots.footer">
      <slot name="footer" />
    </DialogFooter>
  </DialogContent>
</template>
