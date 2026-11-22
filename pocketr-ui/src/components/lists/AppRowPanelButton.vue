<script setup lang="ts">
/**
 * Full-width action button in the expanded panel of a phone row (Edit, Delete, Archive). A
 * `destructive` one is red, a `warning` one amber. `loading`, `disabled` and `@click` go to the
 * `UButton`. A disabled one is faded well below Nuxt UI's default so it reads as unavailable next to
 * an active one.
 */
import { computed } from 'vue'

const props = defineProps<{
  icon: string
  /** Visible text. */
  label: string
  /** Accessible name; it names the row too. */
  accessibleLabel: string
  destructive?: boolean
  warning?: boolean
}>()

const color = computed(() => {
  if (props.destructive) return 'error'
  if (props.warning) return 'warning'
  return 'neutral'
})
</script>

<template>
  <UButton
    block
    :color="color"
    variant="soft"
    size="xl"
    :icon="icon"
    :label="label"
    :aria-label="accessibleLabel"
    class="disabled:opacity-40 aria-disabled:opacity-40"
    :class="warning && 'text-(--pocketr-warning-fg)'"
  />
</template>
