<script setup lang="ts">
/**
 * Page frame for the Nuxt UI shell: header (desktop title + context controls, mobile menu toggle,
 * brand and profile avatar), mobile context row, scrollable body and optional mobile footer.
 * Context controls render once, in the header on desktop or in the mobile row below `lg`.
 */
import { computed, useSlots } from 'vue'
import { useI18n } from 'vue-i18n'
import { useIsDesktop } from '@/utils/useIsDesktop'
import AppLogo from '@/layout/AppLogo.vue'
import AppModeSelect from '@/layout/AppModeSelect.vue'
import AppUserMenu from '@/layout/AppUserMenu.vue'
import { useAuthStore } from '@/stores/auth'
import { initialsFromName } from '@/utils/initials'

defineProps<{ title: string }>()

const slots = useSlots()
const { t } = useI18n()
const authStore = useAuthStore()
const isDesktop = useIsDesktop()

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
        :toggle="
          isDesktop
            ? false
            : {
                color: 'neutral',
                variant: 'ghost',
                size: 'lg',
                class: '-ms-2 size-10',
                ui: { leadingIcon: 'size-6' },
              }
        "
        :ui="{
          root: 'relative h-[68px] border-b-0 px-4 lg:h-(--pocketr-header-height) lg:border-b lg:px-[30px]',
          right: 'gap-2.5',
        }"
      >
        <template #left>
          <h1 v-if="isDesktop" class="truncate text-xl font-semibold text-highlighted">
            {{ title }}
          </h1>
          <AppLogo v-else class="absolute left-1/2 -translate-x-1/2" />
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
              />
            </UButton>
          </AppUserMenu>
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <main class="flex flex-1 flex-col gap-4">
        <!-- The mode select and the page's context controls take one full-width row each. -->
        <div v-if="!isDesktop" class="flex flex-col gap-2.5 *:w-auto">
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
