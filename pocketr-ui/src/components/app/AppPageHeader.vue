<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

const props = defineProps<{
  title: string
  subtitle?: string
  class?: HTMLAttributes['class']
}>()
</script>

<template>
  <header :class="cn('grid gap-4', props.class)">
    <h1 class="text-2xl font-semibold">{{ props.title }}</h1>
    <p v-if="props.subtitle" class="text-sm text-muted-foreground">{{ props.subtitle }}</p>
  </header>
</template>
