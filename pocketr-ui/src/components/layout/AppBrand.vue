<script setup lang="ts">
/**
 * Pocketr mark + wordmark, linking to the dashboard. Uses the approved transparent PNG in both themes.
 * `shell` (default) is the authenticated shell brand; `auth` is the public-page header brand, which
 * keeps one size at every breakpoint.
 */
withDefaults(defineProps<{ size?: 'shell' | 'auth' }>(), { size: 'shell' })
</script>

<template>
  <RouterLink
    v-if="size === 'auth'"
    to="/dashboard"
    class="flex items-center gap-2.5 rounded-md text-[26px] font-bold tracking-tight text-highlighted focus-visible:outline-2 focus-visible:outline-primary"
  >
    <img src="/pocketr-logo-128x128.png" width="48" height="48" alt="" class="size-12 shrink-0" />
    <span>{{ $t('app.name') }}</span>
  </RouterLink>
  <RouterLink
    v-else
    to="/dashboard"
    class="flex items-center gap-2 rounded-md text-2xl font-bold lg:gap-2.5 lg:text-[21.6px] tracking-tight text-highlighted focus-visible:outline-2 focus-visible:outline-primary"
  >
    <!-- 32px mark (64px file) below lg; 40px mark (128px file) in the desktop sidebar. -->
    <picture class="shrink-0">
      <source
        media="(min-width: 1024px)"
        srcset="/pocketr-logo-128x128.png"
        width="40"
        height="40"
      />
      <img src="/pocketr-logo-64x64.png" width="32" height="32" alt="" class="size-8 lg:size-10" />
    </picture>
    <span>{{ $t('app.name') }}</span>
  </RouterLink>
</template>
