<script setup lang="ts">
/**
 * The one Pocketr select: every dropdown in the app is this component, so they size, open and
 * behave alike.
 *
 * - `USelect`, or the searchable `USelectMenu` with `searchable`.
 * - `items`: a flat list, or groups separated by a rule; entries may carry a leading `icon`, a
 *   `color` dot, a `trailing` note and a `description`; `{ type: 'label' }` is a group heading.
 * - `allLabel` adds a first entry that stands for "everything / nothing" and maps to `allValue`
 *   (`''` by default), so filters and optional fields keep a plain model value.
 * - `variant`: both look the same (the field size: 40px, 44px below `lg`, 16px text there). `field`
 *   fills the width of its container, as a form control does; `toolbar` keeps the width of its value
 *   (page-level mode, period and rows-per-page selects).
 * - `autoWidth` sizes the trigger to its value from `lg` (filter panels). Open lists always take the
 *   width rule of the theme (`styles/theme.ts`).
 */
import type { Component } from 'vue'
import { computed, useSlots } from 'vue'
import USelect from '@nuxt/ui/components/Select.vue'
import USelectMenu from '@nuxt/ui/components/SelectMenu.vue'
import CategoryColorDot from '@/components/fields/CategoryColorDot.vue'
import { FIELD_SELECT_UI } from '@/styles/field'
import {
  type AppSelectEntry,
  type AppSelectItems,
  type AppSelectOption,
  type AppSelectValue,
  isGroupedItems,
  isSelectOption,
} from '@/components/fields/selectItems'

const ALL_SENTINEL = '__all__'

const props = withDefaults(
  defineProps<{
    items: AppSelectItems
    id?: string
    variant?: 'field' | 'toolbar'
    size?: 'sm' | 'md' | 'lg'
    searchable?: boolean
    searchPlaceholder?: string
    emptyText?: string
    icon?: string
    placeholder?: string
    ariaLabel?: string
    /** First entry meaning "all / none"; it selects `allValue`. */
    allLabel?: string
    allValue?: AppSelectValue
    autoWidth?: boolean
    /** Extra classes for the trigger. */
    triggerClass?: string
  }>(),
  { variant: 'field', size: 'lg', allValue: '' },
)

const model = defineModel<AppSelectValue>()
const slots = useSlots()

const ui = computed(() => ({
  ...FIELD_SELECT_UI,
  base: [FIELD_SELECT_UI.base, props.triggerClass],
}))

const rootClass = computed(() => [
  props.variant === 'field' && 'w-full',
  props.autoWidth && 'min-w-0 lg:w-fit lg:max-w-72 lg:min-w-44',
])

const items = computed<AppSelectItems>(() => {
  if (!props.allLabel) return props.items
  const all: AppSelectOption = { value: ALL_SENTINEL, label: props.allLabel }
  return isGroupedItems(props.items) ? [[all], ...props.items] : [all, ...props.items]
})

const entries = computed<AppSelectEntry[]>(() => items.value.flat())
const options = computed(() => entries.value.filter(isSelectOption))
const hasDots = computed(() => options.value.some((option) => 'color' in option))
const hasTrailing = computed(() => options.value.some((option) => option.trailing))

const selected = computed(() => {
  const value = model.value
  if (props.allLabel && (value === props.allValue || value == null || value === '')) {
    return ALL_SENTINEL
  }
  return value == null || value === '' ? undefined : value
})

function onSelect(value: unknown): void {
  if (typeof value !== 'string' && typeof value !== 'number') return
  model.value = value === ALL_SENTINEL ? props.allValue : value
}

const component = computed<Component>(() => (props.searchable ? USelectMenu : USelect))
const searchInput = computed(() =>
  props.searchable ? { placeholder: props.searchPlaceholder } : undefined,
)
</script>

<template>
  <component
    :is="component"
    :id="id"
    :model-value="selected"
    :items="items"
    value-key="value"
    :icon="icon"
    :placeholder="placeholder"
    :aria-label="ariaLabel"
    :search-input="searchInput"
    :size="size"
    :ui="ui"
    :class="rootClass"
    @update:model-value="onSelect"
  >
    <template v-if="slots.default" #default="slotProps">
      <slot v-bind="slotProps" />
    </template>
    <template v-if="searchable" #empty>{{ emptyText }}</template>
    <template v-if="hasDots" #item-leading="{ item }">
      <CategoryColorDot v-if="'color' in item" :color="(item as AppSelectOption).color" />
    </template>
    <template v-if="hasTrailing" #item-trailing="{ item }">
      <span v-if="(item as AppSelectOption).trailing" class="text-xs text-muted">
        {{ (item as AppSelectOption).trailing }}
      </span>
    </template>
  </component>
</template>
