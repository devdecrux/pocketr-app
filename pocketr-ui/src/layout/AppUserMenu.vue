<script setup lang="ts">
/**
 * Profile menu: Settings, Appearance (light / dark / system through `useAppTheme`) and Sign out.
 * The trigger is the default slot, so the sidebar profile row and the mobile avatar share one menu.
 */
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AppThemePicker from '@/layout/AppThemePicker.vue'
import { MENU_POPOVER_UI } from '@/styles/menu'
import { useLogout } from '@/layout/useLogout'
import { APP_ICONS } from '@/utils/appearance/appIcons'

const props = withDefaults(defineProps<{ placement?: 'sidebar' | 'header' }>(), {
  placement: 'sidebar',
})

const { t } = useI18n()
const { logout } = useLogout()
const open = ref(false)

const content = computed(() =>
  props.placement === 'sidebar'
    ? { side: 'top' as const, align: 'start' as const, sideOffset: 10 }
    : { side: 'bottom' as const, align: 'end' as const, sideOffset: 8 },
)

async function signOut(): Promise<void> {
  open.value = false
  await logout()
}
</script>

<template>
  <UPopover v-model:open="open" :content="content" arrow :ui="MENU_POPOVER_UI">
    <slot :open="open" />

    <template #content>
      <div class="flex flex-col">
        <UButton
          to="/settings"
          :icon="APP_ICONS.settings"
          :label="t('common.actions.settings')"
          color="neutral"
          variant="ghost"
          size="lg"
          class="gap-3 px-2 text-sm font-normal text-default"
          @click="open = false"
        />
        <USeparator class="my-2" />
        <AppThemePicker />
        <USeparator class="my-2" />
        <UButton
          :icon="APP_ICONS.signOut"
          :label="t('components.userMenu.signOut')"
          color="neutral"
          variant="ghost"
          size="lg"
          class="gap-3 px-2 text-sm font-normal text-default"
          @click="signOut"
        />
      </div>
    </template>
  </UPopover>
</template>
