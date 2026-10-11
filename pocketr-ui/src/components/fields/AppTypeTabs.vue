<script setup lang="ts" generic="T extends string">
/**
 * Segmented type selector of the create forms (transaction kind, account type): one bordered track
 * with the active segment filled, 44px tall on mobile and 40px from `lg` (the 38px / 34px segments
 * plus the track's padding and border). It is a Nuxt UI `UTabs`, so the arrow keys move between the
 * segments; each segment owns a panel, rendered through the `content` slot for the active item
 * only. The group is named through `groupLabel`.
 *
 * Tab reaches the tab group once (its active tab) and then the fields of the panel. reka makes
 * more elements Tab stops than that: every panel (`tabindex="0"`) and the tab list itself (the
 * entry point of its roving focus, which also catches Shift+Tab and the wrap-around of the dialog's
 * focus trap). Nuxt UI offers no way to change either, so `enforceTabStops` keeps them out of the
 * Tab order and the active tab in it; they stay focusable by script and click.
 */
import type { TabsItem } from '@nuxt/ui'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { elementOf, type FocusTarget } from '@/utils/focus'

defineProps<{ items: (TabsItem & { value: T })[]; groupLabel: string }>()

const model = defineModel<T>({ required: true })

defineSlots<{ content?: (props: { item: TabsItem }) => unknown }>()

const tabs = ref<FocusTarget>(null)

let observer: MutationObserver | undefined

function enforceTabStops(): void {
  const root = elementOf(tabs.value)
  if (!root) return
  const wanted = new Map<Element, string>()
  for (const list of root.querySelectorAll('[role="tablist"]')) wanted.set(list, '-1')
  for (const panel of root.querySelectorAll('[role="tabpanel"]')) wanted.set(panel, '-1')
  for (const tab of root.querySelectorAll('[role="tab"]')) {
    wanted.set(tab, tab.getAttribute('aria-selected') === 'true' ? '0' : '-1')
  }
  for (const [element, tabindex] of wanted) {
    if (element.getAttribute('tabindex') !== tabindex) element.setAttribute('tabindex', tabindex)
  }
}

onMounted(() => {
  const root = elementOf(tabs.value)
  if (!root) return
  enforceTabStops()
  observer = new MutationObserver(enforceTabStops)
  observer.observe(root, {
    subtree: true,
    attributes: true,
    attributeFilter: ['tabindex', 'aria-selected'],
  })
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <UTabs
    ref="tabs"
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
        'h-[38px] px-1 text-xs font-normal text-highlighted not-first:border-s not-first:border-default data-[state=active]:border-transparent data-[state=active]:font-medium data-[state=active]:text-inverted data-[state=inactive]:text-highlighted lg:h-[34px] lg:text-sm',
      content: 'outline-none',
    }"
  >
    <template #content="{ item }">
      <slot name="content" :item="item" />
    </template>
  </UTabs>
</template>
