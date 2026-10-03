<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import { computed, onMounted, ref, useTemplateRef } from 'vue'
import { useI18n } from 'vue-i18n'
import CategoryForm from '@/components/forms/CategoryForm.vue'
import FormMessage from '@/components/forms/FormMessage.vue'
import AppPagePanel from '@/components/layout/AppPagePanel.vue'
import AppConfirmDialog from '@/components/shared/AppConfirmDialog.vue'
import AppDataTable from '@/components/shared/AppDataTable.vue'
import AppExpandableRow from '@/components/shared/AppExpandableRow.vue'
import AppFormOverlay from '@/components/shared/AppFormOverlay.vue'
import CategoryColorDot from '@/components/shared/CategoryColorDot.vue'
import { useCategoryStore } from '@/stores/category'
import type { AppTableColumn } from '@/types/dataTable'
import type { CategoryTag } from '@/types/ledger'
import { APP_ICONS } from '@/utils/appIcons'
import { formatTimestampDate } from '@/utils/dates'

const CREATE_FORM_ID = 'create-category-form'
const EDIT_FORM_ID = 'edit-category-form'

const { t } = useI18n()
const categoryStore = useCategoryStore()
const isDesktop = useMediaQuery('(min-width: 1024px)')

// The store's `error` is also set by failed mutations; only a failed load replaces the list.
const loadError = ref<string | null>(null)

onMounted(async () => {
  await categoryStore.load()
  loadError.value = categoryStore.error
})

const sortedCategories = computed(() =>
  [...categoryStore.categories].sort((a, b) => a.name.localeCompare(b.name)),
)

// One editor state for create and edit, so the two overlays can never be open together.
type Editor = { mode: 'create' } | { mode: 'edit'; category: CategoryTag }

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

// Rows are expanded by category id on the mobile list, which shows the row actions in place.
const expanded = ref<Record<string, boolean>>({})

function toggleRow(category: CategoryTag): void {
  expanded.value = { ...expanded.value, [category.id]: !expanded.value[category.id] }
}

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

const deleteTarget = ref<CategoryTag | null>(null)
// Kept apart from the target so the dialog text does not blank out while it animates closed.
const deleteTargetName = ref('')
const deleteError = ref('')
const isDeleting = ref(false)

const isDeleteDialogOpen = computed({
  get: () => deleteTarget.value !== null,
  set: (open: boolean) => {
    if (!open && !isDeleting.value) deleteTarget.value = null
  },
})

// A deleted category's button is gone, so focus returns to the New category button instead.
const deleteReturnFocus = ref<HTMLElement | null>(null)
const desktopNewButton = useTemplateRef<{ $el: HTMLElement }>('desktopNewButton')
const footerNewButton = useTemplateRef<{ $el: HTMLElement }>('footerNewButton')

function requestDelete(category: CategoryTag): void {
  deleteReturnFocus.value = null
  deleteError.value = ''
  deleteTargetName.value = category.name
  deleteTarget.value = category
}

async function confirmDelete(): Promise<void> {
  const category = deleteTarget.value
  if (!category || isDeleting.value) return

  isDeleting.value = true
  const removed = await categoryStore.remove(category.id)
  isDeleting.value = false
  // Set before the dialog closes: the focus target is read as it finishes closing.
  if (removed) {
    deleteReturnFocus.value =
      (isDesktop.value ? desktopNewButton : footerNewButton).value?.$el ?? null
  }
  deleteTarget.value = null

  if (removed) {
    if (editor.value?.mode === 'edit' && editor.value.category.id === category.id)
      editor.value = null
  } else {
    deleteError.value = categoryStore.error ?? t('errors.categories.delete')
  }
}

const columns = computed<AppTableColumn<CategoryTag>[]>(() => [
  {
    accessorKey: 'name',
    header: t('common.table.category'),
    meta: { align: 'end', class: { th: 'w-full', td: 'w-full max-w-0' } },
  },
  {
    accessorKey: 'createdAt',
    header: t('common.table.created'),
    meta: { align: 'end', class: { th: 'min-w-40', td: 'min-w-40' } },
  },
  {
    id: 'actions',
    header: t('common.table.actions'),
    meta: { align: 'end', class: { th: 'min-w-28', td: 'min-w-28 py-1.5' } },
  },
])

const iconButtonClass = 'rounded-lg text-default'
</script>

<template>
  <AppPagePanel :title="t('views.categories.title')">
    <div class="flex min-w-0 flex-col gap-3">
      <div class="flex items-center justify-between gap-4">
        <h1 class="min-w-0 text-[22px] leading-8 font-bold text-highlighted lg:hidden">
          {{ t('views.categories.title') }}
        </h1>
        <h2 class="hidden min-w-0 text-2xl leading-8 font-bold text-highlighted lg:block">
          {{ t('views.categories.title') }}
        </h2>
        <UButton
          ref="desktopNewButton"
          :icon="APP_ICONS.add"
          size="md"
          :label="t('views.categories.actions.new')"
          class="hidden h-10 shrink-0 rounded-lg px-5 lg:inline-flex"
          @click="openCreate"
        />
      </div>

      <FormMessage v-if="deleteError" tone="error" :message="deleteError" />

      <FormMessage v-if="loadError" tone="error" :message="loadError" />

      <AppDataTable
        v-else-if="isDesktop"
        :data="sortedCategories"
        :columns="columns"
        :loading="categoryStore.isLoading"
        :empty-text="t('views.categories.empty')"
        :get-row-id="(category: CategoryTag) => category.id"
      >
        <template #name-cell="{ row }">
          <span class="flex min-w-0 items-center justify-end gap-3">
            <CategoryColorDot :color="row.original.color" />
            <span class="truncate font-medium text-highlighted">{{ row.original.name }}</span>
          </span>
        </template>
        <template #createdAt-cell="{ row }">
          <span class="whitespace-nowrap text-muted tabular-nums">
            {{ formatTimestampDate(row.original.createdAt) }}
          </span>
        </template>
        <template #actions-cell="{ row }">
          <div class="-me-2 inline-flex items-center gap-1">
            <UTooltip :text="t('common.actions.edit')">
              <UButton
                color="neutral"
                variant="ghost"
                size="md"
                :icon="APP_ICONS.edit"
                :aria-label="t('views.categories.rowActions.edit', { name: row.original.name })"
                :class="iconButtonClass"
                @click="openEdit(row.original)"
              />
            </UTooltip>
            <UTooltip :text="t('common.actions.delete')">
              <UButton
                color="neutral"
                variant="ghost"
                size="md"
                :icon="APP_ICONS.remove"
                :aria-label="t('views.categories.rowActions.delete', { name: row.original.name })"
                :class="iconButtonClass"
                @click="requestDelete(row.original)"
              />
            </UTooltip>
          </div>
        </template>
        <template #loading>{{ t('views.categories.loading') }}</template>
      </AppDataTable>

      <template v-else>
        <div
          v-if="categoryStore.isLoading"
          class="space-y-2"
          :aria-label="t('views.categories.loading')"
        >
          <USkeleton v-for="index in 4" :key="index" class="h-14 w-full rounded-xl" />
        </div>
        <p
          v-else-if="sortedCategories.length === 0"
          class="rounded-xl border border-default bg-default px-4 py-8 text-center text-sm text-muted"
        >
          {{ t('views.categories.empty') }}
        </p>
        <ul
          v-else
          class="divide-y divide-default overflow-hidden rounded-xl border border-default bg-default"
        >
          <li v-for="category in sortedCategories" :key="category.id">
            <AppExpandableRow
              :panel-id="`category-actions-${category.id}`"
              :expanded="Boolean(expanded[category.id])"
              @toggle="toggleRow(category)"
            >
              <CategoryColorDot :color="category.color" class="size-4" />
              <span class="min-w-0 flex-1">
                <span class="block truncate text-[15px] font-medium text-highlighted">
                  {{ category.name }}
                </span>
                <span class="block text-[13px] text-muted tabular-nums">
                  {{ formatTimestampDate(category.createdAt) }}
                </span>
              </span>
              <template #details>
                <div class="grid grid-cols-2 gap-2.5">
                  <UButton
                    block
                    color="neutral"
                    variant="soft"
                    size="md"
                    :icon="APP_ICONS.edit"
                    :label="t('common.actions.edit')"
                    :aria-label="t('views.categories.rowActions.edit', { name: category.name })"
                    class="h-11 justify-center rounded-lg"
                    @click="openEdit(category)"
                  />
                  <UButton
                    block
                    color="error"
                    variant="soft"
                    size="md"
                    :icon="APP_ICONS.remove"
                    :label="t('common.actions.delete')"
                    :aria-label="t('views.categories.rowActions.delete', { name: category.name })"
                    class="h-11 justify-center rounded-lg"
                    @click="requestDelete(category)"
                  />
                </div>
              </template>
            </AppExpandableRow>
          </li>
        </ul>
      </template>
    </div>

    <AppFormOverlay
      v-model:open="isEditOpen"
      :title="t('views.categories.edit.title')"
      :description="t('views.categories.edit.description')"
      :form-id="EDIT_FORM_ID"
      :submit-label="t('views.categories.edit.submit')"
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
      :submit-label="t('views.categories.create.submit')"
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

    <AppConfirmDialog
      v-model:open="isDeleteDialogOpen"
      :title="t('views.categories.confirmDelete', { name: deleteTargetName })"
      :description="t('views.categories.delete.description')"
      :confirm-label="t('views.categories.delete.confirm')"
      :loading="isDeleting"
      :return-focus-to="deleteReturnFocus"
      @confirm="confirmDelete"
    />

    <template #footer>
      <UButton
        ref="footerNewButton"
        block
        :icon="APP_ICONS.add"
        size="md"
        :label="t('views.categories.actions.new')"
        class="h-11 justify-center rounded-lg"
        @click="openCreate"
      />
    </template>
  </AppPagePanel>
</template>
