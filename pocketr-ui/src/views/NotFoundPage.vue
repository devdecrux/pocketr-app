<script setup lang="ts">
import { AppStaticPanel } from '@/components/app'
</script>

<template>
  <AppStaticPanel>
    <h1 class="text-2xl font-semibold text-[var(--app-static-title-fg)]">Page not found</h1>
    <p class="mt-2 text-sm text-[var(--app-static-description-fg)]">
      The page you requested does not exist.
    </p>
    <RouterLink to="/dashboard" class="app-button-link mt-4"> Back to dashboard </RouterLink>
  </AppStaticPanel>
</template>
