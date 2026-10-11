<script setup lang="ts">
import { onMounted, ref, useTemplateRef } from 'vue'
import { useI18n } from 'vue-i18n'
import CategoriesList from '@/views/categories/CategoriesList.vue'
import CategoryDeleteDialog from '@/views/categories/CategoryDeleteDialog.vue'
import CategoryEditor from '@/views/categories/CategoryEditor.vue'
import FormMessage from '@/components/fields/FormMessage.vue'
import AppCreateButton from '@/components/page/AppCreateButton.vue'
import AppPageHeading from '@/components/page/AppPageHeading.vue'
import { PAGE_STACK_CLASS } from '@/styles/page'
import { useCreateButtonFocus } from '@/components/page/useCreateButtonFocus'
import AppContent from '@/layout/AppContent.vue'
import { useCategoryStore } from '@/stores/category'

const { t } = useI18n()
const categoryStore = useCategoryStore()

// The store's `error` is also set by failed mutations; only a failed load replaces the list.
const loadError = ref<string | null>(null)

onMounted(async () => {
  await categoryStore.load()
  loadError.value = categoryStore.error
})

const editor = useTemplateRef('editor')
const deleteDialog = useTemplateRef('deleteDialog')
// After a delete the row's button is gone, so focus goes to the visible New category button.
const { headingButton, footerButton, createButton } = useCreateButtonFocus()
</script>

<template>
  <AppContent :title="t('views.categories.title')">
    <div :class="PAGE_STACK_CLASS">
      <AppPageHeading :title="t('views.categories.title')">
        <template #actions>
          <AppCreateButton
            ref="headingButton"
            :label="t('views.categories.actions.new')"
            :accessible-label="t('views.categories.create.title')"
            @click="editor?.openCreate()"
          />
        </template>
      </AppPageHeading>

      <CategoryDeleteDialog ref="deleteDialog" :return-focus-target="createButton" />

      <FormMessage v-if="loadError" tone="error" :message="loadError" />

      <CategoriesList
        v-else
        :categories="categoryStore.categories"
        :loading="categoryStore.isLoading"
        @edit="editor?.openEdit($event)"
        @delete="deleteDialog?.requestDelete($event)"
      />
    </div>

    <CategoryEditor ref="editor" />

    <template #footer>
      <AppCreateButton
        ref="footerButton"
        placement="footer"
        :label="t('views.categories.actions.new')"
        :accessible-label="t('views.categories.create.title')"
        @click="editor?.openCreate()"
      />
    </template>
  </AppContent>
</template>
