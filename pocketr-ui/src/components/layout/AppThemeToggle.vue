<script setup lang="ts">
/**
 * Public-page theme control: one icon button that cycles light → dark → system through
 * `useAppTheme` (the single theme source of truth). The icon shows the current mode.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { type AppThemeMode, useAppTheme } from '@/composables/useAppTheme'

const { t } = useI18n()
const { selectedMode, options } = useAppTheme()

const themeIcons: Record<AppThemeMode, string> = {
  light: 'i-lucide-sun',
  dark: 'i-lucide-moon',
  auto: 'i-lucide-monitor',
}

/** Names the current mode and the next action, e.g. "Theme: Light. Switch to Dark". */
const label = computed(() => t(`components.themeToggle.${selectedMode.value}`))

function cycleTheme(): void {
  const index = options.findIndex((option) => option.value === selectedMode.value)
  selectedMode.value = options[(index + 1) % options.length]!.value
}
</script>

<template>
  <UTooltip :text="label">
    <UButton
      :icon="themeIcons[selectedMode]"
      color="neutral"
      variant="ghost"
      :aria-label="label"
      class="p-1 text-highlighted"
      :ui="{ leadingIcon: 'size-7' }"
      @click="cycleTheme"
    />
  </UTooltip>
</template>
