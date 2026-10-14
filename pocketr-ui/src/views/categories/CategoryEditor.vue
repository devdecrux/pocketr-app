<script setup lang="ts">
/**
 * Create and edit overlays for categories. One editor state drives both, so they can never be open
 * together; the page opens them through `openCreate` / `openEdit`.
 */
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import CategoryForm from '@/views/categories/CategoryForm.vue'
import AppFormOverlay from '@/components/overlays/AppFormOverlay.vue'
import { useCategoryStore } from '@/stores/category'
import type { CategoryTag } from '@/types/ledger'

const CREATE_FORM_ID = 'create-category-form'
const EDIT_FORM_ID = 'edit-category-form'

type Editor = { mode: 'create' } | { mode: 'edit'; category: CategoryTag }

const { t } = useI18n()
const categoryStore = useCategoryStore()

const editor = ref<Editor | null>(null)
const draftName = ref('')
const draftColor = ref<string | null>(null)
const saveError = ref('')
const isSaving = ref(false)

// The edited category outlives `editor`, so the overlay keeps its form while it animates closed.
const editedCategory = ref<CategoryTag | null>(null)

function closeEditor(): void {
  if (!isSaving.value) editor.value = null
}

const isCreateOpen = computed({
  get: () => editor.value?.mode === 'create',
  set: (open: boolean) => {
    if (!open) closeEditor()
  },
})

const isEditOpen = computed({
  get: () => editor.value?.mode === 'edit',
  set: (open: boolean) => {
    if (!open) closeEditor()
  },
})

function openCreate(): void {
  if (isSaving.value) return
  draftName.value = ''
  draftColor.value = null
  saveError.value = ''
  editor.value = { mode: 'create' }
}

function openEdit(category: CategoryTag): void {
  if (isSaving.value) return
  draftName.value = category.name
  draftColor.value = category.color ?? null
  saveError.value = ''
  editedCategory.value = category
  editor.value = { mode: 'edit', category }
}

async function saveCategory(payload: { name: string; color: string | null }): Promise<void> {
  const current = editor.value
  if (!current || isSaving.value) return

  saveError.value = ''
  isSaving.value = true
  const saved =
    current.mode === 'edit'
      ? await categoryStore.rename(current.category.id, payload.name, payload.color)
      : await categoryStore.create(payload.name, payload.color)
  isSaving.value = false

  if (saved) {
    editor.value = null
  } else {
    saveError.value =
      categoryStore.error ??
      t(current.mode === 'edit' ? 'errors.categories.rename' : 'errors.categories.create')
  }
}

defineExpose({ openCreate, openEdit })
</script>

<template>
  <AppFormOverlay
    v-model:open="isEditOpen"
    :title="t('views.categories.edit.title')"
    :description="t('views.categories.edit.description')"
    :form-id="EDIT_FORM_ID"
    :submit-label="t('common.actions.save')"
    :loading="isSaving"
    :submit-disabled="!draftName.trim()"
  >
    <CategoryForm
      v-if="editedCategory"
      :id="EDIT_FORM_ID"
      :key="editedCategory.id"
      v-model:name="draftName"
      v-model:color="draftColor"
      :categories="categoryStore.categories"
      :exclude-id="editedCategory.id"
      :server-error="saveError"
      preview
      @submit="saveCategory"
    />
  </AppFormOverlay>

  <AppFormOverlay
    v-model:open="isCreateOpen"
    :title="t('views.categories.create.title')"
    :description="t('views.categories.create.description')"
    :form-id="CREATE_FORM_ID"
    :submit-label="t('common.actions.create')"
    :loading="isSaving"
    :submit-disabled="!draftName.trim()"
  >
    <CategoryForm
      :id="CREATE_FORM_ID"
      v-model:name="draftName"
      v-model:color="draftColor"
      :categories="categoryStore.categories"
      :server-error="saveError"
      preview
      @submit="saveCategory"
    />
  </AppFormOverlay>
</template>
