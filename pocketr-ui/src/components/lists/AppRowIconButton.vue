<script setup lang="ts">
/**
 * Icon button of a table row's actions cell, with a tooltip. A `destructive` one (delete, archive)
 * turns red on hover, a `warning` one (edit) amber. `loading`, `disabled` and `@click` go to the
 * `UButton`. A disabled one is faded well below Nuxt UI's default so it reads as unavailable next to
 * an active one.
 */
defineOptions({ inheritAttrs: false })

defineProps<{
  icon: string
  /** Tooltip text. */
  tooltip: string
  /** Accessible name; it names the row too. */
  label: string
  destructive?: boolean
  warning?: boolean
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
      class="text-default disabled:opacity-40 aria-disabled:opacity-40"
      :class="[
        destructive && 'hover:bg-error/10 hover:text-error dark:hover:text-error-400',
        warning && 'enabled:hover:bg-warning/10 enabled:hover:text-(--pocketr-warning-fg)',
      ]"
    />
  </UTooltip>
</template>
