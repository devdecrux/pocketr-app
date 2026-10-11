import { useI18n } from 'vue-i18n'
import { useCategoryStore } from '@/stores/category'
import { APP_ICONS } from '@/utils/appearance/appIcons'

/**
 * Props for an `AppSelect` that picks a category: searchable, with the colour dots. `noneLabel`
 * is the first entry and clears the selection (`null`): "All categories" in filters, "No category"
 * in forms. Call it in a template with `v-bind`; it reads the store, so the list follows it.
 */
export function useCategoryOptions() {
  const { t } = useI18n()
  const categoryStore = useCategoryStore()

  function categorySelect(noneLabel: string) {
    return {
      items: categoryStore.categories.map((category) => ({
        value: category.id,
        label: category.name,
        color: category.color,
      })),
      searchable: true,
      searchPlaceholder: t('components.categoryTagSelector.searchPlaceholder'),
      emptyText: t('components.categoryTagSelector.empty'),
      icon: APP_ICONS.categories,
      placeholder: t('common.formHints.selectCategory'),
      allLabel: noneLabel,
      allValue: null,
    }
  }

  return { categorySelect }
}
