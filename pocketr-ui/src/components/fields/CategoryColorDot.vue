<script setup lang="ts">
/** Decorative category colour marker; a category without a colour shows a dashed outline. */
defineProps<{ color?: string | null }>()
</script>

<template>
  <span
    aria-hidden="true"
    class="inline-block size-3.5 shrink-0 rounded-full"
    :class="color ? undefined : 'border border-dashed border-(--ui-text-muted)'"
    :style="color ? { backgroundColor: color } : undefined"
  />
</template>
