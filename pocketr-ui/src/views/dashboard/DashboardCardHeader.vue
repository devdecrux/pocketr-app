<script setup lang="ts">
import { CARD_TITLE_CLASS } from '@/styles/card'

/** Title row of a dashboard card: icon and truncating title on the left, an optional control right. */
defineProps<{ icon: string; title: string }>()
</script>

<template>
  <div class="flex items-center justify-between gap-3">
    <h2 :class="[CARD_TITLE_CLASS, 'flex min-w-0 items-center gap-1.5']">
      <UIcon :name="icon" class="size-4 shrink-0 text-primary" />
      <span class="truncate">{{ title }}</span>
    </h2>
    <slot />
  </div>
</template>
