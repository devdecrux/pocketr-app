<script setup lang="ts">
/**
 * The theme choice: Light / Dark / System as one radio card group. The only place where the
 * theme is picked (profile menu and signed-out header both render it), over `useAppTheme`, the
 * single theme source of truth.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { APP_THEME_ICONS, useAppTheme } from '@/layout/useAppTheme'

const { t } = useI18n()
const { selectedMode, options } = useAppTheme()

const items = computed(() =>
  options.map((option) => ({
    value: option.value,
    label: t(option.labelKey),
    icon: APP_THEME_ICONS[option.value],
  })),
)
</script>

<template>
  <URadioGroup
    v-model="selectedMode"
    :items="items"
    :legend="t('components.userMenu.appearance')"
    variant="card"
    orientation="horizontal"
    indicator="hidden"
    size="sm"
    :ui="{
      legend: 'mb-1.5 px-1 text-xs font-normal text-default',
      fieldset: 'flex-nowrap gap-1',
      item: 'flex-1 justify-center rounded-md p-2 has-data-[state=checked]:border-primary has-data-[state=checked]:bg-(--pocketr-choice-active) has-data-[state=checked]:**:text-primary',
      label: 'text-xs font-normal text-muted',
      icon: 'size-4 text-default',
    }"
  />
</template>
