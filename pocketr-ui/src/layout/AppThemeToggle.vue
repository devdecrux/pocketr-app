<script setup lang="ts">
/**
 * Signed-out theme control: an icon button showing the current mode that opens the same
 * `AppThemePicker` as the profile menu, in the same popover.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import AppThemePicker from '@/layout/AppThemePicker.vue'
import { MENU_POPOVER_UI } from '@/styles/menu'
import { APP_THEME_ICONS, useAppTheme } from '@/layout/useAppTheme'

const { t } = useI18n()
const { selectedMode } = useAppTheme()

const label = computed(() => t('components.userMenu.appearance'))
</script>

<template>
  <UPopover :content="{ side: 'bottom', align: 'end', sideOffset: 8 }" arrow :ui="MENU_POPOVER_UI">
    <UTooltip :text="label">
      <UButton
        :icon="APP_THEME_ICONS[selectedMode]"
        color="neutral"
        variant="ghost"
        :aria-label="label"
        class="p-1 text-highlighted"
        :ui="{ leadingIcon: 'size-7' }"
      />
    </UTooltip>

    <template #content>
      <AppThemePicker />
    </template>
  </UPopover>
</template>
