<script setup lang="ts">
/** Individual / household view-mode selector ("Personal" context selector in the shell header). */
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { SelectItem } from '@nuxt/ui'
import { useHouseholdStore } from '@/stores/household'
import { INDIVIDUAL_MODE_VALUE, useViewModeSelection } from '@/composables/useViewModeSelection'
import { SELECT_CONTENT_CLASS } from '@/components/forms/fieldStyles'
import { APP_ICONS } from '@/utils/appIcons'

const { t } = useI18n()
const householdStore = useHouseholdStore()
const { currentValue, selectMode, householdValue } = useViewModeSelection()

// Refresh the household list when the selector mounts.
onMounted(() => householdStore.loadHouseholds())

const personalLabel = computed(() => t('components.viewMode.personal'))
const householdLabel = computed(() => t('components.viewMode.household'))
const isHouseholdSelected = computed(() => currentValue.value !== INDIVIDUAL_MODE_VALUE)

// The trigger names only the mode, so it never truncates. A single household is listed as
// "Household"; several are grouped under that label and listed by name to stay distinguishable.
const items = computed<SelectItem[] | SelectItem[][]>(() => {
  const personal = {
    value: INDIVIDUAL_MODE_VALUE,
    label: personalLabel.value,
    icon: APP_ICONS.personal,
  }
  const households = householdStore.households.map((household) => ({
    value: householdValue(household.id),
    label: household.name,
    icon: APP_ICONS.household,
  }))
  if (households.length === 0) return [personal]
  if (households.length === 1) return [personal, { ...households[0]!, label: householdLabel.value }]
  return [[personal], [{ type: 'label', label: householdLabel.value }, ...households]]
})
</script>

<template>
  <USelect
    :model-value="currentValue"
    :items="items"
    :icon="isHouseholdSelected ? APP_ICONS.household : APP_ICONS.personal"
    :aria-label="t('components.viewMode.selectMode')"
    color="neutral"
    size="lg"
    :ui="{
      base: 'rounded-lg bg-default text-sm ring-default max-lg:h-11 max-lg:text-base',
      leadingIcon: 'text-default',
      trailingIcon: 'text-default size-4',
      content: SELECT_CONTENT_CLASS,
    }"
    @update:model-value="selectMode"
  >
    <template #default>
      {{ isHouseholdSelected ? householdLabel : personalLabel }}
    </template>
  </USelect>
</template>
