<script setup lang="ts">
/**
 * Page frame for the Nuxt UI shell: header (desktop title + context controls, mobile menu toggle,
 * brand and profile avatar), mobile context row, scrollable body and optional mobile footer.
 * Context controls render once, in the header on desktop or in the mobile row below `lg`.
 */
import { computed, useSlots } from 'vue'
import { useI18n } from 'vue-i18n'
import { useMediaQuery } from '@vueuse/core'
import AppBrand from '@/components/layout/AppBrand.vue'
import AppModeSelect from '@/components/layout/AppModeSelect.vue'
import AppUserMenu from '@/components/layout/AppUserMenu.vue'
import { useAuthStore } from '@/stores/auth'
import { initialsFromName } from '@/utils/initials'

defineProps<{ title: string }>()

const slots = useSlots()
const { t } = useI18n()
const authStore = useAuthStore()
const isDesktop = useMediaQuery('(min-width: 1024px)')

const userInitials = computed(() =>
  initialsFromName(authStore.user?.firstName, authStore.user?.lastName),
)
</script>

<template>
  <UDashboardPanel
    :ui="{
      root: 'min-w-0',
      body: 'gap-0 px-4 pt-0 pb-4 sm:p-0 sm:px-4 sm:pb-4 lg:px-[30px] lg:pt-[25px] lg:pb-4',
    }"
  >
    <template #header>
      <UDashboardNavbar
        as="header"
        :toggle="{ color: 'neutral', variant: 'ghost', size: 'xl', class: '-ms-2 lg:hidden' }"
        :ui="{
          root: 'relative h-[68px] border-b-0 px-4 lg:h-(--pocketr-header-height) lg:border-b lg:px-[30px]',
          right: 'gap-2.5',
        }"
      >
        <template #left>
          <h1 class="hidden truncate text-xl font-semibold text-highlighted lg:block">
            {{ title }}
          </h1>
          <AppBrand class="absolute left-1/2 -translate-x-1/2 lg:hidden" />
        </template>

        <template #right>
          <template v-if="isDesktop">
            <AppModeSelect class="w-auto max-w-64 min-w-[137px]" />
            <slot name="context" />
          </template>
          <AppUserMenu v-else-if="authStore.user" placement="header">
            <UButton
              color="neutral"
              variant="ghost"
              :aria-label="t('components.userMenu.openProfileMenu')"
              class="-me-1 rounded-full p-0"
            >
              <UAvatar
                :src="authStore.user.avatar ?? undefined"
                :text="userInitials"
                :alt="authStore.displayName"
                size="lg"
                :ui="{
                  root: 'size-9 bg-(--pocketr-avatar-bg)',
                  fallback: 'text-sm font-normal text-highlighted',
                }"
              />
            </UButton>
          </AppUserMenu>
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <main class="flex flex-1 flex-col gap-4">
        <div
          v-if="!isDesktop"
          class="grid gap-2.5"
          :class="
            slots.context ? 'grid-cols-[minmax(max-content,1fr)_minmax(0,1fr)]' : 'grid-cols-1'
          "
        >
          <AppModeSelect />
          <slot name="context" />
        </div>
        <slot />
      </main>
    </template>

    <template v-if="slots.footer && !isDesktop" #footer>
      <div
        class="border-t border-default bg-(--pocketr-bg-main) px-4 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]"
      >
        <slot name="footer" />
      </div>
    </template>
  </UDashboardPanel>
</template>
