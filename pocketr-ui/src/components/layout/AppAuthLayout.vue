<script setup lang="ts">
/**
 * Public (signed-out) page frame shared by the sign-in and registration routes: brand header with
 * the theme toggle, a centred content column (plain on mobile, a bordered card from `sm`) and the
 * Pocketr motto.
 */
import { useI18n } from 'vue-i18n'
import AppBrand from '@/components/layout/AppBrand.vue'
import AppThemeToggle from '@/components/layout/AppThemeToggle.vue'

withDefaults(defineProps<{ motto?: boolean }>(), { motto: true })

const { t } = useI18n()
</script>

<template>
  <div class="flex min-h-dvh flex-col bg-(--pocketr-bg-main)">
    <header
      class="flex h-[78px] shrink-0 items-center justify-between border-b border-default bg-(--pocketr-auth-header-bg) px-[22px] sm:px-6"
    >
      <AppBrand size="auth" />
      <AppThemeToggle />
    </header>

    <main
      class="flex flex-1 flex-col items-center px-[22px] pt-[47px] pb-12 sm:justify-center sm:px-6 sm:pt-12 sm:pb-10"
    >
      <div
        class="pocketr-auth-card w-full sm:w-sm sm:rounded-lg sm:border sm:border-default sm:bg-default sm:px-6 sm:pt-7 sm:pb-6"
      >
        <slot />
      </div>
      <p v-if="motto" class="mt-[65px] text-center text-sm text-muted sm:mt-9 sm:text-[13.5px]">
        {{ t('components.authLayout.motto') }}
      </p>
    </main>
  </div>
</template>

<style scoped>
/* Declared on the card so the slotted page form (sign-in, registration) inherits the same sizes. */
@media (width >= 40rem) {
  .pocketr-auth-card {
    --pocketr-auth-mark: 5rem;
    --pocketr-auth-mark-icon: 2.75rem;
    --pocketr-auth-title: 1.5rem;
    --pocketr-auth-title-leading: 2rem;
    --pocketr-auth-title-gap: 0.25rem;
    --pocketr-auth-subtitle: 1rem;
    --pocketr-auth-subtitle-gap: 0.25rem;
    --pocketr-auth-form-gap: 1.25rem;
    --pocketr-auth-field-gap: 1rem;
    --pocketr-auth-label: 0.875rem;
    --pocketr-auth-label-gap: 0.25rem;
    --pocketr-auth-control-h: 2.5rem;
    --pocketr-auth-control-text: 0.875rem;
    --pocketr-auth-control-icon: 1.125rem;
    --pocketr-auth-control-icon-inset: 0.875rem;
    --pocketr-auth-control-text-inset: 2.75rem;
    --pocketr-auth-button-h: 2.75rem;
    --pocketr-auth-button-text: 1rem;
    --pocketr-auth-link: 0.875rem;
    --pocketr-auth-link-gap: 1.25rem;
  }
}
</style>
