<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { type ColumnDef, getCoreRowModel, useVueTable } from '@tanstack/vue-table'
import { Pencil, Plus, Trash2 } from 'lucide-vue-next'
import { useCategoryStore } from '@/stores/category'
import type { CategoryTag } from '@/types/ledger'
import { CATEGORY_PRESET_COLORS } from '@/utils/categoryColors'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Dialog, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import {
  AppCardHeader,
  AppDialogBody,
  AppDialogContent,
  AppFormField,
  AppStateMessage,
  AppStatusText,
} from '@/components/app'
import CategoryColorPicker from '@/components/CategoryColorPicker.vue'
import DataTable from '@/components/DataTable.vue'
import { translate } from '@/i18n/translate'

const categoryStore = useCategoryStore()

const createDialogOpen = ref(false)
const createName = ref('')
const createColor = ref<string | null>(null)
const createError = ref('')
const isCreating = ref(false)

const renameDialogOpen = ref(false)
const renameTarget = ref<CategoryTag | null>(null)
const renameName = ref('')
const renameColor = ref<string | null>(null)
const renameError = ref('')
const isRenaming = ref(false)

const deleteError = ref('')
const deletingId = ref<string | null>(null)

const sortedCategories = computed(() => {
  return [...categoryStore.categories].sort((a, b) => a.name.localeCompare(b.name))
})

function hasDuplicateName(name: string, excludeId?: string): boolean {
  const normalized = name.trim().toLowerCase()
  return categoryStore.categories.some((category) => {
    if (excludeId && category.id === excludeId) {
      return false
    }
    return category.name.trim().toLowerCase() === normalized
  })
}

onMounted(async () => {
  await categoryStore.load()
})

function startRename(category: CategoryTag): void {
  renameTarget.value = category
  renameName.value = category.name
  renameColor.value = category.color ?? null
  renameError.value = ''
  renameDialogOpen.value = true
}

async function submitCreate(): Promise<void> {
  const trimmedName = createName.value.trim()
  if (!trimmedName) {
    createError.value = translate('validation.category.nameRequired')
    return
  }
  if (hasDuplicateName(trimmedName)) {
    createError.value = translate('validation.category.duplicate', { name: trimmedName })
    return
  }

  createError.value = ''
  isCreating.value = true

  const created = await categoryStore.create(trimmedName, createColor.value)
  if (created) {
    createDialogOpen.value = false
    createName.value = ''
    createColor.value = null
  } else {
    createError.value = categoryStore.error ?? translate('errors.categories.create')
  }

  isCreating.value = false
}

async function submitRename(): Promise<void> {
  const target = renameTarget.value
  const trimmedName = renameName.value.trim()
  if (!target) return

  if (!trimmedName) {
    renameError.value = translate('validation.category.nameRequired')
    return
  }
  if (hasDuplicateName(trimmedName, target.id)) {
    renameError.value = translate('validation.category.duplicate', { name: trimmedName })
    return
  }

  renameError.value = ''
  isRenaming.value = true

  const updated = await categoryStore.rename(target.id, trimmedName, renameColor.value)
  if (updated) {
    renameDialogOpen.value = false
    renameTarget.value = null
    renameName.value = ''
    renameColor.value = null
  } else {
    renameError.value = categoryStore.error ?? translate('errors.categories.rename')
  }

  isRenaming.value = false
}

async function deleteCategory(category: CategoryTag): Promise<void> {
  deleteError.value = ''

  const confirmed = window.confirm(
    translate('views.categories.confirmDelete', { name: category.name }),
  )
  if (!confirmed) return

  deletingId.value = category.id
  const success = await categoryStore.remove(category.id)
  if (!success) {
    deleteError.value = categoryStore.error ?? translate('errors.categories.delete')
  }
  deletingId.value = null
}

const columns: ColumnDef<CategoryTag>[] = [
  {
    accessorKey: 'name',
    header: translate('common.table.name'),
    cell: ({ row }) => {
      const cat = row.original
      return h('div', { class: 'flex items-center justify-end gap-2' }, [
        cat.color
          ? h('span', {
              class: 'inline-block h-3.5 w-3.5 shrink-0 rounded-full border border-border',
              style: { backgroundColor: cat.color },
            })
          : null,
        h('span', {}, cat.name),
      ])
    },
  },
  {
    accessorKey: 'createdAt',
    header: translate('common.table.created'),
    cell: ({ row }) => new Date(row.original.createdAt).toLocaleDateString(),
  },
  {
    id: 'actions',
    header: '',
    cell: ({ row }) =>
      h('div', { class: 'flex items-center justify-end gap-1' }, [
        h(
          Button,
          {
            variant: 'ghost',
            size: 'icon',
            'data-table-action': 'edit',
            onClick: () => startRename(row.original),
          },
          () => h(Pencil, { class: 'size-4' }),
        ),
        h(
          Button,
          {
            variant: 'ghost',
            size: 'icon',
            'data-table-action': 'delete',
            disabled: deletingId.value === row.original.id,
            onClick: () => deleteCategory(row.original),
          },
          () => h(Trash2, { class: 'size-4' }),
        ),
      ]),
  },
]

const table = useVueTable({
  get data() {
    return sortedCategories.value
  },
  columns,
  getCoreRowModel: getCoreRowModel(),
})
</script>

<template>
  <section class="flex flex-col gap-4">
    <Card>
      <AppCardHeader :title="$t('views.categories.title')" title-class="text-2xl">
        <Dialog v-model:open="createDialogOpen">
          <DialogTrigger as-child>
            <Button size="sm">
              <Plus class="size-4" />
              {{ $t('views.categories.actions.new') }}
            </Button>
          </DialogTrigger>
          <AppDialogContent
            :title="$t('views.categories.create.title')"
            :description="$t('views.categories.create.description')"
          >
            <AppDialogBody>
              <AppFormField :label="$t('common.fields.name')" control-id="create-category-name">
                <Input
                  id="create-category-name"
                  v-model="createName"
                  :placeholder="$t('common.formHints.categoryExamples')"
                />
              </AppFormField>
              <AppFormField :label="$t('common.fields.color')">
                <CategoryColorPicker v-model="createColor" :colors="CATEGORY_PRESET_COLORS" />
              </AppFormField>
              <AppStatusText v-if="createError">{{ createError }}</AppStatusText>
            </AppDialogBody>

            <template #footer>
              <Button :disabled="isCreating || !createName.trim()" @click="submitCreate">
                {{ isCreating ? $t('common.feedback.creating') : $t('common.actions.create') }}
              </Button>
            </template>
          </AppDialogContent>
        </Dialog>
      </AppCardHeader>

      <CardContent class="flex flex-col pb-6">
        <AppStateMessage v-if="categoryStore.isLoading" center>
          {{ $t('views.categories.loading') }}
        </AppStateMessage>

        <AppStateMessage v-else-if="categoryStore.error" variant="error" center>
          {{ categoryStore.error }}
        </AppStateMessage>

        <DataTable v-else :table="table" sticky-header :empty-text="$t('views.categories.empty')" />

        <AppStatusText v-if="deleteError" class="mt-3">{{ deleteError }}</AppStatusText>
      </CardContent>
    </Card>

    <Dialog v-model:open="renameDialogOpen">
      <AppDialogContent
        :title="$t('views.categories.rename.title')"
        :description="$t('views.categories.rename.description')"
      >
        <AppDialogBody>
          <AppFormField :label="$t('common.fields.name')" control-id="rename-category-name">
            <Input id="rename-category-name" v-model="renameName" />
          </AppFormField>
          <AppFormField :label="$t('common.fields.color')">
            <CategoryColorPicker v-model="renameColor" :colors="CATEGORY_PRESET_COLORS" />
          </AppFormField>
          <AppStatusText v-if="renameError">{{ renameError }}</AppStatusText>
        </AppDialogBody>

        <template #footer>
          <Button :disabled="isRenaming || !renameName.trim()" @click="submitRename">
            {{ isRenaming ? $t('common.feedback.saving') : $t('common.actions.save') }}
          </Button>
        </template>
      </AppDialogContent>
    </Dialog>
  </section>
</template>
