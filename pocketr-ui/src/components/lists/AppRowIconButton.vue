<script setup lang="ts">
/**
 * Icon button of a table row's actions cell, with a tooltip. A `destructive` one (delete, archive)
 * turns red on hover. `loading`, `disabled` and `@click` go to the `UButton`.
 */
defineOptions({ inheritAttrs: false })

defineProps<{
  icon: string
  /** Tooltip text. */
  tooltip: string
  /** Accessible name; it names the row too. */
  label: string
  destructive?: boolean
}>()
</script>

<template>
  <UTooltip :text="tooltip">
    <UButton
      v-bind="$attrs"
      color="neutral"
      variant="ghost"
      size="xl"
      :icon="icon"
      :aria-label="label"
      class="text-default"
      :class="destructive && 'hover:bg-error/10 hover:text-error dark:hover:text-error-400'"
    />
  </UTooltip>
</template>
