<script setup lang="ts">
/**
 * Profile menu: Settings, Appearance (light / dark / system through `useAppTheme`) and Sign out.
 * The trigger is the default slot, so the sidebar profile row and the mobile avatar share one menu.
 */
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { type AppThemeMode, useAppTheme } from '@/composables/useAppTheme'
import { useLogout } from '@/composables/useLogout'

const props = withDefaults(defineProps<{ placement?: 'sidebar' | 'header' }>(), {
  placement: 'sidebar',
})

const { t } = useI18n()
const { selectedMode: themeMode, options } = useAppTheme()
const { logout } = useLogout()
const open = ref(false)

const themeIcons: Record<AppThemeMode, string> = {
  light: 'i-lucide-sun',
  dark: 'i-lucide-moon',
  auto: 'i-lucide-monitor',
}

const themeItems = computed(() =>
  options.map((option) => ({
    value: option.value,
    label: t(option.labelKey),
    icon: themeIcons[option.value],
  })),
)

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
  <UPopover
    v-model:open="open"
    :content="content"
    arrow
    :ui="{
      content: 'w-[13.25rem] rounded-lg bg-default p-2 shadow-md ring-default dark:bg-elevated',
      arrow: 'fill-(--ui-bg) dark:fill-(--ui-bg-elevated) stroke-(--ui-border)',
    }"
  >
    <slot :open="open" />

    <template #content>
      <div class="flex flex-col">
        <UButton
          to="/settings"
          icon="i-lucide-settings"
          :label="t('common.actions.settings')"
          color="neutral"
          variant="ghost"
          size="lg"
          class="gap-3 px-2 text-sm font-normal text-default"
          @click="open = false"
        />
        <USeparator class="my-2" />
        <URadioGroup
          v-model="themeMode"
          :items="themeItems"
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
        <USeparator class="my-2" />
        <UButton
          icon="i-lucide-log-out"
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
