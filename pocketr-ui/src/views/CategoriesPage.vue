<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import { useMediaQuery } from '@vueuse/core'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import CategoryForm from '@/components/forms/CategoryForm.vue'
import FormMessage from '@/components/forms/FormMessage.vue'
import AppPagePanel from '@/components/layout/AppPagePanel.vue'
import AppConfirmDialog from '@/components/shared/AppConfirmDialog.vue'
import AppDataTable from '@/components/shared/AppDataTable.vue'
import AppFormOverlay from '@/components/shared/AppFormOverlay.vue'
import CategoryColorDot from '@/components/shared/CategoryColorDot.vue'
import { useCategoryStore } from '@/stores/category'
import type { AppTableColumn } from '@/types/dataTable'
import type { CategoryTag } from '@/types/ledger'

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

const dateFormatter = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' })

function formatCreated(createdAt: string): string {
  const date = new Date(createdAt)
  return Number.isNaN(date.getTime()) ? '' : dateFormatter.format(date)
}

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

function openCreate(): void {
  if (isSaving.value) return
  returnFocusTo.value = null
  draftName.value = ''
  draftColor.value = null
  saveError.value = ''
  editor.value = { mode: 'create' }
}

// Actions chosen from a row menu return focus to its trigger; the menu item is gone by then.
const menuTrigger = ref<HTMLElement | null>(null)
const returnFocusTo = ref<HTMLElement | null>(null)

function onMenuTriggerClick(event: MouseEvent): void {
  menuTrigger.value = event.currentTarget as HTMLElement
}

function openEdit(category: CategoryTag, fromMenu = false): void {
  if (isSaving.value) return
  returnFocusTo.value = fromMenu ? menuTrigger.value : null
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

function requestDelete(category: CategoryTag, fromMenu = false): void {
  returnFocusTo.value = fromMenu ? menuTrigger.value : null
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
    meta: { class: { th: 'w-full', td: 'w-full max-w-0' } },
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

function rowMenuItems(category: CategoryTag): DropdownMenuItem[] {
  return [
    {
      label: t('common.actions.edit'),
      icon: 'i-lucide-pencil',
      onSelect: () => openEdit(category, true),
    },
    {
      label: t('common.actions.delete'),
      icon: 'i-lucide-trash-2',
      color: 'error',
      onSelect: () => requestDelete(category, true),
    },
  ]
}

const iconButtonClass = 'rounded-lg text-default'
</script>

<template>
  <AppPagePanel :title="t('views.categories.title')">
    <div class="grid gap-4 lg:grid-cols-5">
      <div class="flex min-w-0 flex-col gap-3 lg:col-span-3">
        <div class="flex items-center justify-between gap-4">
          <h1 class="min-w-0 text-[22px] leading-8 font-bold text-highlighted lg:hidden">
            {{ t('views.categories.title') }}
          </h1>
          <h2 class="hidden min-w-0 text-2xl leading-8 font-bold text-highlighted lg:block">
            {{ t('views.categories.title') }}
          </h2>
          <UButton
            icon="i-lucide-plus"
            size="md"
            :label="t('views.categories.actions.new')"
            class="hidden shrink-0 rounded-lg lg:inline-flex"
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
            <span class="flex min-w-0 items-center gap-3">
              <CategoryColorDot :color="row.original.color" />
              <span class="truncate font-medium text-highlighted">{{ row.original.name }}</span>
            </span>
          </template>
          <template #createdAt-cell="{ row }">
            <span class="whitespace-nowrap text-muted tabular-nums">
              {{ formatCreated(row.original.createdAt) }}
            </span>
          </template>
          <template #actions-cell="{ row }">
            <div class="-me-2 inline-flex items-center gap-1">
              <UTooltip :text="t('common.actions.edit')">
                <UButton
                  color="neutral"
                  variant="ghost"
                  size="md"
                  icon="i-lucide-pencil"
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
                  icon="i-lucide-trash-2"
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
            <li
              v-for="category in sortedCategories"
              :key="category.id"
              class="flex items-center gap-3 py-2.5 ps-4 pe-2"
            >
              <CategoryColorDot :color="category.color" class="size-4" />
              <div class="min-w-0 flex-1">
                <p class="truncate text-[15px] font-medium text-highlighted">{{ category.name }}</p>
                <p class="text-[13px] text-muted tabular-nums">
                  {{ formatCreated(category.createdAt) }}
                </p>
              </div>
              <UButton
                color="neutral"
                variant="ghost"
                size="lg"
                icon="i-lucide-pencil"
                :aria-label="t('views.categories.rowActions.edit', { name: category.name })"
                :class="iconButtonClass"
                @click="openEdit(category)"
              />
              <!-- Non-modal: a modal menu's pointer lock would outlive the drawer its item opens. -->
              <UDropdownMenu
                :items="rowMenuItems(category)"
                :modal="false"
                :content="{ align: 'end' }"
              >
                <UButton
                  color="neutral"
                  variant="ghost"
                  size="lg"
                  icon="i-lucide-ellipsis"
                  :aria-label="t('views.categories.rowActions.more', { name: category.name })"
                  :class="iconButtonClass"
                  @click="onMenuTriggerClick"
                />
              </UDropdownMenu>
            </li>
          </ul>
        </template>
      </div>
    </div>

    <AppFormOverlay
      v-model:open="isEditOpen"
      :title="t('views.categories.edit.title')"
      :description="t('views.categories.edit.description')"
      :form-id="EDIT_FORM_ID"
      :submit-label="t('views.categories.edit.submit')"
      :loading="isSaving"
      :submit-disabled="!draftName.trim()"
      :return-focus-to="returnFocusTo"
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
      :return-focus-to="returnFocusTo"
      @confirm="confirmDelete"
    />

    <template #footer>
      <UButton
        block
        icon="i-lucide-plus"
        size="md"
        :label="t('views.categories.actions.new')"
        class="h-11 justify-center rounded-lg"
        @click="openCreate"
      />
    </template>
  </AppPagePanel>
</template>
