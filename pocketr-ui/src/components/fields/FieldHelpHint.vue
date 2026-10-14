<script setup lang="ts">
/**
 * Info button for the `hint` slot of a `UFormField`: a tooltip next to a pointer and a tap-to-open
 * popover on touch screens (tooltips do not open there). `label` names the button. It stays
 * clickable but is out of the Tab order (`tabindex="-1"`) so Tab moves field to field; callers must
 * give the field the same text for keyboard and screen-reader users (e.g. an `sr-only` copy that
 * the input points `aria-describedby` at, as `RolloverDayField` does).
 */
import { APP_ICONS } from '@/utils/appearance/appIcons'
import { useIsDesktop } from '@/utils/useIsDesktop'

defineProps<{ text: string; label: string }>()

const isDesktop = useIsDesktop()
</script>

<template>
  <UTooltip
    v-if="isDesktop"
    :text="text"
    :content="{ side: 'top' }"
    :ui="{ content: 'h-auto max-w-60 py-1.5', text: 'whitespace-normal' }"
  >
    <UButton
      :icon="APP_ICONS.info"
      color="neutral"
      variant="ghost"
      size="xs"
      :aria-label="label"
      tabindex="-1"
      class="-my-1"
    />
  </UTooltip>
  <UPopover v-else :content="{ side: 'top' }" arrow>
    <UButton
      :icon="APP_ICONS.info"
      color="neutral"
      variant="ghost"
      size="xs"
      :aria-label="label"
      tabindex="-1"
      class="-my-1"
    />
    <template #content>
      <p class="max-w-64 p-3 text-xs text-default">{{ text }}</p>
    </template>
  </UPopover>
</template>
