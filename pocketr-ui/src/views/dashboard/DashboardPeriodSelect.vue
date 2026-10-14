<script setup lang="ts">
import AppSelect from '@/components/fields/AppSelect.vue'
import { formatPeriodLabel } from '@/views/dashboard/dashboardPeriods'
import { APP_ICONS } from '@/utils/appearance/appIcons'
import { useIsDesktop } from '@/utils/useIsDesktop'

/** The page-level period picker; the label shortens below `lg`. 44px / 16px on phones like every control. */
defineProps<{
  modelValue: string
  items: Array<{ value: string; label: string; description: string }>
}>()

const isDesktop = useIsDesktop()

const emit = defineEmits<{ 'update:modelValue': [period: string] }>()

function onUpdate(period: unknown): void {
  if (typeof period === 'string') emit('update:modelValue', period)
}
</script>

<template>
  <AppSelect
    :model-value="modelValue"
    :items="items"
    :icon="APP_ICONS.date"
    :aria-label="$t('views.dashboard.overview.selectPeriod')"
    variant="toolbar"
    @update:model-value="onUpdate"
  >
    <template #default>
      <span class="truncate">{{
        formatPeriodLabel(modelValue, isDesktop ? 'long' : 'short')
      }}</span>
    </template>
  </AppSelect>
</template>
