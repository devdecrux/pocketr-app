<script setup lang="ts">
/**
 * The Filters button of a list page: outline button with the sliders icon, labelled on desktop and
 * icon-only below `lg`, with a badge counting the filters in use. It opens and closes the filter
 * panel (`AppFilterPanel`) whose id it controls; closing the panel keeps the filters applied.
 */
import { useIsDesktop } from '@/utils/useIsDesktop'
import { useI18n } from 'vue-i18n'
import { APP_ICONS } from '@/utils/appearance/appIcons'

defineProps<{
  /** Id of the filter panel this button opens. */
  panelId: string
  /** Number of filters that are not at their default. */
  count: number
}>()

const open = defineModel<boolean>('open', { required: true })

const { t } = useI18n()
const isDesktop = useIsDesktop()
</script>

<template>
  <UButton
    type="button"
    color="neutral"
    variant="outline"
    size="xl"
    :icon="APP_ICONS.filters"
    :label="isDesktop ? t('common.filters.button') : undefined"
    :aria-label="t('common.filters.button')"
    :aria-expanded="open"
    :aria-controls="panelId"
    class="px-3 lg:px-4"
    @click="open = !open"
  >
    <template v-if="count > 0" #trailing>
      <UBadge :label="String(count)" color="primary" variant="subtle" size="sm" />
    </template>
  </UButton>
</template>
