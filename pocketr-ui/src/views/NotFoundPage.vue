<script setup lang="ts">
import { AppStaticPanel } from '@/components/app'
</script>

<template>
  <AppStaticPanel>
    <h1 class="text-2xl font-semibold text-[var(--app-static-title-fg)]">
      {{ $t('views.notFound.title') }}
    </h1>
    <p class="mt-2 text-sm text-[var(--app-static-description-fg)]">
      {{ $t('views.notFound.description') }}
    </p>
    <RouterLink to="/dashboard" class="app-button-link mt-4">
      {{ $t('common.actions.backToDashboard') }}
    </RouterLink>
  </AppStaticPanel>
</template>
