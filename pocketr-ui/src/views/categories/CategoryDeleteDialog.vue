<script setup lang="ts">
/**
 * Delete confirmation for a category plus the error message of a failed delete (rendered where the
 * page places this component, above the list). The page opens it through `requestDelete`.
 */
import { useI18n } from 'vue-i18n'
import FormMessage from '@/components/fields/FormMessage.vue'
import AppConfirmDialog from '@/components/overlays/AppConfirmDialog.vue'
import { ActionFailed, useTargetAction } from '@/components/overlays/useTargetAction'
import { useCategoryStore } from '@/stores/category'
import type { CategoryTag } from '@/types/ledger'

const props = defineProps<{
  /** A deleted category's button is gone; focus returns to this element (read when it is needed). */
  returnFocusTarget: () => HTMLElement | null
}>()

const { t } = useI18n()
const categoryStore = useCategoryStore()

const {
  isOpen,
  label: categoryName,
  error,
  busyId,
  returnFocus,
  open: requestDelete,
  run,
} = useTargetAction<CategoryTag>({
  labelOf: (category) => category.name,
  perform: async (category) => {
    // The store reports a failed delete through its return value and `error`.
    if (!(await categoryStore.remove(category.id))) {
      throw new ActionFailed(categoryStore.error ?? t('errors.categories.delete'))
    }
  },
  errorMessage: () => t('errors.categories.delete'),
  returnFocusTarget: () => props.returnFocusTarget(),
})

defineExpose({ requestDelete })
</script>

<template>
  <FormMessage v-if="error" tone="error" :message="error" />

  <AppConfirmDialog
    v-model:open="isOpen"
    :title="t('views.categories.confirmDelete', { name: categoryName })"
    :description="t('views.categories.delete.description')"
    :confirm-label="t('views.categories.delete.confirm')"
    :loading="busyId !== null"
    :return-focus-to="returnFocus"
    @confirm="run()"
  />
</template>
