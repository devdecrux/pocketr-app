<script setup lang="ts">
/**
 * Heading block of a list page: title and subtitle on the left, the page actions (Filters, create)
 * on the right from `lg`. Below `lg` the title row carries the actions and the subtitle runs under
 * both at full width. The page title is an `h1` below `lg` and an `h2` from `lg`, where the panel
 * header already holds the page `h1`.
 */
defineProps<{ title: string; subtitle: string }>()

defineSlots<{ actions?: () => unknown }>()
</script>

<template>
  <!-- The first desktop row is the title's height; the 40px actions overhang it by 8px. -->
  <div class="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 lg:grid-rows-[2rem_auto]">
    <h1
      class="col-start-1 row-start-1 min-w-0 truncate text-[22px] leading-8 font-bold text-highlighted lg:hidden"
    >
      {{ title }}
    </h1>
    <h2
      class="col-start-1 row-start-1 hidden min-w-0 truncate text-2xl leading-8 font-bold text-highlighted lg:block"
    >
      {{ title }}
    </h2>
    <p class="col-start-1 row-start-2 text-base text-muted max-lg:col-span-2 lg:self-start">
      {{ subtitle }}
    </p>
    <div
      class="col-start-2 row-start-1 flex shrink-0 items-center gap-2.5 justify-self-end lg:self-start"
    >
      <slot name="actions" />
    </div>
  </div>
</template>
