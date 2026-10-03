<script setup lang="ts" generic="T extends string">
/**
 * Segmented type selector of the create forms (transaction kind, account type): one bordered track
 * with the active segment filled, 32px tall on mobile and 36px from `lg`. It is a Nuxt UI `UTabs`, so
 * the arrow keys move between the segments; each segment owns a panel, rendered through the
 * `content` slot for the active item only. The group is named through `groupLabel`.
 */
import type { TabsItem } from '@nuxt/ui'

defineProps<{ items: (TabsItem & { value: T })[]; groupLabel: string }>()

const model = defineModel<T>({ required: true })

defineSlots<{ content?: (props: { item: TabsItem }) => unknown }>()
</script>

<template>
  <UTabs
    v-model="model"
    :items="items"
    role="group"
    :aria-label="groupLabel"
    color="primary"
    variant="pill"
    size="md"
    :unmount-on-hide="true"
    :ui="{
      root: 'gap-3.5 lg:gap-[23px]',
      list: 'gap-0 rounded-lg border border-default bg-(--pocketr-field-bg) p-0.5',
      indicator: 'inset-y-0.5 rounded-md bg-primary shadow-none',
      trigger:
        'h-8 px-1 text-xs font-normal text-highlighted not-first:border-s not-first:border-default data-[state=active]:border-transparent data-[state=active]:font-medium data-[state=active]:text-inverted data-[state=inactive]:text-highlighted lg:h-9 lg:text-sm',
      content: 'outline-none',
    }"
  >
    <template #content="{ item }">
      <slot name="content" :item="item" />
    </template>
  </UTabs>
</template>
