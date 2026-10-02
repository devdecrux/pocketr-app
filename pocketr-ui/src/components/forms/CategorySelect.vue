<script setup lang="ts">
/**
 * Searchable category picker on `USelectMenu`. The first entry (`noneLabel`) clears the selection
 * (`null`): "All categories" in filters, "No category" in forms.
 */
import type { SelectMenuItem } from '@nuxt/ui'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { FIELD_MENU_UI } from '@/components/forms/fieldStyles'
import CategoryColorDot from '@/components/shared/CategoryColorDot.vue'
import { useCategoryStore } from '@/stores/category'

const NONE_VALUE = '__none__'

const props = withDefaults(
  defineProps<{
    id?: string
    noneLabel: string
    placeholder?: string
    icon?: string
    ariaLabel?: string
    /** Extra classes for the trigger, for example a taller height. */
    triggerClass?: string
  }>(),
  { icon: 'i-lucide-tag' },
)

const model = defineModel<string | null>({ default: null })

const { t } = useI18n()
const categoryStore = useCategoryStore()

interface CategoryItem {
  value: string
  label: string
  color?: string | null
}

const items = computed<CategoryItem[]>(() => [
  { value: NONE_VALUE, label: props.noneLabel },
  ...categoryStore.categories.map((category) => ({
    value: category.id,
    label: category.name,
    color: category.color,
  })),
])

const selected = computed(() => model.value ?? NONE_VALUE)

function onSelect(value: unknown): void {
  if (typeof value === 'string') model.value = value === NONE_VALUE ? null : value
}
</script>

<template>
  <USelectMenu
    :id="id"
    :model-value="selected"
    :items="items as SelectMenuItem[]"
    value-key="value"
    :icon="icon"
    :placeholder="placeholder ?? t('common.formHints.selectCategory')"
    :aria-label="ariaLabel"
    :search-input="{ placeholder: t('components.categoryTagSelector.searchPlaceholder') }"
    size="lg"
    class="w-full"
    :ui="{ ...FIELD_MENU_UI, base: [FIELD_MENU_UI.base, triggerClass] }"
    @update:model-value="onSelect"
  >
    <template #empty>{{ t('components.categoryTagSelector.empty') }}</template>
    <template #item-leading="{ item }">
      <CategoryColorDot
        v-if="(item as CategoryItem).value !== NONE_VALUE"
        :color="(item as CategoryItem).color"
      />
    </template>
  </USelectMenu>
</template>
