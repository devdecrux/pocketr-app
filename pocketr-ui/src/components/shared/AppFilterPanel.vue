<script setup lang="ts">
/**
 * Panel of a list page's filter controls, shown while the Filters button is open, as its own
 * full-width row directly under the page heading: one wrapping, left-aligned row on desktop,
 * stacked below `lg`. The controls come in the default slot; the Clear action appears at the end
 * while any filter is in use (icon-only with a tooltip on desktop, labelled below `lg`); since it
 * then disappears, focus returns to the Filters button that controls the panel.
 */
import { useMediaQuery } from '@vueuse/core'
import { nextTick } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  id: string
  /** Number of filters that are not at their default. */
  count: number
}>()

const emit = defineEmits<{ clear: [] }>()

defineSlots<{ default?: () => unknown }>()

const { t } = useI18n()
const isDesktop = useMediaQuery('(min-width: 1024px)')

// The Clear action disappears once nothing is filtered, so focus moves to the Filters button.
async function clear(): Promise<void> {
  emit('clear')
  await nextTick()
  document.querySelector<HTMLElement>(`[aria-controls="${props.id}"]`)?.focus()
}
</script>

<template>
  <div
    :id="id"
    role="group"
    :aria-label="t('common.filters.title')"
    class="grid gap-2.5 lg:flex lg:flex-wrap lg:items-center"
  >
    <slot />
    <UTooltip v-if="count > 0" :text="t('common.filters.clear')">
      <UButton
        type="button"
        color="neutral"
        variant="ghost"
        size="md"
        icon="i-lucide-x"
        :label="isDesktop ? undefined : t('common.filters.clear')"
        :aria-label="t('common.filters.clear')"
        class="shrink-0 rounded-lg text-default max-lg:justify-self-start"
        @click="clear"
      />
    </UTooltip>
  </div>
</template>
