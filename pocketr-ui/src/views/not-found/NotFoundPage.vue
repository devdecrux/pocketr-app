<script setup lang="ts">
import { useIsDesktop } from '@/utils/useIsDesktop'
import AppContent from '@/layout/AppContent.vue'
import LostFigureAnimation from '@/views/not-found/LostFigureAnimation.vue'

// The desktop header already renders the page title as the h1, so the message steps down to h2.
const isDesktop = useIsDesktop()
</script>

<template>
  <AppContent :title="$t('views.notFound.title')">
    <section
      class="flex flex-1 flex-col items-center justify-center pt-8 pb-24 text-center lg:pt-0 lg:pb-20"
    >
      <LostFigureAnimation class="mb-3" />
      <p class="text-5xl leading-none font-semibold text-primary">404</p>
      <component
        :is="isDesktop ? 'h2' : 'h1'"
        class="mt-5 text-2xl font-bold text-highlighted lg:text-[28px]"
      >
        {{ $t('views.notFound.heading') }}
      </component>
      <p class="mt-2 max-w-76 text-base text-muted lg:max-w-86 lg:text-lg">
        {{ $t('views.notFound.description') }}
      </p>
      <UButton
        to="/dashboard"
        icon="i-lucide-arrow-left"
        :label="$t('common.actions.backToDashboard')"
        size="xl"
        block
        class="mt-7 lg:w-auto lg:min-w-68 lg:px-8"
      />
    </section>
  </AppContent>
</template>
