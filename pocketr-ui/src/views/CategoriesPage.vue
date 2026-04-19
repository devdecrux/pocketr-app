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
    createError.value = 'Category name is required.'
    return
  }
  if (hasDuplicateName(trimmedName)) {
    createError.value = `Category '${trimmedName}' already exists.`
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
    createError.value = categoryStore.error ?? 'Failed to create category.'
  }

  isCreating.value = false
}

async function submitRename(): Promise<void> {
  const target = renameTarget.value
  const trimmedName = renameName.value.trim()
  if (!target) return

  if (!trimmedName) {
    renameError.value = 'Category name is required.'
    return
  }
  if (hasDuplicateName(trimmedName, target.id)) {
    renameError.value = `Category '${trimmedName}' already exists.`
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
    renameError.value = categoryStore.error ?? 'Failed to rename category.'
  }

  isRenaming.value = false
}

async function deleteCategory(category: CategoryTag): Promise<void> {
  deleteError.value = ''

  const confirmed = window.confirm(`Delete category "${category.name}"?`)
  if (!confirmed) return

  deletingId.value = category.id
  const success = await categoryStore.remove(category.id)
  if (!success) {
    deleteError.value = categoryStore.error ?? 'Failed to delete category.'
  }
  deletingId.value = null
}

const columns: ColumnDef<CategoryTag>[] = [
  {
    accessorKey: 'name',
    header: 'Name',
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
    header: 'Created',
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
      <AppCardHeader title="Categories" title-class="text-2xl">
        <Dialog v-model:open="createDialogOpen">
          <DialogTrigger as-child>
            <Button size="sm">
              <Plus class="size-4" />
              New Category
            </Button>
          </DialogTrigger>
          <AppDialogContent
            title="Create Category"
            description="Add a category for transaction tagging."
          >
            <AppDialogBody>
              <AppFormField label="Name" control-id="create-category-name">
                <Input
                  id="create-category-name"
                  v-model="createName"
                  placeholder="e.g. Groceries, Utilities"
                />
              </AppFormField>
              <AppFormField label="Color">
                <CategoryColorPicker v-model="createColor" :colors="CATEGORY_PRESET_COLORS" />
              </AppFormField>
              <AppStatusText v-if="createError">{{ createError }}</AppStatusText>
            </AppDialogBody>

            <template #footer>
              <Button :disabled="isCreating || !createName.trim()" @click="submitCreate">
                {{ isCreating ? 'Creating...' : 'Create' }}
              </Button>
            </template>
          </AppDialogContent>
        </Dialog>
      </AppCardHeader>

      <CardContent class="flex flex-col pb-6">
        <AppStateMessage v-if="categoryStore.isLoading" center>
          Loading categories...
        </AppStateMessage>

        <AppStateMessage v-else-if="categoryStore.error" variant="error" center>
          {{ categoryStore.error }}
        </AppStateMessage>

        <DataTable
          v-else
          :table="table"
          sticky-header
          empty-text="No categories yet. Create your first category to get started."
        />

        <AppStatusText v-if="deleteError" class="mt-3">{{ deleteError }}</AppStatusText>
      </CardContent>
    </Card>

    <Dialog v-model:open="renameDialogOpen">
      <AppDialogContent title="Rename Category" description="Update the category name.">
        <AppDialogBody>
          <AppFormField label="Name" control-id="rename-category-name">
            <Input id="rename-category-name" v-model="renameName" />
          </AppFormField>
          <AppFormField label="Color">
            <CategoryColorPicker v-model="renameColor" :colors="CATEGORY_PRESET_COLORS" />
          </AppFormField>
          <AppStatusText v-if="renameError">{{ renameError }}</AppStatusText>
        </AppDialogBody>

        <template #footer>
          <Button :disabled="isRenaming || !renameName.trim()" @click="submitRename">
            {{ isRenaming ? 'Saving...' : 'Save' }}
          </Button>
        </template>
      </AppDialogContent>
    </Dialog>
  </section>
</template>
