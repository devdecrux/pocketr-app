<script setup lang="ts">
/**
 * Row of a mobile list that expands in place: a full-width button with the row content and a
 * chevron (left when closed, down when open) and, below it, a details panel that holds the row
 * actions. Used by the Transactions, Accounts and Categories lists, which wrap each row in an `li`.
 * The page owns the expanded state and toggles it on `toggle`.
 */
import { computed } from 'vue'
import { APP_ICONS } from '@/utils/appIcons'

const props = defineProps<{
  /** Unique id of the row; the panel is `<id>` and the button controls it. */
  panelId: string
  expanded: boolean
}>()

const emit = defineEmits<{ toggle: [] }>()

defineSlots<{ default?: () => unknown; details?: () => unknown }>()

const chevron = computed(() => (props.expanded ? APP_ICONS.expanded : APP_ICONS.collapsed))
</script>

<template>
  <button
    type="button"
    class="flex w-full items-center gap-3 px-3 py-2.5 text-start outline-primary/25 focus-visible:outline-3 focus-visible:-outline-offset-3"
    :aria-expanded="expanded"
    :aria-controls="panelId"
    @click="emit('toggle')"
  >
    <slot />
    <UIcon :name="chevron" class="size-5 shrink-0 text-default" aria-hidden="true" />
  </button>
  <div v-if="expanded" :id="panelId" class="flex flex-col gap-2.5 px-3 pt-2 pb-3">
    <slot name="details" />
  </div>
</template>
