<script setup lang="ts">
/** Individual / household view-mode selector ("Personal" context selector in the shell header). */
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useHouseholdStore } from '@/stores/household'
import { INDIVIDUAL_MODE_VALUE, useViewModeSelection } from '@/composables/useViewModeSelection'

const { t } = useI18n()
const householdStore = useHouseholdStore()
const { currentValue, selectMode, householdValue } = useViewModeSelection()

// Refresh the household list when the selector mounts, like the legacy `ModeSwitcher`.
onMounted(() => householdStore.loadHouseholds())

const items = computed(() => [
  { value: INDIVIDUAL_MODE_VALUE, label: t('components.viewMode.personal'), icon: 'i-lucide-user' },
  ...householdStore.households.map((household) => ({
    value: householdValue(household.id),
    label: household.name,
    icon: 'i-lucide-users',
  })),
])

const selectedIcon = computed(
  () => items.value.find((item) => item.value === currentValue.value)?.icon ?? 'i-lucide-user',
)
</script>

<template>
  <USelect
    :model-value="currentValue"
    :items="items"
    :icon="selectedIcon"
    :aria-label="t('components.viewMode.selectMode')"
    color="neutral"
    size="lg"
    :ui="{
      base: 'rounded-lg bg-default text-sm ring-default',
      leadingIcon: 'text-default',
      trailingIcon: 'text-default size-4',
      content: 'min-w-(--reka-select-trigger-width)',
    }"
    @update:model-value="selectMode"
  />
</template>
