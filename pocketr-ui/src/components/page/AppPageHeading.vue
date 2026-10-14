<script setup lang="ts">
/**
 * Heading block of every page: title and optional subtitle on the left, the page actions (Filters,
 * create) on the right from `lg`. Below `lg` the title row carries the actions and the subtitle
 * runs under both at full width. The page title is an `h1` below `lg` and an `h2` from `lg`, where
 * the panel header already holds the page `h1`. A page whose desktop wording differs passes
 * `desktopTitle` / `desktopSubtitle`; `loading` shows a skeleton instead of the title, and the
 * `badge` slot sits right after it. Only the active breakpoint's title and subtitle are rendered.
 */
import { useIsDesktop } from '@/utils/useIsDesktop'

const props = defineProps<{
  title: string
  subtitle?: string
  desktopTitle?: string
  desktopSubtitle?: string
  loading?: boolean
}>()

defineSlots<{ actions?: () => unknown; badge?: () => unknown }>()

const isDesktop = useIsDesktop()
</script>

<template>
  <!-- The first desktop row is the title's height; the 40px actions overhang it by 8px. -->
  <div class="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 lg:grid-rows-[2rem_auto]">
    <USkeleton
      v-if="loading"
      class="col-start-1 row-start-1 h-8 w-56 max-w-full rounded-lg"
      aria-hidden="true"
    />
    <div v-else class="col-start-1 row-start-1 flex min-w-0 items-center gap-x-3">
      <h2 v-if="isDesktop" class="min-w-0 truncate text-2xl leading-8 font-bold text-highlighted">
        {{ props.desktopTitle ?? title }}
      </h2>
      <h1 v-else class="min-w-0 truncate text-[22px] leading-8 font-bold text-highlighted">
        {{ title }}
      </h1>
      <slot name="badge" />
    </div>
    <p
      v-if="subtitle"
      class="col-start-1 row-start-2 text-base text-muted max-lg:col-span-2 lg:self-start"
    >
      {{ isDesktop ? (props.desktopSubtitle ?? subtitle) : subtitle }}
    </p>
    <div
      class="col-start-2 row-start-1 flex shrink-0 items-center gap-2.5 justify-self-end lg:self-start"
    >
      <slot name="actions" />
    </div>
  </div>
</template>
