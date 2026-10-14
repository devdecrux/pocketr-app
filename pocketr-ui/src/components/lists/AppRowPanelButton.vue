<script setup lang="ts">
/**
 * Full-width action button in the expanded panel of a phone row (Edit, Delete, Archive). A
 * `destructive` one is red. `loading`, `disabled` and `@click` go to the `UButton`.
 */
defineProps<{
  icon: string
  /** Visible text. */
  label: string
  /** Accessible name; it names the row too. */
  accessibleLabel: string
  destructive?: boolean
}>()
</script>

<template>
  <UButton
    block
    :color="destructive ? 'error' : 'neutral'"
    variant="soft"
    size="xl"
    :icon="icon"
    :label="label"
    :aria-label="accessibleLabel"
  />
</template>
