<script setup lang="ts">
/**
 * The page's "new …" action. In the heading it is an action button rendered only from `lg` (nothing
 * below); in the mobile footer (`placement="footer"`) a full-width one. Anything else
 * (`to`, `@click`, `ref`) goes to the underlying `UButton`.
 */
import { ACTION_BUTTON_WIDE_CLASS } from '@/styles/button'
import { APP_ICONS } from '@/utils/appearance/appIcons'
import { useIsDesktop } from '@/utils/useIsDesktop'

// Attributes go to the `UButton` explicitly: the root is absent below `lg` in the heading.
defineOptions({ inheritAttrs: false })

withDefaults(
  defineProps<{
    /** Visible text: the entity only ("Account"); the + icon says "new". */
    label: string
    /** Full name for assistive technology ("New account"), as the + icon is decorative. */
    accessibleLabel: string
    placement?: 'heading' | 'footer'
  }>(),
  { placement: 'heading' },
)

const isDesktop = useIsDesktop()
</script>

<template>
  <UButton
    v-if="placement === 'footer' || isDesktop"
    v-bind="$attrs"
    :block="placement === 'footer'"
    :icon="APP_ICONS.add"
    size="xl"
    :label="label"
    :aria-label="accessibleLabel"
    :class="placement === 'footer' ? undefined : ['shrink-0', ACTION_BUTTON_WIDE_CLASS]"
  />
</template>
