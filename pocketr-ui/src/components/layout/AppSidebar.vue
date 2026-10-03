<script setup lang="ts">
/**
 * Authenticated sidebar: brand, page navigation and profile menu. Collapsible on desktop
 * (Cmd/Ctrl+B) and a left slide-over below `lg`.
 */
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { useEventListener, useMediaQuery } from '@vueuse/core'
import type { NavigationMenuItem } from '@nuxt/ui'
import AppBrand from '@/components/layout/AppBrand.vue'
import AppUserMenu from '@/components/layout/AppUserMenu.vue'
import { useHouseholdSettingsPath } from '@/composables/useHouseholdSettingsPath'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { useModeStore } from '@/stores/mode'
import { initialsFromName } from '@/utils/initials'
import { APP_ICONS } from '@/utils/appIcons'

/** Keyboard shortcut (with Cmd/Ctrl) that collapses and expands the desktop sidebar. */
const SIDEBAR_KEYBOARD_SHORTCUT = 'b'

const { t } = useI18n()
const route = useRoute()
const authStore = useAuthStore()
const householdStore = useHouseholdStore()
const modeStore = useModeStore()
const householdSettingsPath = useHouseholdSettingsPath()
const isDesktop = useMediaQuery('(min-width: 1024px)')

const mobileOpen = ref(false)
const collapsed = ref(false)

/** Settings is reached from the profile menu, so the profile row (not a nav row) marks it. */
const isSettingsRoute = computed(() => route.path === '/settings')

const userInitials = computed(() =>
  initialsFromName(authStore.user?.firstName, authStore.user?.lastName),
)

const navigationItems = computed<NavigationMenuItem[]>(() => [
  {
    label: t('components.sidebar.routes.dashboard'),
    icon: APP_ICONS.dashboard,
    to: '/dashboard',
    exact: true,
  },
  {
    label: t('components.sidebar.routes.transactions'),
    icon: APP_ICONS.transactions,
    to: '/transactions',
    exact: true,
  },
  {
    label: t('components.sidebar.routes.accounts'),
    icon: APP_ICONS.accounts,
    to: '/accounts',
    exact: true,
  },
  {
    label: t('components.sidebar.routes.categories'),
    icon: APP_ICONS.categories,
    to: '/categories',
    exact: true,
  },
  // Only in household mode, and hidden until the memberships have loaded.
  ...(modeStore.isHousehold && householdStore.households.length
    ? [
        {
          label: t('components.sidebar.household'),
          icon: APP_ICONS.household,
          // OWNER/ADMIN in household mode manage the active household; everyone else reaches the
          // membership entry point on the settings page.
          to: householdSettingsPath.value ?? '/settings',
          // On the settings page the profile row marks the current page instead; `exact` would
          // still set aria-current on this row, so it is dropped there too.
          exact: !isSettingsRoute.value,
          active: isSettingsRoute.value ? false : undefined,
        },
      ]
    : []),
])

useEventListener('keydown', (event: KeyboardEvent) => {
  if (event.key === SIDEBAR_KEYBOARD_SHORTCUT && (event.metaKey || event.ctrlKey)) {
    event.preventDefault()
    if (isDesktop.value) {
      collapsed.value = !collapsed.value
    } else {
      mobileOpen.value = !mobileOpen.value
    }
  }
})
</script>

<template>
  <UDashboardSidebar
    v-model:open="mobileOpen"
    v-model:collapsed="collapsed"
    collapsible
    :default-size="232"
    :min-size="232"
    :max-size="232"
    :collapsed-size="72"
    :toggle="{ color: 'neutral', variant: 'ghost', size: 'lg' }"
    :ui="{
      root: 'bg-(--pocketr-bg-shell) border-default',
      content: 'max-w-72 bg-(--pocketr-bg-shell)',
      header:
        'mx-3 h-[78px] shrink-0 lg:h-(--pocketr-header-height) gap-2 border-b border-default px-3',
      body: 'gap-0 px-3 py-3.5',
      footer: 'mx-3 border-t border-default px-0 py-3',
    }"
  >
    <template #header="{ collapsed: isCollapsed }">
      <AppBrand v-if="!isCollapsed" />
      <RouterLink v-else to="/dashboard" class="mx-auto rounded-md">
        <img
          src="/pocketr-logo-128x128.png"
          width="40"
          height="40"
          :alt="t('app.name')"
          class="size-10"
        />
      </RouterLink>
    </template>

    <template #default="{ collapsed: isCollapsed }">
      <UNavigationMenu
        :items="navigationItems"
        :collapsed="isCollapsed"
        orientation="vertical"
        :aria-label="t('components.sidebar.mainNavigation')"
        class="[--ui-bg-elevated:var(--pocketr-nav-active)] [--ui-text-muted:var(--ui-text)]"
        :ui="{
          list: 'flex flex-col gap-[5px]',
          link: 'h-[50px] gap-6 px-[17px] text-[15px] font-normal before:rounded-lg',
          linkLeadingIcon: 'size-[22px]',
        }"
      />
    </template>

    <template v-if="authStore.user" #footer="{ collapsed: isCollapsed }">
      <AppUserMenu>
        <UButton
          color="neutral"
          variant="ghost"
          :aria-label="isCollapsed ? t('components.userMenu.openProfileMenu') : undefined"
          class="w-full gap-2.5 rounded-lg px-0.5 py-1.5 text-start"
          :class="[isCollapsed && 'justify-center', isSettingsRoute && 'bg-(--pocketr-nav-active)']"
        >
          <UAvatar
            :src="authStore.user.avatar ?? undefined"
            :text="userInitials"
            :alt="authStore.displayName"
            size="xl"
            :ui="{
              root: 'size-11 bg-(--pocketr-avatar-bg)',
              fallback: 'text-base font-normal text-highlighted',
            }"
          />
          <template v-if="!isCollapsed">
            <span class="grid min-w-0 flex-1 leading-tight">
              <span class="truncate text-sm font-medium text-highlighted">{{
                authStore.displayName
              }}</span>
              <span class="truncate text-[13px] text-muted">{{ authStore.user.email }}</span>
            </span>
            <UIcon
              name="i-lucide-chevron-right"
              class="size-4 shrink-0"
              :class="isSettingsRoute ? 'text-primary' : 'text-default'"
            />
          </template>
        </UButton>
      </AppUserMenu>
    </template>
  </UDashboardSidebar>
</template>
